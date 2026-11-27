package ar.edu.utn.frsf.pfc.management;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

import ar.edu.utn.frsf.pfc.management.catalog.DemoFixtureLoader;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class DataContextIT {

    private static final ManagementPostgres POSTGRES = new ManagementPostgres();
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String OCCURRENCE = "2026-01-15T12:00:00Z";
    private static final String RPM = "engine.rpm";
    private static final String COOLANT = "engine.coolant_temperature";

    @BeforeAll
    static void start() throws Exception {
        POSTGRES.startDatabase();
        POSTGRES.startApplication(Map.of("pfc.internal.records-token", StationAuthorizationTests.RECORDS_TOKEN));
        POSTGRES.bean(DemoFixtureLoader.class).load();
        POSTGRES.jdbc().sql("""
                INSERT INTO record_definition (type_id, definition_id, kind, name)
                VALUES ('embolsadora', 'engine.state', 'STATE', 'Engine state')
                """).update();
    }

    @AfterAll
    static void stop() {
        try {
            HTTP.close();
        } finally {
            POSTGRES.close();
        }
    }

    @Test
    void resolvesBothCatalogDefinitionsForTheTextualChassis() throws Exception {
        assertContext(post(valid("000123", RPM, OCCURRENCE)), RPM, "rpm", "rpm-b");
        assertContext(post(valid("000123", COOLANT, OCCURRENCE)), COOLANT, "°C", "coolant-a");
        assertEmptyStatus(post(valid("123", RPM, OCCURRENCE)), 404);
    }

    @Test
    void reportsUnknownDefinitionsOtherKindsAndMissingBindings() throws Exception {
        assertEmptyStatus(post(valid("000123", "engine.unknown", OCCURRENCE)), 404);
        assertEmptyStatus(post(valid("000123", "engine.state", OCCURRENCE)), 404);
        assertEmptyStatus(post(valid("000123", RPM, "2026-01-15T11:59:59.999999999Z")), 404);
    }

    @Test
    void resolvesCatalogWithoutAssigningASourceToAnInvalidClockMark() throws Exception {
        for (var mark : new String[] {null, OCCURRENCE}) {
            var body = JSON.createObjectNode().put("instanceId", "000123").put("definitionId", RPM)
                    .put("timeValidity", "INVALID").put("occurredAt", mark);
            assertContext(post(body.toString()), RPM, "rpm", null);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "42", "\"2026-01-15T12:00:00+00:00\"", "\"2026-01-15T12:00:00\"", "\"bad-date\""})
    void requiresAnActualUtcMarkForValidTime(String mark) throws Exception {
        assertEmptyStatus(post("""
                {"instanceId":"000123","definitionId":"engine.rpm","timeValidity":"VALID","occurredAt":%s}
                """.formatted(mark)), 400);
    }

    @Test
    void validatesJsonWithoutCoercionOrExtraFields() throws Exception {
        for (var body : new String[] {"", "{", "null", valid("000123", RPM, OCCURRENCE) + "{}",
                """
                {"instanceId":123,"definitionId":"engine.rpm","timeValidity":"INVALID"}
                """,
                """
                {"instanceId":"000123","definitionId":"engine.rpm","timeValidity":"VALID"}
                """,
                """
                {"instanceId":"123","instanceId":"000123","definitionId":"engine.rpm","timeValidity":"INVALID"}
                """,
                """
                {"instanceId":"000123","definitionId":"engine.rpm","timeValidity":"INVALID","receivedAt":"2026-01-15T12:00:00Z"}
                """}) {
            assertEmptyStatus(post(body), 400);
        }
        assertEmptyStatus(post(" ".repeat(4096) + "{}"), 400);
    }

    @Test
    void rejectsMissingOrIncorrectServiceCredentialsBeforeParsing() throws Exception {
        var missing = post("{", null);
        assertEmptyStatus(missing, 401);
        assertThat(missing.headers().firstValue("WWW-Authenticate")).contains("Bearer");
        assertEmptyStatus(post("{", "f".repeat(64)), 401);
    }

    private static String valid(String instanceId, String definitionId, String occurrence) {
        return JSON.createObjectNode().put("instanceId", instanceId).put("definitionId", definitionId)
                .put("timeValidity", "VALID").put("occurredAt", occurrence).toString();
    }

    private static HttpResponse<String> post(String body) throws Exception {
        return post(body, StationAuthorizationTests.RECORDS_TOKEN);
    }

    private static HttpResponse<String> post(String body, String token) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + POSTGRES.port()
                + "/internal/v1/data-context/resolve")).timeout(Duration.ofSeconds(5))
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body));
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        return HTTP.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static void assertContext(HttpResponse<String> response, String definition, String unit, String source) {
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Cache-Control")).contains("no-store");
        assertThat(JSON.readTree(response.body())).isEqualTo(JSON.createObjectNode().put("typeId", "embolsadora")
                .put("definitionId", definition).put("subsystemId", "engine").put("valueType", "NUMBER")
                .put("unit", unit).put("sourceId", source));
    }

    private static void assertEmptyStatus(HttpResponse<String> response, int status) {
        assertThat(response.statusCode()).isEqualTo(status);
        assertThat(response.body()).isEmpty();
        assertThat(response.headers().firstValue("Cache-Control")).contains("no-store");
    }
}
