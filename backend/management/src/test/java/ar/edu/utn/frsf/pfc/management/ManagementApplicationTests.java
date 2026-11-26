package ar.edu.utn.frsf.pfc.management;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import tools.jackson.databind.json.JsonMapper;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
        "spring.flyway.enabled=false"
})
class ManagementApplicationTests {

    @MockitoBean
    private JdbcClient jdbc;

    @LocalServerPort
    private int port;

    @Test
    void healthEndpointReportsUp() throws Exception {
        var response = get("/actuator/health");

        assertThat(response.statusCode()).isEqualTo(200);
        var health = JsonMapper.builder().build().readTree(response.body());
        assertThat(health.path("status").asText()).isEqualTo("UP");
        assertThat(health.has("details")).isFalse();
        assertThat(health.has("components")).isFalse();
    }

    @Test
    void environmentEndpointIsNotExposed() throws Exception {
        assertThat(get("/actuator/env").statusCode()).isEqualTo(404);
    }

    @Test
    void stationAuthorizationIsDisabledWithoutServiceToken() throws Exception {
        assertStationAuthorizationDisabled(port, "a".repeat(64));
    }

    @Nested
    @TestPropertySource(properties = "pfc.internal.records-token=" + MalformedServiceToken.VALUE)
    class MalformedServiceToken {
        static final String VALUE = "0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF";

        @LocalServerPort
        private int serverPort;

        @Test
        void stationAuthorizationIsDisabled() throws Exception {
            assertStationAuthorizationDisabled(serverPort, VALUE);
        }
    }

    private static void assertStationAuthorizationDisabled(int port, String token) throws Exception {
        var response = send(port, "/internal/v1/station-authorizations/check", HttpRequest.newBuilder()
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{")));

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.headers().firstValue("WWW-Authenticate")).contains("Bearer");
        assertThat(response.headers().firstValue("Cache-Control")).contains("no-store");
        assertThat(response.body()).isEmpty();
    }

    private HttpResponse<String> get(String path) throws Exception {
        return send(port, path, HttpRequest.newBuilder().GET());
    }

    private static HttpResponse<String> send(int port, String path, HttpRequest.Builder builder) throws Exception {
        try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()) {
            var request = builder.uri(URI.create("http://127.0.0.1:" + port + path))
                    .timeout(Duration.ofSeconds(5)).build();
            return client.send(request, HttpResponse.BodyHandlers.ofString());
        }
    }
}
