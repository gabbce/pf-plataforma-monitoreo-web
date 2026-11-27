package ar.edu.utn.frsf.pfc.records.storage;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.mongodb.WriteConcern;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.result.UpdateResult;

import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.exists;
import static com.mongodb.client.model.Filters.lte;
import static com.mongodb.client.model.Filters.or;
import static com.mongodb.client.model.Sorts.descending;
import static com.mongodb.client.model.Updates.combine;
import static com.mongodb.client.model.Updates.set;

@Component
public final class MeasurementReads {

    static final String STATE_COLLECTION = "record_visibility";
    private static final String STATE_ID = "latest";
    private static final String OPERATIONAL = "OPERATIONAL";
    private static final List<String> DEFINITIONS = List.of("engine.rpm", "engine.coolant_temperature");
    private static final long MICROS_PER_SECOND = 1_000_000;

    private final MongoCollection<Document> originals;
    private final MongoCollection<Document> stateCollection;
    private final Clock clock;
    private boolean prepared;

    @Autowired
    public MeasurementReads(MongoClient client, @Value("${spring.mongodb.database}") String database,
            @Value("${pfc.records.storage-operation-timeout-ms:5000}") long timeoutMillis) {
        this(client.getDatabase(database), Clock.systemUTC(), timeoutMillis);
    }

    MeasurementReads(MongoDatabase database, Clock clock, long timeoutMillis) {
        if (timeoutMillis <= 0) {
            throw new IllegalArgumentException("The storage operation timeout must be positive");
        }
        var durable = database.withWriteConcern(WriteConcern.W1.withJournal(true))
                .withTimeout(timeoutMillis, TimeUnit.MILLISECONDS);
        this.originals = durable.getCollection(StoredDataRecords.COLLECTION);
        this.stateCollection = durable.getCollection(STATE_COLLECTION);
        this.clock = clock;
    }

    // This monitor is the publication boundary for the single Records process in P1.
    // Original insertions happen outside it and cannot become queryable on their own.
    public synchronized void prepare() {
        if (prepared) {
            return;
        }
        var state = recover();
        if (!state.legacyComplete()) {
            // The ingestion path calls prepare before inserting any new originals.
            var unpublished = and(eq("storageClass", OPERATIONAL), exists("visibleAtMicros", false));
            for (var original = originals.find(unpublished).first(); original != null;
                    original = originals.find(unpublished).first()) {
                state = publish(state, original);
            }
            save(state, state.floorMicros(), null, true);
        }
        prepared = true;
    }

    public synchronized void confirmVisible(String chassis, String recordId) {
        prepare();
        var state = recover();
        var original = originals.find(and(eq("instanceId", chassis), eq("recordId", recordId),
                eq("storageClass", OPERATIONAL))).first();
        if (original == null) {
            throw unavailable();
        }
        if (!original.containsKey("visibleAtMicros")) {
            publish(state, original);
        }
    }

    public Latest latest(String chassis) {
        var cut = issueCut();
        return readAt(chassis, cut);
    }

    public Latest latestAt(String chassis, Instant asOf) {
        // Past cuts require no new watermark advance. Reconciliation keeps future
        // callers out when publication state cannot be confirmed.
        checkCut(asOf);
        return readAt(chassis, asOf);
    }

    private synchronized Instant issueCut() {
        prepare();
        var state = recover();
        var cut = micros(clock.instant());
        if (cut < state.floorMicros()) {
            throw unavailable();
        }
        // Persist the cut before returning it. The next publication must be strictly
        // later, even after a restart or with more than one event per clock tick.
        save(state, cut, null, true);
        return instant(cut);
    }

    private synchronized void checkCut(Instant asOf) {
        prepare();
        var state = recover();
        if (micros(asOf) > state.floorMicros() || asOf.isAfter(clock.instant())) {
            throw new IllegalArgumentException("Invalid visibility cut");
        }
    }

    private Latest readAt(String chassis, Instant cut) {
        var items = new ArrayList<LatestMeasurement>(DEFINITIONS.size());
        for (var definition : DEFINITIONS) {
            var document = originals.find(and(eq("instanceId", chassis), eq("storageClass", OPERATIONAL),
                    eq("original.definitionId", definition), lte("visibleAtMicros", micros(cut)),
                    lte("receivedAtMicros", micros(cut))))
                    .sort(descending("occurredAtSecond", "occurredAtNano", "recordId")).first();
            if (document != null) {
                items.add(new LatestMeasurement(measurement(document), Freshness.UNKNOWN));
            }
        }
        return new Latest(cut, List.copyOf(items));
    }

    private State recover() {
        var document = stateCollection.find(eq("_id", STATE_ID)).first();
        if (document == null) {
            if (prepared) {
                throw unavailable();
            }
            var initial = new State(new ObjectId(), Long.MIN_VALUE, null, false);
            if (!stateCollection.insertOne(initial.document()).wasAcknowledged()) {
                throw unavailable();
            }
            return initial;
        }
        var state = State.read(document);
        // A late request from an uncertain prior write can match only its old revision.
        // This acknowledged CAS fences it before the state is used again.
        var fenced = save(state, state.floorMicros(), state.pending(), state.legacyComplete());
        return fenced.pending() == null ? fenced : complete(fenced);
    }

    private State publish(State state, Document original) {
        var receivedAt = ceilingMicros(Instant.parse(original.getString("receivedAt")));
        var position = Math.max(Math.incrementExact(state.floorMicros()),
                Math.max(micros(clock.instant()), receivedAt));
        var reservation = new Pending(original.getObjectId("_id"), position);
        return complete(save(state, position, reservation, state.legacyComplete()));
    }

    private State complete(State state) {
        var pending = state.pending();
        var original = originals.find(and(eq("_id", pending.originalId()), eq("storageClass", OPERATIONAL))).first();
        if (original == null) {
            throw unavailable();
        }
        var occurrence = Instant.parse(original.get("original", Document.class).getString("occurredAt"));
        var receivedAt = ceilingMicros(Instant.parse(original.getString("receivedAt")));
        if (receivedAt > pending.position()) {
            throw unavailable();
        }

        // Reapplying a reservation changes only its durability fence. A timeout
        // cannot move this document to a different point in the visibility order.
        requireModified(originals.updateOne(and(eq("_id", pending.originalId()),
                or(exists("visibleAtMicros", false), eq("visibleAtMicros", pending.position()))),
                combine(set("visibleAtMicros", pending.position()), set("receivedAtMicros", receivedAt),
                        set("occurredAtSecond", occurrence.getEpochSecond()), set("occurredAtNano", occurrence.getNano()),
                        set("durabilityFence", new ObjectId()))));
        return save(state, state.floorMicros(), null, state.legacyComplete());
    }

    private State save(State previous, long floor, Pending pending, boolean legacyComplete) {
        var next = new State(new ObjectId(), floor, pending, legacyComplete);
        requireModified(stateCollection.replaceOne(
                and(eq("_id", STATE_ID), eq("revision", previous.revision())), next.document()));
        return next;
    }

    private static void requireModified(UpdateResult result) {
        if (!result.wasAcknowledged() || result.getMatchedCount() != 1 || result.getModifiedCount() != 1) {
            throw unavailable();
        }
    }

    private static IllegalStateException unavailable() {
        return new IllegalStateException("Measurement visibility could not be confirmed");
    }

    private static long micros(Instant instant) {
        return Math.addExact(Math.multiplyExact(instant.getEpochSecond(), MICROS_PER_SECOND), instant.getNano() / 1000);
    }

    private static long ceilingMicros(Instant instant) {
        var micros = micros(instant);
        return instant.getNano() % 1000 == 0 ? micros : Math.incrementExact(micros);
    }

    private static Instant instant(long micros) {
        return Instant.ofEpochSecond(Math.floorDiv(micros, MICROS_PER_SECOND),
                Math.floorMod(micros, MICROS_PER_SECOND) * 1000);
    }

    private static Measurement measurement(Document document) {
        var original = document.get("original", Document.class);
        var payload = original.get("payload", Document.class);
        var quality = DataRecord.Quality.valueOf(payload.getString("quality"));
        var value = quality == DataRecord.Quality.VALID ? new BigDecimal(payload.getString("value")) : null;
        return new Measurement(document.getString("recordId"), original.getString("definitionId"), value,
                payload.getString("unit"), quality, Instant.parse(original.getString("occurredAt")),
                Instant.parse(document.getString("receivedAt")),
                new HistoricalContext(document.get("context", Document.class).getString("sourceId"),
                        original.getString("appliedConfigVersion")));
    }

    private record State(ObjectId revision, long floorMicros, Pending pending, boolean legacyComplete) {
        private Document document() {
            return new Document("_id", STATE_ID).append("revision", revision).append("floorMicros", floorMicros)
                    .append("pending", pending == null ? null : pending.document()).append("legacyComplete", legacyComplete);
        }

        private static State read(Document state) {
            return new State(state.getObjectId("revision"), state.getLong("floorMicros"),
                    Pending.read(state.get("pending", Document.class)), state.getBoolean("legacyComplete"));
        }
    }

    private record Pending(ObjectId originalId, long position) {
        private Document document() {
            return new Document("originalId", originalId).append("position", position);
        }

        private static Pending read(Document pending) {
            return pending == null ? null : new Pending(pending.getObjectId("originalId"), pending.getLong("position"));
        }
    }

    public record Latest(Instant asOf, List<LatestMeasurement> items) {
    }

    public record LatestMeasurement(Measurement measurement, Freshness freshness) {
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Measurement(String recordId, String definitionId, BigDecimal value, String unit,
            DataRecord.Quality quality, Instant occurredAt, Instant receivedAt, HistoricalContext context) {
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record HistoricalContext(String sourceId, String appliedConfigVersion) {
    }

    public enum Freshness {
        UNKNOWN
    }
}
