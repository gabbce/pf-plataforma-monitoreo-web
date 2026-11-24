package ar.edu.utn.frsf.pfc.management.catalog;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DemoFixtureLoader {

    private static final String MACHINE_TYPE = "embolsadora";
    private static final String CHASSIS = "000123";
    private static final String ENGINE = "engine";
    private static final String RPM = "engine.rpm";
    private static final String COOLANT_TEMPERATURE = "engine.coolant_temperature";
    private static final OffsetDateTime START = OffsetDateTime.parse("2026-01-15T12:00:00Z");

    private final JdbcClient jdbc;

    public DemoFixtureLoader(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public void load() {
        // Keep concurrent invocations of this fixture in a single order across processes.
        jdbc.sql("SELECT 1 FROM pg_advisory_xact_lock(158303, 1)").query(Integer.class).single();

        ensure("machine_type", MACHINE_TYPE, "type_id", "model", MACHINE_TYPE, "Embolsadora de forrajes");
        ensure("machine_instance", CHASSIS, "instance_id", "type_id", CHASSIS, MACHINE_TYPE);
        ensure("subsystem_definition", MACHINE_TYPE + "/" + ENGINE, "type_id, subsystem_id", "name",
                MACHINE_TYPE, ENGINE, "Motor");
        ensureDefinition(RPM, "RPM del motor", "rpm");
        ensureDefinition(COOLANT_TEMPERATURE, "Temperatura del refrigerante", "°C");
        ensureSource("rpm-b");
        ensureSource("coolant-a");
        ensureBinding(RPM, "rpm-b");
        ensureBinding(COOLANT_TEMPERATURE, "coolant-a");
    }

    private void ensureDefinition(String id, String name, String unit) {
        var auditId = MACHINE_TYPE + "/" + id;
        ensure("record_definition", auditId, "type_id, definition_id", "subsystem_id, kind, name",
                MACHINE_TYPE, id, ENGINE, "DATA", name);
        ensure("data_definition", auditId, "type_id, definition_id", "kind, classification, value_type, unit",
                MACHINE_TYPE, id, "DATA", "ACQUIRED", "NUMBER", unit);
    }

    private void ensureSource(String sourceId) {
        ensure("data_source", CHASSIS + "/" + sourceId, "source_id", "instance_id, origin, reference",
                sourceId, CHASSIS, "SENSOR", sourceId);
    }

    private void ensureBinding(String definitionId, String sourceId) {
        var auditId = CHASSIS + "/" + definitionId + "/" + START;
        ensure("data_source_binding", auditId, "instance_id, definition_id, valid_from", "type_id, source_id, valid_until",
                CHASSIS, definitionId, START, MACHINE_TYPE, sourceId, null);
    }

    // The SQL identifiers below come only from the fixed fixture declarations above.
    private void ensure(String table, String id, String keyColumns, String valueColumns, Object... values) {
        var columnList = keyColumns + ", " + valueColumns;
        var columns = columnList.split(", ");
        var parameters = new LinkedHashMap<String, Object>();
        for (int index = 0; index < columns.length; index++) {
            parameters.put(columns[index], values[index]);
        }

        var placeholders = Arrays.stream(columns).map(column -> ":" + column).collect(Collectors.joining(", "));
        var inserted = jdbc.sql("""
                INSERT INTO %s (%s) VALUES (%s)
                ON CONFLICT (%s) DO NOTHING
                """.formatted(table, columnList, placeholders, keyColumns))
                .params(parameters)
                .update();
        if (inserted == 1) {
            audit(table, id);
            return;
        }

        var matches = Arrays.stream(columns)
                .map(column -> column + " IS NOT DISTINCT FROM :" + column)
                .collect(Collectors.joining(" AND "));
        var identical = jdbc.sql("SELECT EXISTS (SELECT 1 FROM %s WHERE %s FOR UPDATE)".formatted(table, matches))
                .params(parameters)
                .query(Boolean.class)
                .single();
        if (!identical) {
            throw new IllegalStateException("Conflicting demo fixture element " + table + " " + id);
        }
    }

    private void audit(String table, String id) {
        jdbc.sql("""
                INSERT INTO audit_record (actor_type, actor_reference, action, element_type, element_id, occurred_at)
                VALUES ('SISTEMA', 'p1-demo-fixture', 'CREATE', :table, :id, CURRENT_TIMESTAMP)
                """)
                .param("table", table)
                .param("id", id)
                .update();
    }
}
