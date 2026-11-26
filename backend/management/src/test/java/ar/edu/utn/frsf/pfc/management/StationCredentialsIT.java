package ar.edu.utn.frsf.pfc.management;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import ar.edu.utn.frsf.pfc.management.station.StationCredentials;
import ar.edu.utn.frsf.pfc.management.station.StationOperation;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import tools.jackson.databind.json.JsonMapper;

import static ar.edu.utn.frsf.pfc.management.station.StationCredentials.Outcome.ALREADY_EXISTS;
import static ar.edu.utn.frsf.pfc.management.station.StationCredentials.Outcome.ALREADY_REVOKED;
import static ar.edu.utn.frsf.pfc.management.station.StationCredentials.Outcome.CREATED;
import static ar.edu.utn.frsf.pfc.management.station.StationCredentials.Outcome.REVOKED;
import static ar.edu.utn.frsf.pfc.management.station.StationOperation.CONFIGURATION_READ;
import static ar.edu.utn.frsf.pfc.management.station.StationOperation.CONFIGURATION_RESULTS_WRITE;
import static ar.edu.utn.frsf.pfc.management.station.StationOperation.RECORDS_WRITE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StationCredentialsIT {

    private static final ManagementPostgres POSTGRES = new ManagementPostgres();
    private static final String INSTANCE = "000123";
    private static final String PRODUCER = "station-a";
    private static final String TOKEN = "0123456789abcdef".repeat(4);
    private static final String OTHER_TOKEN = "fedcba9876543210".repeat(4);
    private static final String RECORDS_TOKEN = "abaef0123456789c".repeat(4);
    private static final String PROVISION = "provision-station";
    private static final String REVOKE = "revoke-station";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @TempDir
    private static Path secrets;

    private static JdbcClient jdbc;
    private static StationCredentials credentials;

    @BeforeAll
    static void startFromV3() throws Exception {
        POSTGRES.startDatabase();
        var dataSource = new DriverManagerDataSource(POSTGRES.jdbcUrl(), "management_app", POSTGRES.password());
        dataSource.setSchema("management");
        Flyway.configure().dataSource(dataSource).defaultSchema("management").createSchemas(false)
                .target("3").load().migrate();
        var legacy = JdbcClient.create(dataSource);
        legacy.sql("INSERT INTO machine_type VALUES ('embolsadora', 'Embolsadora')").update();
        legacy.sql("INSERT INTO machine_instance VALUES ('000123', 'embolsadora'), ('000124', 'embolsadora')").update();
        var user = legacy.sql("""
                INSERT INTO web_user (identifier, name, email, active, password_hash)
                VALUES ('legacy', 'Legacy', 'legacy@example.invalid', true, 'v3-password-hash') RETURNING user_id
                """).query(Long.class).single();
        legacy.sql("""
                INSERT INTO web_session (session_digest, user_id, expires_at, logout_csrf_token)
                VALUES (:digest, :user, '2030-01-01T00:00:00Z', 'v3-csrf')
                """)
                .param("digest", "f".repeat(64)).param("user", user).update();
        legacy.sql("""
                INSERT INTO audit_record (actor_type, actor_reference, action, element_type, element_id, occurred_at)
                VALUES ('SISTEMA', 'v3-preexisting', 'CREATE', 'machine_instance', '000123', CURRENT_TIMESTAMP)
                """).update();

        Files.writeString(secrets.resolve("pfc.internal.records-token"), RECORDS_TOKEN + "\n");
        POSTGRES.startApplication(Map.of("spring.config.import", "configtree:" + secrets + "/"));
        jdbc = POSTGRES.jdbc();
        credentials = POSTGRES.bean(StationCredentials.class);
        assertThat(credentialCount()).isZero();
        assertThat(auditEntries()).isEmpty();
    }

    @AfterAll
    static void stop() {
        POSTGRES.close();
    }

    @BeforeEach
    void clearCredentials() {
        jdbc.sql("TRUNCATE station_credential_operation, station_credential RESTART IDENTITY").update();
        jdbc.sql("DELETE FROM audit_record WHERE element_type = 'station_credential'").update();
    }

    @Test
    void v4PreservesV3CatalogSessionsAndAuditWithoutProvisioningStation() {
        var flyway = POSTGRES.bean(Flyway.class);
        flyway.validate();
        assertThat(flyway.info().applied()).extracting(migration -> migration.getVersion().getVersion())
                .containsExactly("1", "2", "3", "4");
        assertThat(jdbc.sql("SELECT instance_id FROM machine_instance ORDER BY instance_id").query(String.class).list())
                .containsExactly(INSTANCE, "000124");
        assertThat(jdbc.sql("SELECT logout_csrf_token FROM web_session").query(String.class).single())
                .isEqualTo("v3-csrf");
        assertThat(jdbc.sql("SELECT element_id FROM audit_record WHERE actor_reference = 'v3-preexisting'")
                .query(String.class).single()).isEqualTo(INSTANCE);
        assertThat(credentialCount()).isZero();
        assertThat(auditEntries()).isEmpty();
    }

    @Test
    void executableJarProvisionsAndRevokesWithOneAuditPerChange() throws Exception {
        assertOutcome(command(TOKEN + "\r\n", PROVISION, INSTANCE, PRODUCER, RECORDS_WRITE.name()), CREATED);

        var digest = tokenDigest(TOKEN);
        assertThat(jdbc.sql("SELECT token_digest FROM station_credential").query((row, index) -> row.getBytes(1))
                .single()).isEqualTo(digest);
        assertThat(jdbc.sql("SELECT to_jsonb(c)::text FROM station_credential c").query(String.class).single()
                .contains(TOKEN)).isFalse();

        assertOutcome(command(TOKEN + "\n", PROVISION, INSTANCE, PRODUCER, RECORDS_WRITE.name()), ALREADY_EXISTS);
        assertThat(credentials.authorizes(INSTANCE, PRODUCER, RECORDS_WRITE, bytes(TOKEN))).isTrue();
        assertThat(auditEntries()).containsExactly("SISTEMA/p1-station-provision/CREATE/1");

        assertOutcome(command("", REVOKE, INSTANCE, PRODUCER), REVOKED);
        var revokedAt = jdbc.sql("SELECT revoked_at FROM station_credential").query(OffsetDateTime.class).single();
        assertOutcome(command("", REVOKE, INSTANCE, PRODUCER), ALREADY_REVOKED);
        assertOutcome(command(TOKEN, PROVISION, INSTANCE, PRODUCER, RECORDS_WRITE.name()), ALREADY_REVOKED);
        assertThat(credentials.authorizes(INSTANCE, PRODUCER, RECORDS_WRITE, bytes(TOKEN))).isFalse();
        assertThat(jdbc.sql("SELECT revoked_at FROM station_credential").query(OffsetDateTime.class).single())
                .isEqualTo(revokedAt);
        assertThat(credentialCount()).isOne();
        assertThat(auditEntries()).containsExactly("SISTEMA/p1-station-provision/CREATE/1", "SISTEMA/p1-station-revoke/REVOKE/1");
    }

    @Test
    void authorizesOnlyTheExactInstanceProducerOperationAndToken() {
        var operations = Set.of(RECORDS_WRITE, CONFIGURATION_RESULTS_WRITE);
        assertThat(credentials.provision(INSTANCE, PRODUCER, operations, bytes(TOKEN))).isEqualTo(CREATED);
        assertThat(credentials.provision(INSTANCE, PRODUCER, Set.of(CONFIGURATION_RESULTS_WRITE, RECORDS_WRITE),
                bytes(TOKEN))).isEqualTo(ALREADY_EXISTS);
        assertThat(credentials.provision(INSTANCE, "Station-A", Set.of(RECORDS_WRITE), bytes(OTHER_TOKEN)))
                .isEqualTo(CREATED);
        assertThat(credentials.authorizes(INSTANCE, PRODUCER, RECORDS_WRITE, bytes(TOKEN))).isTrue();
        assertThat(credentials.authorizes(INSTANCE, PRODUCER, CONFIGURATION_RESULTS_WRITE, bytes(TOKEN))).isTrue();
        assertThat(credentials.authorizes(INSTANCE, PRODUCER, CONFIGURATION_READ, bytes(TOKEN))).isFalse();
        assertThat(credentials.authorizes("123", PRODUCER, RECORDS_WRITE, bytes(TOKEN))).isFalse();
        assertThat(credentials.authorizes("000124", PRODUCER, RECORDS_WRITE, bytes(TOKEN))).isFalse();
        assertThat(credentials.authorizes(INSTANCE, "Station-A", RECORDS_WRITE, bytes(TOKEN))).isFalse();
        assertThat(credentials.authorizes(INSTANCE, PRODUCER + " ", RECORDS_WRITE, bytes(TOKEN))).isFalse();
        assertThat(credentials.authorizes(INSTANCE, PRODUCER, RECORDS_WRITE, bytes(OTHER_TOKEN))).isFalse();
        assertThat(credentials.authorizes(INSTANCE, PRODUCER, RECORDS_WRITE, bytes("invalid"))).isFalse();
        assertThat(credentials.authorizes(INSTANCE, PRODUCER, null, bytes(TOKEN))).isFalse();
    }

    @Test
    void checksCurrentStationGrantForRecordsWithTheExactTextualIdentity() throws Exception {
        credentials.provision(INSTANCE, PRODUCER, Set.of(RECORDS_WRITE, CONFIGURATION_READ), bytes(TOKEN));
        credentials.provision(INSTANCE, "station-config", Set.of(CONFIGURATION_READ), bytes(OTHER_TOKEN));

        try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()) {
            assertAuthorization(client, INSTANCE, PRODUCER, RECORDS_WRITE, TOKEN, 204);
            assertAuthorization(client, "123", PRODUCER, RECORDS_WRITE, TOKEN, 403);
            assertAuthorization(client, "000124", PRODUCER, RECORDS_WRITE, TOKEN, 403);
            assertAuthorization(client, INSTANCE, "Station-A", RECORDS_WRITE, TOKEN, 403);
            assertAuthorization(client, INSTANCE, PRODUCER, RECORDS_WRITE, OTHER_TOKEN, 403);
            assertAuthorization(client, INSTANCE, PRODUCER, RECORDS_WRITE, "invalid", 403);
            assertAuthorization(client, INSTANCE, PRODUCER, CONFIGURATION_READ, TOKEN, 403);
            assertAuthorization(client, INSTANCE, "station-config", RECORDS_WRITE, OTHER_TOKEN, 403);

            assertThat(credentials.revoke(INSTANCE, PRODUCER)).isEqualTo(REVOKED);
            assertAuthorization(client, INSTANCE, PRODUCER, RECORDS_WRITE, TOKEN, 403);
        }
    }

    @Test
    void conflictsAndUnknownIdentitiesPreserveCredentialsAndAudit() {
        assertThat(credentials.provision(INSTANCE, PRODUCER, Set.of(RECORDS_WRITE), bytes(TOKEN))).isEqualTo(CREATED);
        assertThatThrownBy(() -> credentials.provision(INSTANCE, PRODUCER, Set.of(RECORDS_WRITE), bytes(OTHER_TOKEN)))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Conflicting Station credential");
        assertThatThrownBy(() -> credentials.provision(INSTANCE, PRODUCER, Set.of(CONFIGURATION_READ), bytes(TOKEN)))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Conflicting Station credential");
        assertThatThrownBy(() -> credentials.provision("000124", PRODUCER, Set.of(RECORDS_WRITE), bytes(TOKEN)))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Conflicting Station credential");
        assertThatThrownBy(() -> credentials.provision(INSTANCE, "station-b", Set.of(RECORDS_WRITE), bytes(TOKEN)))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Conflicting Station credential");
        assertThatThrownBy(() -> credentials.provision("123", PRODUCER, Set.of(RECORDS_WRITE), bytes(OTHER_TOKEN)))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Unknown machine instance");
        assertThatThrownBy(() -> credentials.revoke(INSTANCE, "station-b"))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Unknown Station credential");

        assertThat(credentialCount()).isOne();
        assertThat(credentials.authorizes(INSTANCE, PRODUCER, RECORDS_WRITE, bytes(TOKEN))).isTrue();
        assertThat(credentials.authorizes(INSTANCE, PRODUCER, CONFIGURATION_READ, bytes(TOKEN))).isFalse();
        assertThat(auditEntries()).containsExactly("SISTEMA/p1-station-provision/CREATE/1");

        assertThat(credentials.revoke(INSTANCE, PRODUCER)).isEqualTo(REVOKED);
        assertThatThrownBy(() -> credentials.provision(INSTANCE, PRODUCER, Set.of(RECORDS_WRITE), bytes(OTHER_TOKEN)))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Conflicting Station credential");
        assertThatThrownBy(() -> credentials.provision("000124", PRODUCER, Set.of(RECORDS_WRITE), bytes(TOKEN)))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Conflicting Station credential");
        assertThat(credentialCount()).isOne();
        assertThat(auditEntries()).hasSize(2);
    }

    @Test
    void commandRejectsMalformedInputAndReportsFailuresWithoutSecrets() throws Exception {
        for (var malformed : List.of("", TOKEN.substring(1), TOKEN.toUpperCase(Locale.ROOT),
                TOKEN + "\r", TOKEN + "\n\n", TOKEN + "\r\n\n", TOKEN + "\n" + OTHER_TOKEN)) {
            assertFailure(command(malformed, PROVISION, INSTANCE, PRODUCER, RECORDS_WRITE.name()),
                    "64 lowercase hexadecimal characters");
        }
        assertFailure(command(TOKEN, PROVISION, INSTANCE, PRODUCER), "Usage");
        assertFailure(command("", PROVISION, INSTANCE, PRODUCER, RECORDS_WRITE.name(), TOKEN),
                "Unknown Station operation");
        assertFailure(command("", PROVISION, INSTANCE, PRODUCER, RECORDS_WRITE.name(), RECORDS_WRITE.name()),
                "Duplicate Station operation");
        assertFailure(command("", REVOKE, INSTANCE, PRODUCER, TOKEN), "Usage");
        assertFailure(command("", REVOKE, INSTANCE, PRODUCER), "Unknown Station credential");
        assertFailure(command(TOKEN, PROVISION, "123", PRODUCER, RECORDS_WRITE.name()), "Unknown machine instance");
        assertThat(credentialCount()).isZero();
        assertThat(auditEntries()).isEmpty();

        credentials.provision(INSTANCE, PRODUCER, Set.of(RECORDS_WRITE), bytes(TOKEN));
        assertFailure(command(TOKEN, PROVISION, "000124", PRODUCER, RECORDS_WRITE.name()),
                "Conflicting Station credential");
        assertThat(credentialCount()).isOne();
        assertThat(auditEntries()).hasSize(1);
    }

    @Test
    void concurrentIdenticalCommandsAndRevocationsChangeStateOnce() throws Exception {
        assertThat(concurrently(() -> credentials.provision(INSTANCE, PRODUCER, Set.of(RECORDS_WRITE), bytes(TOKEN))))
                .containsExactlyInAnyOrder(CREATED, ALREADY_EXISTS);
        assertThat(concurrently(() -> credentials.revoke(INSTANCE, PRODUCER)))
                .containsExactlyInAnyOrder(REVOKED, ALREADY_REVOKED);
        assertThat(credentialCount()).isOne();
        assertThat(auditEntries()).containsExactly("SISTEMA/p1-station-provision/CREATE/1", "SISTEMA/p1-station-revoke/REVOKE/1");
    }

    @Test
    void failedAuditRollsBackProvisionAndRevocation() throws Exception {
        jdbc.sql("""
                ALTER TABLE audit_record ADD CONSTRAINT station_test_block_create
                CHECK (element_type <> 'station_credential')
                """).update();
        try {
            assertFailure(command(TOKEN, PROVISION, INSTANCE, PRODUCER, RECORDS_WRITE.name()),
                    "Station credential command failed");
            assertThat(credentialCount()).isZero();
            assertThat(jdbc.sql("SELECT count(*) FROM station_credential_operation").query(Integer.class).single())
                    .isZero();
            assertThat(auditEntries()).isEmpty();
        } finally {
            jdbc.sql("ALTER TABLE audit_record DROP CONSTRAINT station_test_block_create").update();
        }

        credentials.provision(INSTANCE, PRODUCER, Set.of(RECORDS_WRITE), bytes(TOKEN));
        var auditBeforeRevoke = auditEntries();
        assertThat(auditBeforeRevoke).hasSize(1);
        jdbc.sql("""
                ALTER TABLE audit_record ADD CONSTRAINT station_test_block_revoke
                CHECK (element_type <> 'station_credential' OR action <> 'REVOKE')
                """).update();
        try {
            assertFailure(command("", REVOKE, INSTANCE, PRODUCER), "Station credential command failed");
            assertThat(credentials.authorizes(INSTANCE, PRODUCER, RECORDS_WRITE, bytes(TOKEN))).isTrue();
            assertThat(auditEntries()).isEqualTo(auditBeforeRevoke);
        } finally {
            jdbc.sql("ALTER TABLE audit_record DROP CONSTRAINT station_test_block_revoke").update();
        }
    }

    private static void assertAuthorization(HttpClient client, String instance, String producer, StationOperation operation,
            String stationToken, int expectedStatus) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + POSTGRES.port()
                + "/internal/v1/station-authorizations/check"))
                .header("Authorization", "Bearer " + RECORDS_TOKEN)
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(5))
                .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(Map.of("instanceId", instance,
                        "producerId", producer, "operation", operation.name(), "stationToken", stationToken))))
                .build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(expectedStatus);
        assertThat(response.headers().firstValue("Cache-Control")).contains("no-store");
        assertThat(response.body()).isEmpty();
    }

    private static <T> List<T> concurrently(Callable<T> call) throws Exception {
        var start = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Callable<T> synchronizedCall = () -> {
                start.await();
                return call.call();
            };
            var first = executor.submit(synchronizedCall);
            var second = executor.submit(synchronizedCall);
            start.countDown();
            return List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS));
        }
    }

    private static CommandResult command(String input, String... args) throws Exception {
        var java = Path.of(System.getProperty("java.home"), "bin", "java");
        var command = new ArrayList<>(List.of(java.toString(), "-jar", System.getProperty("management.jar")));
        command.addAll(List.of(args));
        var builder = new ProcessBuilder(command).redirectErrorStream(true);
        builder.environment().put("SPRING_DATASOURCE_URL", POSTGRES.jdbcUrl());
        builder.environment().put("SPRING_DATASOURCE_USERNAME", "management_app");
        builder.environment().put("SPRING_DATASOURCE_PASSWORD", POSTGRES.password());
        builder.environment().put("SERVER_PORT", Integer.toString(POSTGRES.port()));
        builder.environment().put("LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_JDBC_CORE", "TRACE");
        var log = Files.createTempFile("management-station-", ".log");
        try {
            var process = builder.redirectOutput(log.toFile()).start();
            try {
                try (var stdin = process.getOutputStream()) {
                    stdin.write(bytes(input));
                }
                assertThat(process.waitFor(40, TimeUnit.SECONDS)).isTrue();
                var output = Files.readString(log);
                assertSensitiveValuesAbsent(output, TOKEN);
                assertSensitiveValuesAbsent(output, OTHER_TOKEN);
                return new CommandResult(process.exitValue(), output);
            } finally {
                if (process.isAlive()) {
                    process.destroyForcibly().waitFor();
                }
            }
        } finally {
            Files.deleteIfExists(log);
        }
    }

    private static void assertSensitiveValuesAbsent(String output, String token) throws Exception {
        var digest = tokenDigest(token);
        assertThat(output.contains(token)).as("No raw Station token in command output").isFalse();
        assertThat(output.contains(HexFormat.of().formatHex(digest))).as("No hex verifier in command output").isFalse();
        assertThat(output.contains(Base64.getEncoder().encodeToString(digest))).as("No Base64 verifier in command output")
                .isFalse();
    }

    private static byte[] tokenDigest(String token) throws Exception {
        return MessageDigest.getInstance("SHA-256").digest(bytes(token));
    }

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.US_ASCII);
    }

    private static void assertOutcome(CommandResult result, StationCredentials.Outcome outcome) {
        assertThat(result.exitCode()).withFailMessage(result.output()).isZero();
        assertThat(result.output().lines().toList()).contains(outcome.name());
    }

    private static void assertFailure(CommandResult result, String message) {
        assertThat(result.exitCode()).isNotZero();
        assertThat(result.output()).contains(message);
    }

    private static int credentialCount() {
        return jdbc.sql("SELECT count(*) FROM station_credential").query(Integer.class).single();
    }

    private static List<String> auditEntries() {
        return jdbc.sql("""
                SELECT actor_type || '/' || actor_reference || '/' || action || '/' || element_id
                FROM audit_record WHERE element_type = 'station_credential' ORDER BY audit_id
                """).query(String.class).list();
    }

    private record CommandResult(int exitCode, String output) {
    }
}
