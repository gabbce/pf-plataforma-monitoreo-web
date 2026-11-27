package ar.edu.utn.frsf.pfc.records.station;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static ar.edu.utn.frsf.pfc.records.station.DataContextClient.Status.RESOLVED;
import static ar.edu.utn.frsf.pfc.records.station.DataContextClient.Status.UNAVAILABLE;
import static ar.edu.utn.frsf.pfc.records.station.DataContextClient.TimeValidity.INVALID;
import static ar.edu.utn.frsf.pfc.records.station.DataContextClient.TimeValidity.VALID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

class DataContextClientTests {

    private static final String PATH = "/internal/v1/data-context/resolve";
    private static final String TOKEN = "1".repeat(64);
    private static final String DEFINITION = "engine.rpm";
    private static final Instant OCCURRENCE = Instant.parse("2026-01-15T12:05:00Z");
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String CONTEXT = """
            {"typeId":"embolsadora","definitionId":"engine.rpm","subsystemId":"engine",
             "valueType":"NUMBER","unit":"rpm","sourceId":"rpm-b"}
            """;

    private HttpServer management;
    private ManagementInternalHttp http;
    private DataContextClient client;

    @BeforeEach
    void startManagement() throws Exception {
        management = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        management.start();
        http = new ManagementInternalHttp(new ManagementInternalHttp.Properties(
                "http://127.0.0.1:" + management.getAddress().getPort(), TOKEN));
        client = new DataContextClient(http, JSON);
    }

    @AfterEach
    void closeResources() {
        management.stop(0);
        http.close();
    }

    @Test
    void sendsTheLookupThroughTheInternalConnectionAndDecodesTheContext() {
        var method = new AtomicReference<String>();
        var authorization = new AtomicReference<String>();
        var request = new AtomicReference<JsonNode>();
        management.createContext(PATH, exchange -> {
            try (exchange) {
                method.set(exchange.getRequestMethod());
                authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
                request.set(JSON.readTree(exchange.getRequestBody()));
                var bytes = CONTEXT.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
            }
        });

        assertThat(resolve()).isEqualTo(new DataContextClient.Result(RESOLVED,
                new DataContextClient.Context("embolsadora", DEFINITION, "engine", "NUMBER", "rpm", "rpm-b")));
        assertThat(method.get()).isEqualTo("POST");
        assertThat(authorization.get()).isEqualTo("Bearer " + TOKEN);
        assertThat(request.get()).isEqualTo(JSON.createObjectNode()
                .put("instanceId", "000123").put("definitionId", DEFINITION).put("timeValidity", "VALID")
                .put("occurredAt", OCCURRENCE.toString()));
    }

    @Test
    void acceptsNullSourceAndSubsystemForAnInvalidOccurrence() {
        respond(200, CONTEXT.replace("\"rpm-b\"", "null").replace("\"engine\"", "null"));

        assertThat(client.resolve("000123", DEFINITION, INVALID, null))
                .isEqualTo(new DataContextClient.Result(RESOLVED,
                        new DataContextClient.Context("embolsadora", DEFINITION, null, "NUMBER", "rpm", null)));
        assertThat(resolve().status()).isEqualTo(UNAVAILABLE);
    }

    @Test
    void rejectsAssigningASourceToAnInvalidOccurrence() {
        respond(200, CONTEXT);

        assertThat(client.resolve("000123", DEFINITION, INVALID, OCCURRENCE).status()).isEqualTo(UNAVAILABLE);
    }

    @ParameterizedTest
    @CsvSource({"400,INVALID_REQUEST", "404,NOT_FOUND", "401,UNAVAILABLE", "503,UNAVAILABLE"})
    void distinguishesRejectionFromUnavailableManagement(int status, DataContextClient.Status expected) {
        respond(status, "");

        assertThat(resolve()).isEqualTo(new DataContextClient.Result(expected, null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "null", "{}", "{", "[]"})
    void rejectsMalformedSuccessBodies(String body) {
        respond(200, body);

        assertThat(resolve().status()).isEqualTo(UNAVAILABLE);
    }

    @Test
    void rejectsAContextForADifferentDefinition() {
        respond(200, CONTEXT.replace(DEFINITION, "engine.coolant_temperature"));

        assertThat(resolve().status()).isEqualTo(UNAVAILABLE);
    }

    @Test
    void rejectsAnOversizedResponse() {
        respond(200, CONTEXT + " ".repeat(8192));

        assertThat(resolve().status()).isEqualTo(UNAVAILABLE);
    }

    @Test
    void timesOutWhenManagementStallsAfterSendingHeaders() {
        var release = new CountDownLatch(1);
        management.createContext(PATH, exchange -> {
            try (exchange) {
                exchange.getRequestBody().readAllBytes();
                exchange.sendResponseHeaders(200, 0);
                release.await();
            } catch (InterruptedException failure) {
                Thread.currentThread().interrupt();
            }
        });
        try {
            assertTimeoutPreemptively(Duration.ofSeconds(5), () -> assertThat(resolve().status()).isEqualTo(UNAVAILABLE));
        } finally {
            release.countDown();
        }
    }

    private DataContextClient.Result resolve() {
        return client.resolve("000123", DEFINITION, VALID, OCCURRENCE);
    }

    private void respond(int status, String body) {
        management.createContext(PATH, exchange -> {
            try (exchange) {
                exchange.getRequestBody().readAllBytes();
                var bytes = body.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
                exchange.getResponseBody().write(bytes);
            }
        });
    }
}
