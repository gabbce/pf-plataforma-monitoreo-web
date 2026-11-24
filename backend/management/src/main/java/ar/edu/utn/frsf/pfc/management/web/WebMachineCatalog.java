package ar.edu.utn.frsf.pfc.management.web;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class WebMachineCatalog {

    private static final String SCOPE = """
            WITH grants AS (
                SELECT COALESCE(bool_or(ur.role_id = :technician), false) AS read_all,
                       COALESCE(bool_or(ur.role_id = :owner), false) AS read_owned
                FROM web_user_role ur
                JOIN web_role_permission rp ON rp.role_id = ur.role_id
                WHERE ur.user_id = :user AND rp.permission_id = :permission
            )
            """;

    private static final String CAN_READ_MACHINE = """
            (grants.read_all OR (grants.read_owned AND EXISTS (
                SELECT 1 FROM web_machine_association association
                WHERE association.user_id = :user AND association.instance_id = machine.instance_id
                  AND association.link_type = :owner
                  AND tstzrange(association.valid_from, association.valid_until, '[)') @> CAST(:now AS timestamptz)
            )))
            """;

    private final JdbcClient jdbc;
    private final Clock clock;

    WebMachineCatalog(JdbcClient jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    MachineList list(long userId) {
        var rows = authorized(SCOPE + """
                SELECT machine.instance_id
                FROM grants
                LEFT JOIN machine_instance machine ON
                """ + CAN_READ_MACHINE + """
                WHERE grants.read_all OR grants.read_owned
                ORDER BY machine.instance_id
                """, userId)
                .query((row, index) -> new MachineSummary(row.getString(1), null)).list();
        if (rows.isEmpty()) {
            throw WebProblem.ACCESS_DENIED.failure();
        }
        return new MachineList(rows.stream().filter(row -> row.chassis() != null).toList());
    }

    MachineDetail detail(long userId, String chassis) {
        var rows = authorized(SCOPE + """
                SELECT data.definition_id, definition.name, data.unit
                FROM grants
                CROSS JOIN machine_instance machine
                LEFT JOIN data_definition data ON data.type_id = machine.type_id
                    AND data.definition_id IN ('engine.rpm', 'engine.coolant_temperature')
                LEFT JOIN record_definition definition ON definition.type_id = data.type_id
                    AND definition.definition_id = data.definition_id
                WHERE machine.instance_id = :chassis AND
                """ + CAN_READ_MACHINE + """
                ORDER BY (data.definition_id = 'engine.rpm') DESC, data.definition_id
                """, userId)
                .param("chassis", chassis)
                .query((row, index) -> new MeasurementDefinition(row.getString(1), row.getString(2), row.getString(3)))
                .list();
        if (rows.isEmpty()) {
            throw WebProblem.ACCESS_DENIED.failure();
        }
        var definitions = rows.stream().filter(row -> row.definitionId() != null).toList();
        return new MachineDetail(chassis, null, definitions);
    }

    private JdbcClient.StatementSpec authorized(String sql, long userId) {
        return jdbc.sql(sql)
                .param("user", userId)
                .param("technician", WebCredentials.TECHNICAL_SERVICE)
                .param("owner", WebCredentials.OWNER)
                .param("permission", WebCredentials.MACHINE_READ)
                .param("now", OffsetDateTime.now(clock).truncatedTo(ChronoUnit.MICROS));
    }

    record MachineSummary(String chassis, Instant lastAuthenticatedContactAt) {
    }

    record MachineList(List<MachineSummary> items) {
    }

    record MachineDetail(String chassis, Instant lastAuthenticatedContactAt, List<MeasurementDefinition> definitions) {
    }

    record MeasurementDefinition(String definitionId, String name, String unit) {
    }
}
