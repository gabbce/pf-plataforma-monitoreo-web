package ar.edu.utn.frsf.pfc.management.catalog;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class DataSourceResolver {

    private final JdbcClient jdbc;

    public DataSourceResolver(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<String> findSourceId(String instanceId, String definitionId, Instant occurredAt) {
        return findDataContext(instanceId, definitionId, occurredAt).map(DataContext::sourceId);
    }

    Optional<DataContext> findDataContext(String instanceId, String definitionId, Instant occurredAt) {
        // PostgreSQL stores microseconds. Truncation preserves the side of a stored boundary.
        OffsetDateTime occurrence = occurredAt == null ? null
                : occurredAt.truncatedTo(ChronoUnit.MICROS).atOffset(ZoneOffset.UTC);
        return jdbc.sql("""
                SELECT machine.type_id, data.definition_id, definition.subsystem_id,
                       data.value_type, data.unit, binding.source_id
                FROM machine_instance machine
                JOIN data_definition data ON data.type_id = machine.type_id
                JOIN record_definition definition ON definition.type_id = data.type_id
                    AND definition.definition_id = data.definition_id
                LEFT JOIN data_source_binding binding ON binding.instance_id = machine.instance_id
                    AND binding.definition_id = data.definition_id
                    AND tstzrange(binding.valid_from, binding.valid_until, '[)') @> CAST(:occurrence AS timestamptz)
                WHERE machine.instance_id = :instanceId AND data.definition_id = :definitionId
                """)
                .param("instanceId", instanceId)
                .param("definitionId", definitionId)
                .param("occurrence", occurrence)
                .query((row, index) -> new DataContext(row.getString("type_id"), row.getString("definition_id"),
                        row.getString("subsystem_id"), row.getString("value_type"), row.getString("unit"),
                        row.getString("source_id")))
                .optional();
    }

    record DataContext(String typeId, String definitionId, String subsystemId, String valueType, String unit,
                       String sourceId) {
    }
}
