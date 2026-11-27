package ar.edu.utn.frsf.pfc.management;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import ar.edu.utn.frsf.pfc.management.catalog.DemoFixtureLoader;
import ar.edu.utn.frsf.pfc.management.station.StationCredentials;
import ar.edu.utn.frsf.pfc.management.web.DemoWebAccountLoader;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static ar.edu.utn.frsf.pfc.management.station.StationOperation.RECORDS_WRITE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ManagementWebAccessIT {

    private static final ManagementPostgres POSTGRES = new ManagementPostgres();
    private static final HttpClient CLIENT = HttpClient.newHttpClient();
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Instant START = Instant.parse("2026-01-15T12:00:00Z");
    private static final AtomicReference<Instant> NOW = new AtomicReference<>(START);
    private static final String TECHNICIAN = "tecnico-ejemplo";
    private static final String OWNER = "propietario-ejemplo";
    private static final String UNASSOCIATED = "propietario-sin-asociacion";
    private static final String TECHNICIAN_PASSWORD = UUID.randomUUID().toString();
    private static final String OWNER_PASSWORD = UUID.randomUUID().toString();
    private static final String UNASSOCIATED_PASSWORD = UUID.randomUUID().toString();

    private JdbcClient jdbc;

    @BeforeAll
    static void start() throws Exception {
        POSTGRES.start(FixedClockConfiguration.class);
        assertThat(POSTGRES.jdbc().sql("SELECT count(*) FROM web_user").query(Integer.class).single()).isZero();
    }

    @AfterAll
    static void stop() {
        try (CLIENT; POSTGRES) {
            // The resources are shared by the tests.
        }
    }

    @BeforeEach
    void prepare() {
        jdbc = POSTGRES.jdbc();
        jdbc.sql("""
                TRUNCATE web_login_context, web_session, web_user, web_role, web_permission,
                         web_user_role, web_role_permission, web_machine_association,
                         station_credential, station_credential_operation, audit_record RESTART IDENTITY
                """).update();
        jdbc.sql("DELETE FROM machine_instance WHERE instance_id = '000124'").update();
        NOW.set(START);
        POSTGRES.bean(DemoFixtureLoader.class).load();
    }

    @Test
    void explicitAccountCommandUsesExternalPasswordsAndAuditsOnlyNewRows() throws Exception {
        var java = Path.of(System.getProperty("java.home"), "bin", "java");
        var builder = new ProcessBuilder(java.toString(), "-jar", System.getProperty("management.jar"),
                "load-demo-web-accounts");
        builder.environment().putAll(Map.of(
                "SPRING_DATASOURCE_URL", POSTGRES.jdbcUrl(),
                "SPRING_DATASOURCE_USERNAME", "management_app",
                "SPRING_DATASOURCE_PASSWORD", POSTGRES.password(),
                "SERVER_PORT", Integer.toString(POSTGRES.port()),
                "PFC_DEMO_TECHNICIAN_PASSWORD", TECHNICIAN_PASSWORD,
                "PFC_DEMO_OWNER_PASSWORD", OWNER_PASSWORD,
                "PFC_DEMO_UNASSOCIATED_OWNER_PASSWORD", UNASSOCIATED_PASSWORD));
        var log = Files.createTempFile("management-web-accounts-", ".log");
        var process = builder.redirectErrorStream(true).redirectOutput(log.toFile()).start();
        try {
            assertThat(process.waitFor(40, TimeUnit.SECONDS)).isTrue();
            assertThat(process.exitValue()).withFailMessage(Files.readString(log)).isZero();
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly().waitFor();
            }
            Files.deleteIfExists(log);
        }

        var hash = jdbc.sql("SELECT password_hash FROM web_user WHERE identifier = :id").param("id", OWNER)
                .query(String.class).single();
        assertThat(hash).isNotEqualTo(OWNER_PASSWORD);
        assertThat(POSTGRES.bean(PasswordEncoder.class).matches(OWNER_PASSWORD, hash)).isTrue();
        var before = auditCount();
        assertThat(before).isEqualTo(12);
        loadAccounts();
        assertThat(auditCount()).isEqualTo(before);

        assertThatThrownBy(() -> POSTGRES.bean(DemoWebAccountLoader.class)
                .load(TECHNICIAN_PASSWORD, UUID.randomUUID().toString(), UNASSOCIATED_PASSWORD))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining(OWNER);
        assertThat(auditCount()).isEqualTo(before);

        jdbc.sql("DELETE FROM web_user_role WHERE user_id = (SELECT user_id FROM web_user WHERE identifier = :id)")
                .param("id", OWNER).update();
        assertThatThrownBy(this::loadAccounts).isInstanceOf(IllegalStateException.class).hasMessageContaining(OWNER);
        assertThat(jdbc.sql("SELECT count(*) FROM web_user_role").query(Integer.class).single()).isEqualTo(2);
        assertThat(auditCount()).isEqualTo(before);
    }

    @Test
    void validatesCsrfPriorToCredentialsAndRotatesBothContexts() throws Exception {
        loadAccounts();
        var first = csrf(null);
        var other = csrf(null);
        assertThat(first.token()).isNotEqualTo(other.token());
        var malformed = "{\"identifier\":5,\"password\":\"example\"}";

        problem(post(malformed, null, null), 403, "INVALID_CSRF");
        problem(post(malformed, other.cookie(), first.token()), 403, "INVALID_CSRF");
        assertThat(ok(get("/auth/csrf", first.cookie())).get("token").stringValue()).isEqualTo(first.token());
        problem(post(malformed, first.cookie(), first.token()), 400, "INVALID_REQUEST");
        problem(post("{", first.cookie(), first.token()), 400, "INVALID_REQUEST");
        var unsupported = send("POST", "/auth/session", first.cookie(), first.token(), "identifier=unknown",
                "application/x-www-form-urlencoded");
        problem(unsupported, 400, "INVALID_REQUEST");

        var login = login(TECHNICIAN, TECHNICIAN_PASSWORD, first, null);
        assertThat(login.statusCode()).isEqualTo(204);
        assertThat(login.body()).isEmpty();
        assertThat(login.headers().firstValue("Cache-Control")).contains("no-store");
        var session = cookie(login, "PFC_SESSION");
        assertCookie(login, "PFC_SESSION");
        assertThat(login.headers().allValues("Set-Cookie")).anyMatch(value -> value.startsWith("PFC_LOGIN=;")
                && value.contains("Max-Age=0"));
        var expectedDigest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(
                session.substring(session.indexOf('=') + 1).getBytes(StandardCharsets.US_ASCII)));
        assertThat(jdbc.sql("SELECT session_digest FROM web_session").query(String.class).single())
                .isEqualTo(expectedDigest);
        problem(login(TECHNICIAN, TECHNICIAN_PASSWORD, first, null), 403, "INVALID_CSRF");
        assertThat(get("/machines", session).statusCode()).isEqualTo(200);

        var rotated = login(TECHNICIAN, TECHNICIAN_PASSWORD, other, session);
        assertThat(rotated.statusCode()).isEqualTo(204);
        var newSession = cookie(rotated, "PFC_SESSION");
        assertThat(newSession).isNotEqualTo(session);
        problem(get("/machines", session), 401, "SESSION_REQUIRED");
        assertThat(get("/machines", newSession).statusCode()).isEqualTo(200);
    }

    @Test
    void concurrentLoginConsumesOneContextAtMostOnce() throws Exception {
        loadAccounts();
        var context = csrf(null);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = executor.submit(() -> login(TECHNICIAN, TECHNICIAN_PASSWORD, context, null).statusCode());
            var second = executor.submit(() -> login(TECHNICIAN, TECHNICIAN_PASSWORD, context, null).statusCode());
            assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(204, 403);
        }
        assertThat(jdbc.sql("SELECT count(*) FROM web_session").query(Integer.class).single()).isEqualTo(1);
    }

    @Test
    void requiresAnActiveSessionForLogoutAndRejectsInvalidCsrfPurpose() throws Exception {
        problem(get("/auth/csrf?purpose=", null), 400, "INVALID_REQUEST");
        problem(get("/auth/csrf?purpose=login", null), 400, "INVALID_REQUEST");
        problem(get("/auth/csrf?purpose=logout&purpose=logout", null), 400, "INVALID_REQUEST");
        for (var cookie : new String[] {null, "PFC_SESSION=malformed", "PFC_SESSION=" + "A".repeat(43)}) {
            problem(get("/auth/csrf?purpose=logout", cookie), 401, "SESSION_REQUIRED");
            problem(logout(cookie, null), 401, "SESSION_REQUIRED");
        }

        loadAccounts();
        var session = authenticated(OWNER, OWNER_PASSWORD);
        var token = logoutCsrf(session);
        NOW.set(START.plus(Duration.ofMinutes(30)));
        problem(get("/auth/csrf?purpose=logout", session), 401, "SESSION_REQUIRED");
        problem(logout(session, token), 401, "SESSION_REQUIRED");
    }

    @Test
    void onlyAcceptsTheCurrentSessionsLogoutTokenAndRevokesThatSession() throws Exception {
        loadAccounts();
        var session = authenticated(OWNER, OWNER_PASSWORD);
        var anotherSession = authenticated(OWNER, OWNER_PASSWORD);
        var preLogin = csrf(null);
        var cookies = session + "; " + preLogin.cookie();
        var anotherToken = logoutCsrf(anotherSession);

        problem(logout(cookies, preLogin.token()), 403, "INVALID_CSRF");
        assertThat(jdbc.sql("SELECT count(*) FROM web_session WHERE logout_csrf_token IS NULL")
                .query(Integer.class).single()).isEqualTo(1);

        var token = logoutCsrf(session);
        assertThat(token).isNotEqualTo(anotherToken).isNotEqualTo(preLogin.token());
        assertThat(logoutCsrf(session)).isEqualTo(token);
        assertThat(ok(get("/auth/csrf", cookies)).get("token").stringValue()).isEqualTo(preLogin.token());
        problem(logout(session, null), 403, "INVALID_CSRF");
        problem(logout(session, "wrong-token"), 403, "INVALID_CSRF");
        problem(logout(cookies, preLogin.token()), 403, "INVALID_CSRF");
        problem(logout(session, anotherToken), 403, "INVALID_CSRF");
        assertThat(get("/machines", session).statusCode()).isEqualTo(200);

        var response = logout(cookies, token);
        assertThat(response.statusCode()).isEqualTo(204);
        assertThat(response.body()).isEmpty();
        assertThat(response.headers().firstValue("Cache-Control")).contains("no-store");
        assertCookie(response, "PFC_SESSION");
        assertThat(response.headers().allValues("Set-Cookie")).singleElement().asString()
                .startsWith("PFC_SESSION=;").contains("Max-Age=0");

        problem(get("/machines", session), 401, "SESSION_REQUIRED");
        problem(get("/auth/csrf?purpose=logout", session), 401, "SESSION_REQUIRED");
        problem(logout(session, token), 401, "SESSION_REQUIRED");
        assertThat(get("/machines", anotherSession).statusCode()).isEqualTo(200);
        assertThat(logoutCsrf(anotherSession)).isEqualTo(anotherToken);
    }

    @Test
    void concurrentLogoutRequestsShareOneTokenAndRevokeTheSessionOnceWithoutMachineReadPermission() throws Exception {
        loadAccounts();
        var session = authenticated(TECHNICIAN, TECHNICIAN_PASSWORD);
        jdbc.sql("DELETE FROM web_role_permission WHERE role_id = 'TECHNICAL_SERVICE'").update();
        problem(get("/machines", session), 403, "ACCESS_DENIED");

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var firstToken = executor.submit(() -> logoutCsrf(session));
            var secondToken = executor.submit(() -> logoutCsrf(session));
            var token = firstToken.get(10, TimeUnit.SECONDS);
            assertThat(secondToken.get(10, TimeUnit.SECONDS)).isEqualTo(token);

            var firstLogout = executor.submit(() -> logout(session, token));
            var secondLogout = executor.submit(() -> logout(session, token));
            assertThat(List.of(firstLogout.get(10, TimeUnit.SECONDS), secondLogout.get(10, TimeUnit.SECONDS)))
                    .satisfiesExactlyInAnyOrder(response -> assertThat(response.statusCode()).isEqualTo(204),
                            response -> problem(response, 401, "SESSION_REQUIRED"));
        }
        assertThat(jdbc.sql("SELECT count(*) FROM web_session").query(Integer.class).single()).isZero();
    }

    @Test
    void enforcesExpiryAndReturnsTheSameCredentialErrorAcrossFailures() throws Exception {
        loadAccounts();
        jdbc.sql("INSERT INTO web_role (role_id, name) VALUES ('OPERATOR', 'Operador')").update();
        jdbc.sql("""
                INSERT INTO web_user_role (user_id, role_id)
                SELECT user_id, 'OPERATOR' FROM web_user WHERE identifier = :id
                """)
                .param("id", UNASSOCIATED).update();
        jdbc.sql("DELETE FROM web_user_role WHERE role_id = 'OWNER' AND user_id = "
                + "(SELECT user_id FROM web_user WHERE identifier = :id)").param("id", UNASSOCIATED).update();
        jdbc.sql("UPDATE web_user SET active = false WHERE identifier = :id").param("id", OWNER).update();

        var first = csrf(null);
        var unknown = login("unknown", TECHNICIAN_PASSWORD, first, null);
        var inactive = login(OWNER, OWNER_PASSWORD, first, null);
        var operator = login(UNASSOCIATED, UNASSOCIATED_PASSWORD, first, null);
        var wrong = login(TECHNICIAN, "wrong", first, null);
        problem(unknown, 401, "INVALID_CREDENTIALS");
        for (var failure : List.of(inactive, operator, wrong)) {
            problem(failure, 401, "INVALID_CREDENTIALS");
            assertThat(failure.body()).isEqualTo(unknown.body());
        }
        NOW.set(START.plus(Duration.ofMinutes(5)));
        problem(login(TECHNICIAN, TECHNICIAN_PASSWORD, first, null), 403, "INVALID_CSRF");

        var session = authenticated(TECHNICIAN, TECHNICIAN_PASSWORD);
        NOW.set(START.plus(Duration.ofMinutes(35)).minusNanos(1));
        assertThat(get("/machines", session).statusCode()).isEqualTo(200);
        NOW.set(START.plus(Duration.ofMinutes(35)));
        problem(get("/machines", session), 401, "SESSION_REQUIRED");
    }

    @Test
    void filtersCatalogByCurrentRolePermissionAndAssociation() throws Exception {
        loadAccounts();
        jdbc.sql("INSERT INTO machine_instance VALUES ('000124', 'embolsadora')").update();

        var absent = get("/machines", null);
        problem(absent, 401, "SESSION_REQUIRED");
        problem(get("/machines/000123", "PFC_SESSION=fake"), 401, "SESSION_REQUIRED");

        var technician = authenticated(TECHNICIAN, TECHNICIAN_PASSWORD);
        var owner = authenticated(OWNER, OWNER_PASSWORD);
        var unassociated = authenticated(UNASSOCIATED, UNASSOCIATED_PASSWORD);
        assertThat(ok(get("/machines", technician)).get("items").size()).isEqualTo(2);
        assertThat(ok(get("/machines", owner)).get("items"))
                .isEqualTo(JSON.readTree("[{\"chassis\":\"000123\",\"lastAuthenticatedContactAt\":null}]"));
        assertThat(ok(get("/machines", unassociated))).isEqualTo(JSON.readTree("{\"items\":[]}"));

        var machine = ok(get("/machines/000123", owner));
        assertThat(machine.get("chassis").stringValue()).isEqualTo("000123");
        assertThat(machine.get("lastAuthenticatedContactAt").isNull()).isTrue();
        assertThat(machine.get("definitions")).isEqualTo(JSON.readTree("""
                [
                  {"definitionId":"engine.rpm","name":"RPM del motor","unit":"rpm"},
                  {"definitionId":"engine.coolant_temperature","name":"Temperatura del refrigerante","unit":"°C"}
                ]
                """));
        var foreign = get("/machines/000124", owner);
        var unknown = get("/machines/123", owner);
        problem(foreign, 403, "ACCESS_DENIED");
        problem(unknown, 403, "ACCESS_DENIED");
        assertThat(foreign.body()).isEqualTo(unknown.body());

        jdbc.sql("""
                UPDATE web_machine_association SET valid_until = TIMESTAMPTZ '2026-01-15 12:01:00Z'
                """).update();
        NOW.set(START.plusSeconds(60).minusNanos(1));
        assertThat(get("/machines/000123", owner).statusCode()).isEqualTo(200);
        NOW.set(START.plusSeconds(60));
        problem(get("/machines/000123", owner), 403, "ACCESS_DENIED");
        assertThat(ok(get("/machines", owner))).isEqualTo(JSON.readTree("{\"items\":[]}"));

        jdbc.sql("DELETE FROM web_role_permission WHERE role_id = 'TECHNICAL_SERVICE'").update();
        problem(get("/machines", technician), 403, "ACCESS_DENIED");
        jdbc.sql("UPDATE web_user SET active = false WHERE identifier = :id").param("id", OWNER).update();
        problem(get("/machines", owner), 401, "SESSION_REQUIRED");
    }

    @Test
    void exposesTheLastStationContactOnlyForAuthorizedMachines() throws Exception {
        loadAccounts();
        jdbc.sql("INSERT INTO machine_instance VALUES ('000124', 'embolsadora')").update();
        var credentials = POSTGRES.bean(StationCredentials.class);
        credentials.provision("000123", "station-a", Set.of(RECORDS_WRITE), "a".repeat(64)
                .getBytes(StandardCharsets.US_ASCII));
        credentials.provision("000123", "station-b", Set.of(RECORDS_WRITE), "b".repeat(64)
                .getBytes(StandardCharsets.US_ASCII));
        credentials.provision("000124", "station-c", Set.of(RECORDS_WRITE), "c".repeat(64)
                .getBytes(StandardCharsets.US_ASCII));
        jdbc.sql("""
                UPDATE station_credential SET last_authenticated_contact_at = CASE producer_id
                    WHEN 'station-a' THEN TIMESTAMPTZ '2026-01-10 19:00:00-03'
                    WHEN 'station-b' THEN TIMESTAMPTZ '2026-01-11 19:00:00-03'
                    WHEN 'station-c' THEN TIMESTAMPTZ '2026-01-12 19:00:00-03'
                END
                """).update();
        credentials.revoke("000123", "station-b");

        var owner = authenticated(OWNER, OWNER_PASSWORD);
        var technician = authenticated(TECHNICIAN, TECHNICIAN_PASSWORD);
        assertThat(ok(get("/machines", owner)).get("items")).isEqualTo(JSON.readTree("""
                [{"chassis":"000123","lastAuthenticatedContactAt":"2026-01-11T22:00:00Z"}]
                """));
        assertThat(ok(get("/machines/000123", owner)).get("lastAuthenticatedContactAt").stringValue())
                .isEqualTo("2026-01-11T22:00:00Z");
        assertThat(ok(get("/machines/000124", technician)).get("lastAuthenticatedContactAt").stringValue())
                .isEqualTo("2026-01-12T22:00:00Z");
        problem(get("/machines/000124", owner), 403, "ACCESS_DENIED");
        problem(get("/machines/000123", null), 401, "SESSION_REQUIRED");
    }

    private void loadAccounts() {
        POSTGRES.bean(DemoWebAccountLoader.class)
                .load(TECHNICIAN_PASSWORD, OWNER_PASSWORD, UNASSOCIATED_PASSWORD);
    }

    private int auditCount() {
        return jdbc.sql("SELECT count(*) FROM audit_record WHERE actor_reference = 'p1-demo-web-accounts'")
                .query(Integer.class).single();
    }

    private static LoginContext csrf(String cookies) throws Exception {
        var response = get("/auth/csrf", cookies);
        var body = ok(response);
        assertCookie(response, "PFC_LOGIN");
        return new LoginContext(cookie(response, "PFC_LOGIN"), body.get("token").stringValue());
    }

    private static String authenticated(String identifier, String password) throws Exception {
        var response = login(identifier, password, csrf(null), null);
        assertThat(response.statusCode()).isEqualTo(204);
        return cookie(response, "PFC_SESSION");
    }

    private static HttpResponse<String> login(String identifier, String password, LoginContext context, String session)
            throws Exception {
        var cookies = session == null ? context.cookie() : context.cookie() + "; " + session;
        var body = JSON.writeValueAsString(Map.of("identifier", identifier, "password", password));
        return post(body, cookies, context.token());
    }

    private static HttpResponse<String> get(String path, String cookie) throws Exception {
        return send("GET", path, cookie, null, null, null);
    }

    private static String logoutCsrf(String session) throws Exception {
        var response = get("/auth/csrf?purpose=logout", session);
        var body = ok(response);
        assertThat(response.headers().allValues("Set-Cookie")).isEmpty();
        return body.get("token").stringValue();
    }

    private static HttpResponse<String> logout(String cookies, String csrf) throws Exception {
        return send("DELETE", "/auth/session", cookies, csrf, null, null);
    }

    private static HttpResponse<String> post(String body, String cookie, String csrf) throws Exception {
        return send("POST", "/auth/session", cookie, csrf, body, "application/json");
    }

    private static HttpResponse<String> send(String method, String path, String cookies, String csrf, String body,
            String contentType) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + POSTGRES.port() + "/api/v1" + path))
                .timeout(Duration.ofSeconds(5));
        if (cookies != null) {
            request.header("Cookie", cookies);
        }
        if (csrf != null) {
            request.header("X-CSRF-TOKEN", csrf);
        }
        if (contentType != null) {
            request.header("Content-Type", contentType);
        }
        request.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body));
        return CLIENT.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String cookie(HttpResponse<String> response, String name) {
        return response.headers().allValues("Set-Cookie").stream().filter(value -> value.startsWith(name + "="))
                .findFirst().orElseThrow().split(";", 2)[0];
    }

    private static void assertCookie(HttpResponse<String> response, String name) {
        assertThat(response.headers().allValues("Set-Cookie")).anyMatch(value -> value.startsWith(name + "=")
                && value.contains("Path=/api/v1") && value.contains("Secure") && value.contains("HttpOnly")
                && value.contains("SameSite=Lax") && !value.contains("Domain="));
    }

    private static void problem(HttpResponse<String> response, int status, String code) {
        assertThat(response.statusCode()).withFailMessage(response.body()).isEqualTo(status);
        assertThat(response.headers().firstValue("Content-Type")).contains("application/problem+json");
        assertThat(response.headers().firstValue("Cache-Control")).contains("no-store");
        assertThat(response.headers().firstValue("Location")).isEmpty();
        var challenge = response.headers().firstValue("WWW-Authenticate");
        if (status == 401) {
            assertThat(challenge).contains("Session realm=\"PFC\"");
        } else {
            assertThat(challenge).isEmpty();
        }
        assertThat(response.body()).contains("\"type\":\"about:blank\"", "\"code\":\"" + code + "\"");
    }

    private static JsonNode ok(HttpResponse<String> response) {
        assertThat(response.statusCode()).withFailMessage(response.body()).isEqualTo(200);
        assertThat(response.headers().firstValue("Cache-Control")).contains("no-store");
        return JSON.readTree(response.body());
    }

    private record LoginContext(String cookie, String token) {
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfiguration {

        @Bean
        @Primary
        Clock testClock() {
            return new Clock() {

                @Override
                public Instant instant() {
                    return NOW.get();
                }

                @Override
                public ZoneId getZone() {
                    return ZoneOffset.UTC;
                }

                @Override
                public Clock withZone(ZoneId zone) {
                    return Clock.fixed(instant(), zone);
                }
            };
        }
    }
}
