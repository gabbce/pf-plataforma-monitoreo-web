package ar.edu.utn.frsf.pfc.management;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import ar.edu.utn.frsf.pfc.management.catalog.DataSourceResolver;
import ar.edu.utn.frsf.pfc.management.catalog.DemoFixtureLoader;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ManagementPersistenceIT {

    private static final ManagementPostgres POSTGRES = new ManagementPostgres();
    private static final Instant START = Instant.parse("2026-01-15T12:00:00Z");

    private static JdbcClient jdbc;

    @BeforeAll
    static void start() throws Exception {
        POSTGRES.start();
        jdbc = POSTGRES.jdbc();
        assertThat(countMachines()).isZero();
        assertThat(auditCount()).isZero();
    }

    @AfterAll
    static void stop() {
        POSTGRES.close();
    }

    @BeforeEach
    void clearFixture() {
        jdbc.sql("""
                TRUNCATE machine_type, machine_instance, subsystem_definition, record_definition, data_definition,
                         data_source, data_source_binding, audit_record RESTART IDENTITY
                """).update();
    }

    @Test
    void normalStartupMigratesWithoutLoadingDemoDataAndReportsHealth() throws Exception {
        var flyway = POSTGRES.bean(Flyway.class);
        flyway.validate();
        assertThat(flyway.migrate().migrationsExecuted).isZero();
        assertThat(POSTGRES.bean(DataSourceResolver.class).findSourceId("000123", "engine.rpm", START)).isEmpty();

        try (var client = HttpClient.newHttpClient()) {
            var request = HttpRequest.newBuilder(
                    URI.create("http://127.0.0.1:" + POSTGRES.port() + "/actuator/health"))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(200);
            var health = JsonMapper.builder().build().readTree(response.body());
            assertThat(health.path("status").asText()).isEqualTo("UP");
            assertThat(health.has("components")).isFalse();
        }
    }

    @Test
    void explicitCommandLoadsTextChassisAndExpectedSourcesWithoutStartingWeb() throws Exception {
        var javaExecutable = Path.of(System.getProperty("java.home"), "bin", "java");
        var process = new ProcessBuilder(javaExecutable.toString(), "-jar", System.getProperty("management.jar"),
                "load-demo-fixture");
        process.environment().put("SPRING_DATASOURCE_URL", POSTGRES.jdbcUrl());
        process.environment().put("SPRING_DATASOURCE_USERNAME", "management_app");
        process.environment().put("SPRING_DATASOURCE_PASSWORD", POSTGRES.password());
        // A web server in the fixture process would attempt to bind the running service's port.
        process.environment().put("SERVER_PORT", Integer.toString(POSTGRES.port()));
        process.redirectErrorStream(true);
        var log = Files.createTempFile("management-fixture-", ".log");
        var command = process.redirectOutput(log.toFile()).start();
        try {
            assertThat(command.waitFor(40, TimeUnit.SECONDS)).isTrue();
            assertThat(command.exitValue()).withFailMessage(Files.readString(log)).isZero();
        } finally {
            if (command.isAlive()) {
                command.destroyForcibly().waitFor();
            }
            Files.deleteIfExists(log);
        }

        assertThat(jdbc.sql("SELECT instance_id FROM machine_instance").query(String.class).single())
                .isEqualTo("000123");
        assertThat(jdbc.sql("SELECT unit FROM data_definition ORDER BY definition_id").query(String.class).list())
                .containsExactly("°C", "rpm");
        var resolver = POSTGRES.bean(DataSourceResolver.class);
        assertThat(resolver.findSourceId("123", "engine.rpm", START)).isEmpty();
        assertThat(resolver.findSourceId("000123", "engine.rpm", START.minusNanos(1))).isEmpty();
        assertThat(resolver.findSourceId("000123", "engine.rpm", START)).contains("rpm-b");
        assertThat(resolver.findSourceId("000123", "engine.coolant_temperature", START)).contains("coolant-a");
        assertThat(auditCount()).isEqualTo(11);
        assertThat(jdbc.sql("SELECT DISTINCT actor_type FROM audit_record").query(String.class).single())
                .isEqualTo("SISTEMA");
    }

    @Test
    void concurrentLoadsAndRepeatProduceOneSetOfDataAndAudit() throws Exception {
        var loader = POSTGRES.bean(DemoFixtureLoader.class);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = executor.submit(() -> {
                start.await();
                loader.load();
                return null;
            });
            var second = executor.submit(() -> {
                start.await();
                loader.load();
                return null;
            });
            start.countDown();
            first.get(15, TimeUnit.SECONDS);
            second.get(15, TimeUnit.SECONDS);
        }
        assertThat(countMachines()).isEqualTo(1);
        assertThat(auditCount()).isEqualTo(11);
        var before = auditSnapshot();
        loader.load();
        POSTGRES.provision();
        assertThat(auditSnapshot()).isEqualTo(before);
        assertThat(jdbc.sql("SELECT count(*) FROM data_source_binding").query(Integer.class).single()).isEqualTo(2);
    }

    @Test
    void incompatibleExistingDefinitionRollsBackPartialLoad() {
        jdbc.sql("INSERT INTO machine_type VALUES ('embolsadora', 'Embolsadora de forrajes')").update();
        jdbc.sql("""
                INSERT INTO record_definition (type_id, definition_id, kind, name)
                VALUES ('embolsadora', 'engine.coolant_temperature', 'DATA', 'Incompatible')
                """).update();

        assertThatThrownBy(() -> POSTGRES.bean(DemoFixtureLoader.class).load())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("engine.coolant_temperature");

        assertThat(countMachines()).isZero();
        assertThat(auditCount()).isZero();
        assertThat(jdbc.sql("SELECT name FROM record_definition").query(String.class).list())
                .containsExactly("Incompatible");
        assertThat(jdbc.sql("SELECT count(*) FROM subsystem_definition").query(Integer.class).single()).isZero();
        assertThat(jdbc.sql("SELECT count(*) FROM data_definition").query(Integer.class).single()).isZero();
    }

    private static int countMachines() {
        return jdbc.sql("SELECT count(*) FROM machine_instance").query(Integer.class).single();
    }

    private static int auditCount() {
        return jdbc.sql("SELECT count(*) FROM audit_record").query(Integer.class).single();
    }

    private static String auditSnapshot() {
        return jdbc.sql("SELECT CAST(jsonb_agg(to_jsonb(a) ORDER BY audit_id) AS text) FROM audit_record a")
                .query(String.class)
                .single();
    }
}
