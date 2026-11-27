package ar.edu.utn.frsf.pfc.records.ingestion;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import ar.edu.utn.frsf.pfc.records.station.DataContextClient;
import ar.edu.utn.frsf.pfc.records.station.DataContextClient.TimeValidity;
import ar.edu.utn.frsf.pfc.records.station.StationAuthorizationClient;
import ar.edu.utn.frsf.pfc.records.station.StationAuthorizationClient.Decision;
import ar.edu.utn.frsf.pfc.records.storage.DataRecord;
import ar.edu.utn.frsf.pfc.records.storage.DataRecord.Quality;
import ar.edu.utn.frsf.pfc.records.storage.MeasurementReads;
import ar.edu.utn.frsf.pfc.records.storage.StoredDataRecords;
import ar.edu.utn.frsf.pfc.records.storage.StoredDataRecords.StorageClass;
import com.fasterxml.jackson.annotation.JsonInclude;

import org.springframework.stereotype.Service;

import tools.jackson.databind.JsonNode;

@Service
class RecordsIngestion {

    private static final long AUTHORIZATION_BUDGET = Duration.ofSeconds(4).toNanos();
    private static final Set<String> RECORD_FIELDS = Set.of("schemaVersion", "recordId", "instanceId", "producerId",
            "sequence", "kind", "definitionId", "subsystemId", "occurredAt", "timeValidity", "appliedConfigVersion",
            "payload");
    private static final Set<String> PAYLOAD_FIELDS = Set.of("value", "unit", "quality");

    private final StationAuthorizationClient authorization;
    private final DataContextClient catalog;
    private final StoredDataRecords storage;
    private final MeasurementReads measurements;

    RecordsIngestion(StationAuthorizationClient authorization, DataContextClient catalog, StoredDataRecords storage,
            MeasurementReads measurements) {
        this.authorization = authorization;
        this.catalog = catalog;
        this.storage = storage;
        this.measurements = measurements;
    }

    Receipt receive(List<Submission> batch, String stationToken) {
        var checked = new HashSet<Scope>();
        var invalidScopes = new HashSet<Scope>();
        var started = System.nanoTime();
        for (var submission : batch) {
            var scope = submission.scope();
            if (scope == null || !checked.add(scope)) {
                continue;
            }
            if (System.nanoTime() - started >= AUTHORIZATION_BUDGET) {
                return new Receipt(Decision.UNAVAILABLE, null);
            }
            var decision = authorization.checkAndRecordContact(scope.instanceId(), scope.producerId(), stationToken);
            if (decision == Decision.INVALID_SCOPE) {
                invalidScopes.add(scope);
                continue;
            }
            if (decision == Decision.UNAVAILABLE) {
                return new Receipt(decision, null);
            }
            if (decision == Decision.ALLOWED) {
                // A credential digest belongs to exactly one pair. No other scope can match this bearer.
                var results = batch.stream().map(item -> process(item, scope, invalidScopes)).toList();
                return new Receipt(decision, new Results(results));
            }
        }
        return new Receipt(Decision.DENIED, null);
    }

    private Acknowledgment process(Submission item, Scope scope, Set<Scope> invalidScopes) {
        if (item.scope() == null || invalidScopes.contains(item.scope())) {
            return item.rejected(Reason.INVALID_RECORD);
        }
        if (!scope.equals(item.scope())) {
            return item.rejected(Reason.IDENTITY_OUT_OF_SCOPE);
        }
        var kind = item.json().path("kind");
        if (!identifier(kind)) {
            return item.rejected(Reason.INVALID_RECORD);
        }
        if (!kind.stringValue().equals("DATA")) {
            return item.rejected(Reason.UNSUPPORTED_KIND);
        }

        DataRecord record;
        try {
            record = dataRecord(item);
        } catch (IllegalArgumentException | DateTimeParseException failure) {
            return item.rejected(Reason.INVALID_RECORD);
        }

        try {
            measurements.prepare();
        } catch (RuntimeException failure) {
            return item.retry(Reason.STORAGE_UNAVAILABLE);
        }
        var recognition = storage.recognize(record);
        if (recognition.status() != StoredDataRecords.Status.NOT_FOUND) {
            return stored(item, recognition);
        }
        var lookup = catalog.resolve(record.instanceId(), record.definitionId(), record.timeValidity(),
                record.occurredAt());
        return switch (lookup.status()) {
            case UNAVAILABLE -> item.retry(Reason.CATALOG_UNAVAILABLE);
            case INVALID_REQUEST -> item.rejected(Reason.INVALID_RECORD);
            case NOT_FOUND -> item.rejected(Reason.DATA_CONTEXT_NOT_FOUND);
            case RESOLVED -> store(item, record, lookup.context());
        };
    }

    private Acknowledgment store(Submission item, DataRecord record, DataContextClient.Context context) {
        if (!"NUMBER".equals(context.valueType()) || !record.payload().unit().equals(context.unit())
                || !Objects.equals(record.subsystemId(), context.subsystemId())) {
            return item.rejected(Reason.DATA_CONTEXT_MISMATCH);
        }
        return stored(item, storage.store(record, context));
    }

    private Acknowledgment stored(Submission item, StoredDataRecords.Result result) {
        if (result.storageClass() == StorageClass.OPERATIONAL) {
            try {
                measurements.confirmVisible(item.scope().instanceId(), item.recordId());
            } catch (RuntimeException failure) {
                return item.retry(Reason.STORAGE_UNAVAILABLE);
            }
        }
        return switch (result.status()) {
            case STORED -> new Acknowledgment(item.recordId(), Status.ACCEPTED, result.storageClass(), null);
            case ALREADY_STORED -> new Acknowledgment(item.recordId(), Status.ALREADY_STORED, result.storageClass(), null);
            case CONFLICT -> item.rejected(Reason.RECORD_ID_CONFLICT);
            case RETRY, NOT_FOUND -> item.retry(Reason.STORAGE_UNAVAILABLE);
        };
    }

    private static DataRecord dataRecord(Submission item) {
        var json = item.json();
        var version = json.path("schemaVersion");
        var sequence = json.path("sequence");
        var payload = json.path("payload");
        require(RECORD_FIELDS.containsAll(json.propertyNames()) && version.isIntegralNumber()
                && version.canConvertToInt() && version.intValue() == 1 && sequence.isIntegralNumber()
                && sequence.canConvertToLong() && sequence.longValue() >= 0
                && payload.isObject() && payload.propertyNames().equals(PAYLOAD_FIELDS));
        var timeValidity = TimeValidity.valueOf(requiredText(json.path("timeValidity")));
        var occurredAt = occurrence(json.path("occurredAt"));
        require(timeValidity != TimeValidity.VALID || occurredAt != null);

        var quality = Quality.valueOf(requiredText(payload.path("quality")));
        var value = payload.path("value");
        require(value.isNumber() || value.isNull());
        var number = value.isNull() ? null : value.decimalValue();
        require(quality == Quality.VALID ? number != null : number == null || number.signum() != 0);

        return new DataRecord(1, item.scope().instanceId(), item.recordId(), item.scope().producerId(),
                sequence.longValue(), requiredText(json.path("definitionId")), optionalText(json.path("subsystemId")),
                occurredAt, timeValidity, optionalText(json.path("appliedConfigVersion")),
                new DataRecord.Payload(number, requiredText(payload.path("unit")), quality));
    }

    private static Instant occurrence(JsonNode node) {
        if (node.isMissingNode() || node.isNull()) {
            return null;
        }
        var text = requiredText(node);
        require(text.endsWith("Z"));
        return OffsetDateTime.parse(text).toInstant();
    }

    private static String optionalText(JsonNode node) {
        return node.isMissingNode() || node.isNull() ? null : requiredText(node);
    }

    private static String requiredText(JsonNode node) {
        require(identifier(node));
        return node.stringValue();
    }

    private static boolean identifier(JsonNode value) {
        return value.isString() && !value.stringValue().isBlank() && value.stringValue().indexOf('\0') < 0;
    }

    private static void require(boolean valid) {
        if (!valid) {
            throw new IllegalArgumentException("Invalid DATA record");
        }
    }

    private record Scope(String instanceId, String producerId) {
    }

    record Submission(String recordId, Scope scope, JsonNode json) {

        static Submission from(JsonNode node) {
            var id = node.path("recordId");
            if (!node.isObject() || !identifier(id)) {
                return null;
            }
            var instance = node.path("instanceId");
            var producer = node.path("producerId");
            var scope = identifier(instance) && identifier(producer)
                    ? new Scope(instance.stringValue(), producer.stringValue()) : null;
            return new Submission(id.stringValue(), scope, node);
        }

        Acknowledgment rejected(Reason reason) {
            return new Acknowledgment(recordId, Status.REJECTED, null, reason);
        }

        Acknowledgment retry(Reason reason) {
            return new Acknowledgment(recordId, Status.RETRY, null, reason);
        }
    }

    record Receipt(Decision decision, Results results) {
    }

    record Results(List<Acknowledgment> results) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Acknowledgment(String recordId, Status status, StorageClass storageClass, Reason reason) {
    }

    enum Status {
        ACCEPTED, ALREADY_STORED, REJECTED, RETRY
    }

    enum Reason {
        INVALID_RECORD, UNSUPPORTED_KIND, IDENTITY_OUT_OF_SCOPE, DATA_CONTEXT_NOT_FOUND, DATA_CONTEXT_MISMATCH,
        RECORD_ID_CONFLICT, CATALOG_UNAVAILABLE, STORAGE_UNAVAILABLE
    }
}
