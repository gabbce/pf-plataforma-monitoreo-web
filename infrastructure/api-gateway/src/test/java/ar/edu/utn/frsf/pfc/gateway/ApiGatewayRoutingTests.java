package ar.edu.utn.frsf.pfc.gateway;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApiGatewayRoutingTests {

    private static HttpServer management;
    private static HttpServer records;

    @LocalServerPort
    private int port;

    @DynamicPropertySource
    static void upstreams(DynamicPropertyRegistry registry) throws IOException {
        management = startUpstream("management");
        records = startUpstream("records");
        registry.add("platform.management-url", () -> address(management));
        registry.add("platform.records-url", () -> address(records));
    }

    @AfterAll
    static void stopUpstreams() {
        if (management != null) {
            management.stop(0);
        }
        if (records != null) {
            records.stop(0);
        }
    }

    @Test
    void routesTheThreeIntegrationOperationsToTheirService() throws Exception {
        assertRouted("POST", "/integration/v1/records", "records");
        assertRouted("GET", "/integration/v1/configuration?instanceId=001", "management");
        assertRouted("POST", "/integration/v1/configuration-results", "management");
    }

    @Test
    void rejectsUnconfiguredMethodsAndPaths() throws Exception {
        assertThat(send("GET", "/integration/v1/records").statusCode()).isEqualTo(404);
        assertThat(send("POST", "/integration/v1/configuration").statusCode()).isEqualTo(404);
        assertThat(send("POST", "/integration/v1/configuration-results/extra").statusCode()).isEqualTo(404);
        assertThat(send("POST", "/integration/v1/records/").statusCode()).isEqualTo(404);
        assertThat(send("GET", "/api/unknown").statusCode()).isEqualTo(404);
    }

    private void assertRouted(String method, String path, String service) throws Exception {
        var response = send(method, path);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo(service + " " + method + " " + path);
    }

    private HttpResponse<String> send(String method, String path) throws Exception {
        try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()) {
            var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                    .timeout(Duration.ofSeconds(5))
                    .method(method, HttpRequest.BodyPublishers.noBody())
                    .build();
            return client.send(request, HttpResponse.BodyHandlers.ofString());
        }
    }

    private static HttpServer startUpstream(String service) throws IOException {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            try (exchange) {
                var payload = (service + " " + exchange.getRequestMethod() + " " + exchange.getRequestURI())
                        .getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, payload.length);
                exchange.getResponseBody().write(payload);
            }
        });
        server.start();
        return server;
    }

    private static String address(HttpServer server) {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }
}
