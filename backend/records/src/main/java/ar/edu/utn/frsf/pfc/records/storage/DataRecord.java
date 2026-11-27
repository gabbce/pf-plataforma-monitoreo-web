package ar.edu.utn.frsf.pfc.records.storage;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

import ar.edu.utn.frsf.pfc.records.station.DataContextClient.TimeValidity;

public record DataRecord(
        int schemaVersion, String instanceId, String recordId, String producerId, long sequence,
        String definitionId, String subsystemId, Instant occurredAt, TimeValidity timeValidity,
        String appliedConfigVersion, Payload payload) {

    public DataRecord {
        Objects.requireNonNull(instanceId);
        Objects.requireNonNull(recordId);
        Objects.requireNonNull(producerId);
        Objects.requireNonNull(definitionId);
        Objects.requireNonNull(timeValidity);
        Objects.requireNonNull(payload);
        if (timeValidity == TimeValidity.VALID) {
            Objects.requireNonNull(occurredAt);
        }
    }

    public record Payload(BigDecimal value, String unit, Quality quality) {
        public Payload {
            Objects.requireNonNull(unit);
            Objects.requireNonNull(quality);
        }
    }

    public enum Quality {
        VALID, MISSING, INVALID, NOT_EVALUABLE
    }
}
