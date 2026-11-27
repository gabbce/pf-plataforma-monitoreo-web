package ar.edu.utn.frsf.pfc.records.station;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
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

import static ar.edu.utn.frsf.pfc.records.station.WebMachineAccessClient.Decision.ALLOWED;
import static ar.edu.utn.frsf.pfc.records.station.WebMachineAccessClient.Decision.UNAVAILABLE;
import static org.assertj.core.api.Assertions.assertThat;

class WebMachineAccessClientTests {

    private static final String PATH = "/internal/v1/web-machine-access/check";
    private static final String SERVICE_TOKEN = "3".repeat(64);
    private static final String SESSION_TOKEN = "B".repeat(43);
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private HttpServer server;
    private ManagementInternalHttp transport;
    private WebMachineAccessClient client;

    @BeforeEach
    void startManagement() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
        var connection = new ManagementInternalHttp.Properties(
                "http://127.0.0.1:" + server.getAddress().getPort(), SERVICE_TOKEN);
        transport = new ManagementInternalHttp(connection);
        client = new WebMachineAccessClient(transport, JSON);
    }

    @AfterEach
    void close() {
        server.stop(0);
        transport.close();
    }

    @Test
    void usesTheServiceBearerAndRelaysOnlyTheOpaqueSessionAndChassis() {
        var authorization = new AtomicReference<String>();
        var request = new AtomicReference<JsonNode>();
        var method = new AtomicReference<String>();
        var cookie = new AtomicReference<String>();
        server.createContext(PATH, exchange -> {
            try (exchange) {
                method.set(exchange.getRequestMethod());
                authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
                cookie.set(exchange.getRequestHeaders().getFirst("Cookie"));
                request.set(JSON.readTree(exchange.getRequestBody()));
                var response = "{\"decision\":\"ALLOWED\"}".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, response.length);
                exchange.getResponseBody().write(response);
            }
        });

        assertThat(client.check("000123", SESSION_TOKEN)).isEqualTo(ALLOWED);
        assertThat(method.get()).isEqualTo("POST");
        assertThat(authorization.get()).isEqualTo("Bearer " + SERVICE_TOKEN);
        assertThat(cookie.get()).isNull();
        assertThat(request.get()).isEqualTo(JSON.createObjectNode()
                .put("chassis", "000123").put("sessionToken", SESSION_TOKEN));
    }

    @ParameterizedTest
    @CsvSource({"200,ALLOWED,ALLOWED", "200,SESSION_REQUIRED,SESSION_REQUIRED",
            "200,ACCESS_DENIED,ACCESS_DENIED", "401,SESSION_REQUIRED,UNAVAILABLE",
            "403,ACCESS_DENIED,UNAVAILABLE", "503,ALLOWED,UNAVAILABLE"})
    void onlyTrustsDecisionsFromSuccessfulInternalResponses(int status, String decision,
            WebMachineAccessClient.Decision expected) {
        respond(status, "{\"decision\":\"" + decision + "\"}");
        assertThat(client.check("000123", SESSION_TOKEN)).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"[]", "{\"decision\":1}", "{\"decision\":\"ALIEN\"}",
            "{\"decision\":\"ALLOWED\",\"decision\":\"ACCESS_DENIED\"}", "{\"decision\":\"ALLOWED\"}{}",
            "{\"decision\":\"ALLOWED\",\"other\":true}"})
    void rejectsInvalidAuthorizationResponses(String body) {
        respond(200, body);
        assertThat(client.check("000123", SESSION_TOKEN)).isEqualTo(UNAVAILABLE);
    }

    private void respond(int status, String body) {
        var response = body.getBytes(StandardCharsets.UTF_8);
        server.createContext(PATH, exchange -> {
            try (exchange) {
                exchange.getRequestBody().readAllBytes();
                exchange.sendResponseHeaders(status, response.length);
                exchange.getResponseBody().write(response);
            }
        });
    }
}
