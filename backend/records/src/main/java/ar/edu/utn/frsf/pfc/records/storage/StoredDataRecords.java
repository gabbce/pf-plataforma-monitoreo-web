package ar.edu.utn.frsf.pfc.records.storage;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import ar.edu.utn.frsf.pfc.records.station.DataContextClient.Context;
import ar.edu.utn.frsf.pfc.records.station.DataContextClient.TimeValidity;
import com.mongodb.ErrorCategory;
import com.mongodb.MongoException;
import com.mongodb.MongoWriteException;
import com.mongodb.WriteConcern;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Projections;

import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Updates.set;

@Component
public final class StoredDataRecords {

    static final String COLLECTION = "operational_records";

    private final MongoCollection<Document> records;
    private final Clock clock;

    @Autowired
    public StoredDataRecords(MongoClient client, @Value("${spring.mongodb.database}") String database,
            @Value("${pfc.records.storage-operation-timeout-ms:5000}") long timeoutMillis) {
        this(client.getDatabase(database).getCollection(COLLECTION), Clock.systemUTC(), timeoutMillis);
    }

    StoredDataRecords(MongoCollection<Document> records, Clock clock, long timeoutMillis) {
        if (timeoutMillis <= 0) {
            throw new IllegalArgumentException("The storage operation timeout must be positive");
        }
        this.records = records.withWriteConcern(WriteConcern.W1.withJournal(true))
                .withTimeout(timeoutMillis, TimeUnit.MILLISECONDS);
        this.clock = clock;
    }

    public Result recognize(DataRecord record) {
        return recognize(identity(record), original(record));
    }

    public Result store(DataRecord record, Context context) {
        var storageClass = storageClass(record, context);
        var receivedAt = clock.instant();
        var key = identity(record);
        var original = original(record);
        var document = new Document("instanceId", record.instanceId()).append("recordId", record.recordId())
                .append("original", original).append("receivedAt", receivedAt.toString())
                .append("storageClass", storageClass.name()).append("context", snapshot(context));
        try {
            var inserted = records.insertOne(document);
            return inserted.wasAcknowledged() && inserted.getInsertedId() != null
                    ? new Result(Status.STORED, storageClass, receivedAt) : Result.of(Status.RETRY);
        } catch (MongoWriteException failure) {
            if (ErrorCategory.fromErrorCode(failure.getCode()) != ErrorCategory.DUPLICATE_KEY) {
                return Result.of(Status.RETRY);
            }
            var recognition = recognize(key, original);
            return recognition.status() == Status.NOT_FOUND ? Result.of(Status.RETRY) : recognition;
        } catch (MongoException failure) {
            return Result.of(Status.RETRY);
        }
    }

    private Result recognize(Bson key, Document original) {
        try {
            var stored = records.find(key).projection(Projections.include("original", "receivedAt", "storageClass"))
                    .first();
            if (stored == null) {
                return Result.of(Status.NOT_FOUND);
            }
            var storedOriginal = stored.get("original", Document.class);
            var id = stored.getObjectId("_id");
            if (storedOriginal == null || id == null
                    || stored.getString("storageClass") == null || stored.getString("receivedAt") == null) {
                return Result.of(Status.RETRY);
            }
            var storageClass = StorageClass.valueOf(stored.getString("storageClass"));
            var receivedAt = Instant.parse(stored.getString("receivedAt"));
            var sameContent = sameOriginal(storedOriginal, original);
            // A read can see a write before it reaches the journal. A replay or conflict
            // is definitive only after a real write to the exact winning document.
            var confirmed = records.updateOne(and(key, eq("_id", id), eq("original", storedOriginal)),
                    set("durabilityFence", new ObjectId()));
            if (!confirmed.wasAcknowledged() || confirmed.getMatchedCount() != 1 || confirmed.getModifiedCount() != 1) {
                return Result.of(Status.RETRY);
            }
            return sameContent ? new Result(Status.ALREADY_STORED, storageClass, receivedAt) : Result.of(Status.CONFLICT);
        } catch (MongoException | DateTimeException | IllegalArgumentException | ClassCastException failure) {
            return Result.of(Status.RETRY);
        }
    }

    private static Bson identity(DataRecord record) {
        return and(eq("instanceId", record.instanceId()), eq("recordId", record.recordId()));
    }

    private static Document original(DataRecord record) {
        var payload = record.payload();
        // Store precision without the rounding of BSON Date or a fixed-width numeric type.
        var values = new Document("value", payload.value() == null ? null : payload.value().toString())
                .append("unit", payload.unit()).append("quality", payload.quality().name());
        return new Document("schemaVersion", record.schemaVersion()).append("instanceId", record.instanceId())
                .append("recordId", record.recordId()).append("producerId", record.producerId())
                .append("sequence", record.sequence()).append("kind", "DATA")
                .append("definitionId", record.definitionId()).append("subsystemId", record.subsystemId())
                .append("occurredAt", record.occurredAt() == null ? null : record.occurredAt().toString())
                .append("timeValidity", record.timeValidity().name())
                .append("appliedConfigVersion", record.appliedConfigVersion()).append("payload", values);
    }

    private static boolean sameOriginal(Document stored, Document incoming) {
        var storedPayload = stored.get("payload", Document.class);
        if (storedPayload == null) {
            throw new IllegalArgumentException("Missing stored DATA payload");
        }
        var storedValue = storedPayload.getString("value");
        var incomingValue = incoming.get("payload", Document.class).getString("value");
        if (Objects.equals(storedValue, incomingValue)) {
            return stored.equals(incoming);
        }
        return storedValue != null && incomingValue != null
                && new BigDecimal(storedValue).compareTo(new BigDecimal(incomingValue)) == 0
                && new Document(stored).append("payload", new Document(storedPayload).append("value", incomingValue))
                        .equals(incoming);
    }

    private static Document snapshot(Context context) {
        return new Document("typeId", context.typeId()).append("definitionId", context.definitionId())
                .append("subsystemId", context.subsystemId()).append("valueType", context.valueType())
                .append("unit", context.unit()).append("sourceId", context.sourceId());
    }

    private static StorageClass storageClass(DataRecord record, Context context) {
        var operational = record.timeValidity() == TimeValidity.VALID;
        if (!record.definitionId().equals(context.definitionId())
                || !Objects.equals(record.subsystemId(), context.subsystemId())
                || !record.payload().unit().equals(context.unit())
                || (operational && (context.sourceId() == null || context.sourceId().isBlank()))
                || (!operational && context.sourceId() != null)) {
            throw new IllegalArgumentException("DATA record and catalog context disagree");
        }
        return operational ? StorageClass.OPERATIONAL : StorageClass.REVIEW;
    }

    public enum Status {
        STORED, ALREADY_STORED, CONFLICT, NOT_FOUND, RETRY
    }

    public enum StorageClass {
        OPERATIONAL, REVIEW
    }

    public record Result(Status status, StorageClass storageClass, Instant receivedAt) {
        private static Result of(Status status) {
            return new Result(status, null, null);
        }
    }
}
