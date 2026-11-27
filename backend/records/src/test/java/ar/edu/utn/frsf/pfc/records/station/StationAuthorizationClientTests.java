package ar.edu.utn.frsf.pfc.records.station;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import ar.edu.utn.frsf.pfc.records.RecordsApplication;
import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static ar.edu.utn.frsf.pfc.records.station.StationAuthorizationClient.Decision.ALLOWED;
import static ar.edu.utn.frsf.pfc.records.station.StationAuthorizationClient.Decision.DENIED;
import static ar.edu.utn.frsf.pfc.records.station.StationAuthorizationClient.Decision.UNAVAILABLE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

@ExtendWith(OutputCaptureExtension.class)
class StationAuthorizationClientTests {

    private static final String CHECK_PATH = "/internal/v1/station-authorizations/check";
    private static final String CONTACT_PATH = CHECK_PATH + "-and-record-contact";
    private static final String SERVICE_TOKEN = "1".repeat(64);
    private static final String STATION_TOKEN = "2".repeat(64);
    private static final String INSTANCE_ID = "000041";
    private static final String PRODUCER_ID = "station-1";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private HttpServer management;
    private ManagementInternalHttp http;
    private StationAuthorizationClient client;

    @BeforeEach
    void startManagement() throws Exception {
        management = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        management.start();
    }

    @AfterEach
    void closeResources() {
        management.stop(0);
        if (http != null) {
            http.close();
        }
    }

    @Test
    void sendsTheRecordsCheckUsingTheMountedServiceSecret(@TempDir Path secrets) throws Exception {
        var method = new AtomicReference<String>();
        var path = new AtomicReference<String>();
        var authorization = new AtomicReference<String>();
        var contentType = new AtomicReference<String>();
        var payload = new AtomicReference<JsonNode>();
        management.createContext(CHECK_PATH, exchange -> {
            try (exchange) {
                method.set(exchange.getRequestMethod());
                path.set(exchange.getRequestURI().toString());
                authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
                contentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
                payload.set(JSON.readTree(exchange.getRequestBody()));
                exchange.sendResponseHeaders(204, -1);
            }
        });
        Files.writeString(secrets.resolve("pfc.internal.records-token"), SERVICE_TOKEN + "\n");

        try (var context = new SpringApplicationBuilder(RecordsApplication.class)
                .web(WebApplicationType.NONE)
                .run("--spring.config.import=configtree:" + secrets.toString().replace('\\', '/') + "/",
                        "--pfc.internal.management-url=" + managementUrl() + "/")) {
            var configured = context.getBean(StationAuthorizationClient.class);

            assertThat(configured.check(INSTANCE_ID, PRODUCER_ID, STATION_TOKEN)).isEqualTo(ALLOWED);
        }

        assertThat(method.get()).isEqualTo("POST");
        assertThat(path.get()).isEqualTo(CHECK_PATH);
        assertThat(authorization.get()).isEqualTo("Bearer " + SERVICE_TOKEN);
        assertThat(contentType.get()).isEqualTo("application/json");
        assertThat(payload.get()).isEqualTo(JSON.createObjectNode()
                .put("instanceId", INSTANCE_ID)
                .put("producerId", PRODUCER_ID)
                .put("operation", "RECORDS_WRITE")
                .put("stationToken", STATION_TOKEN));
    }

    @Test
    void usesTheDedicatedContactOperationWithoutFallingBackToThePureCheck(CapturedOutput output) {
        var request = new AtomicReference<JsonNode>();
        var bearer = new AtomicReference<String>();
        var status = new AtomicInteger(204);
        var pureChecks = new AtomicInteger();
        management.createContext(CONTACT_PATH, exchange -> {
            try (exchange) {
                request.set(JSON.readTree(exchange.getRequestBody()));
                bearer.set(exchange.getRequestHeaders().getFirst("Authorization"));
                exchange.sendResponseHeaders(status.get(), -1);
            }
        });
        management.createContext(CHECK_PATH, exchange -> {
            try (exchange) {
                pureChecks.incrementAndGet();
                exchange.sendResponseHeaders(204, -1);
            }
        });
        client = newClient(managementUrl(), SERVICE_TOKEN);

        assertThat(client.checkAndRecordContact(INSTANCE_ID, PRODUCER_ID, STATION_TOKEN)).isEqualTo(ALLOWED);
        assertThat(bearer.get()).isEqualTo("Bearer " + SERVICE_TOKEN);
        assertThat(request.get()).isEqualTo(JSON.createObjectNode()
                .put("instanceId", INSTANCE_ID).put("producerId", PRODUCER_ID)
                .put("operation", "RECORDS_WRITE").put("stationToken", STATION_TOKEN));
        status.set(403);
        assertThat(client.checkAndRecordContact(INSTANCE_ID, PRODUCER_ID, STATION_TOKEN)).isEqualTo(DENIED);
        status.set(503);
        assertThat(client.checkAndRecordContact(INSTANCE_ID, PRODUCER_ID, STATION_TOKEN)).isEqualTo(UNAVAILABLE);
        assertThat(pureChecks).hasValue(0);
        assertThat(output.getAll()).doesNotContain(SERVICE_TOKEN, STATION_TOKEN);
    }

    @Test
    void distinguishesAnOversizedContactScopeWithoutSendingItToManagement() {
        var calls = new AtomicInteger();
        management.createContext(CONTACT_PATH, exchange -> {
            try (exchange) {
                calls.incrementAndGet();
                exchange.sendResponseHeaders(400, -1);
            }
        });
        client = newClient(managementUrl(), SERVICE_TOKEN);

        assertThat(client.checkAndRecordContact("á".repeat(2048), PRODUCER_ID, STATION_TOKEN))
                .isEqualTo(StationAuthorizationClient.Decision.INVALID_SCOPE);
        assertThat(calls).hasValue(0);
    }

    @Test
    void checksAgainWhenTheStationAuthorizationIsRevoked() {
        var status = new AtomicInteger(204);
        var calls = new AtomicInteger();
        management.createContext(CHECK_PATH, exchange -> {
            try (exchange) {
                exchange.getRequestBody().readAllBytes();
                calls.incrementAndGet();
                exchange.sendResponseHeaders(status.get(), -1);
            }
        });
        client = newClient(managementUrl(), SERVICE_TOKEN);

        assertThat(check()).isEqualTo(ALLOWED);
        status.set(403);
        assertThat(check()).isEqualTo(DENIED);
        assertThat(calls.get()).isEqualTo(2);
    }

    @ParameterizedTest
    @ValueSource(ints = {200, 400, 401, 404, 429, 500, 503})
    void treatsOtherStatusesAsUnavailableWithoutExposingTheResponse(int status, CapturedOutput output) {
        var response = "sensitive response " + SERVICE_TOKEN + " " + STATION_TOKEN;
        var responseBytes = response.getBytes(StandardCharsets.UTF_8);
        var calls = new AtomicInteger();
        management.createContext(CHECK_PATH, exchange -> {
            try (exchange) {
                exchange.getRequestBody().readAllBytes();
                calls.incrementAndGet();
                exchange.sendResponseHeaders(status, responseBytes.length);
                exchange.getResponseBody().write(responseBytes);
            }
        });
        client = newClient(managementUrl(), SERVICE_TOKEN);

        assertThat(check()).isEqualTo(UNAVAILABLE);
        assertThat(calls.get()).isEqualTo(1);
        assertThat(output.getAll()).doesNotContain("sensitive response", SERVICE_TOKEN, STATION_TOKEN);
    }

    @Test
    void doesNotForwardCredentialsToARedirect() {
        var redirected = new AtomicInteger();
        management.createContext(CHECK_PATH, exchange -> {
            try (exchange) {
                exchange.getRequestBody().readAllBytes();
                exchange.getResponseHeaders().set("Location", managementUrl() + "/redirect");
                exchange.sendResponseHeaders(307, -1);
            }
        });
        management.createContext("/redirect", exchange -> {
            try (exchange) {
                redirected.incrementAndGet();
                exchange.sendResponseHeaders(204, -1);
            }
        });
        client = newClient(managementUrl(), SERVICE_TOKEN);

        assertThat(check()).isEqualTo(UNAVAILABLE);
        assertThat(redirected.get()).isZero();
    }

    @Test
    void timesOutWhenManagementDoesNotSendHeaders() {
        var calls = new AtomicInteger();
        var release = new CountDownLatch(1);
        management.createContext(CHECK_PATH, exchange -> {
            try (exchange) {
                exchange.getRequestBody().readAllBytes();
                calls.incrementAndGet();
                release.await();
            } catch (InterruptedException failure) {
                Thread.currentThread().interrupt();
            }
        });
        client = newClient(managementUrl(), SERVICE_TOKEN);

        try {
            assertTimeoutPreemptively(Duration.ofSeconds(5), () -> assertThat(check()).isEqualTo(UNAVAILABLE));
            assertThat(calls.get()).isEqualTo(1);
        } finally {
            release.countDown();
        }
    }

    @Test
    void doesNotWaitForAnErrorResponseBody() {
        var release = new CountDownLatch(1);
        management.createContext(CHECK_PATH, exchange -> {
            try (exchange) {
                exchange.getRequestBody().readAllBytes();
                exchange.sendResponseHeaders(503, 0);
                release.await();
            } catch (InterruptedException failure) {
                Thread.currentThread().interrupt();
            }
        });
        client = newClient(managementUrl(), SERVICE_TOKEN);

        try {
            assertTimeoutPreemptively(Duration.ofSeconds(5), () -> assertThat(check()).isEqualTo(UNAVAILABLE));
        } finally {
            release.countDown();
        }
    }

    @Test
    void treatsConnectionFailureAsUnavailable() {
        client = newClient(managementUrl(), SERVICE_TOKEN);
        management.stop(0);

        assertThat(check()).isEqualTo(UNAVAILABLE);
    }

    @Test
    void preservesInterruption() {
        client = newClient(managementUrl(), SERVICE_TOKEN);

        Thread.currentThread().interrupt();
        try {
            assertThat(check()).isEqualTo(UNAVAILABLE);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "http://", "http://[invalid", "ftp://127.0.0.1", "http://127.0.0.1:99999"})
    void treatsMissingOrMalformedManagementUrlsAsUnavailable(String url) {
        client = newClient(url, SERVICE_TOKEN);

        assertThat(check()).isEqualTo(UNAVAILABLE);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"1234", "secret with whitespace", "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"})
    void makesNoRequestWithAMissingOrMalformedServiceCredential(String token) {
        var calls = new AtomicInteger();
        management.createContext(CHECK_PATH, exchange -> {
            try (exchange) {
                calls.incrementAndGet();
                exchange.sendResponseHeaders(204, -1);
            }
        });
        client = newClient(managementUrl(), token);

        assertThat(check()).isEqualTo(UNAVAILABLE);
        assertThat(calls.get()).isZero();
    }

    private StationAuthorizationClient.Decision check() {
        return client.check(INSTANCE_ID, PRODUCER_ID, STATION_TOKEN);
    }

    private StationAuthorizationClient newClient(String url, String token) {
        http = new ManagementInternalHttp(new ManagementInternalHttp.Properties(url, token));
        return new StationAuthorizationClient(http, JSON);
    }

    private String managementUrl() {
        return "http://127.0.0.1:" + management.getAddress().getPort();
    }
}
