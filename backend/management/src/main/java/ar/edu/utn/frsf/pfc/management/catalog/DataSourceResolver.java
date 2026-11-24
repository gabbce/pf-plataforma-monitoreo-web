package ar.edu.utn.frsf.pfc.management.catalog;

import java.time.Instant;
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
        // PostgreSQL stores microseconds. Truncation preserves the side of a stored boundary.
        var occurrence = occurredAt.truncatedTo(ChronoUnit.MICROS).atOffset(ZoneOffset.UTC);
        return jdbc.sql("""
                SELECT source_id
                FROM data_source_binding
                WHERE instance_id = :instanceId
                  AND definition_id = :definitionId
                  AND tstzrange(valid_from, valid_until, '[)') @> CAST(:occurrence AS timestamptz)
                """)
                .param("instanceId", instanceId)
                .param("definitionId", definitionId)
                .param("occurrence", occurrence)
                .query(String.class)
                .optional();
    }
}
