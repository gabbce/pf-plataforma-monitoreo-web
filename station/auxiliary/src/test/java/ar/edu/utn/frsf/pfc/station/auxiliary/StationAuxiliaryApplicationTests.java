package ar.edu.utn.frsf.pfc.station.auxiliary;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import tools.jackson.databind.json.JsonMapper;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class StationAuxiliaryApplicationTests {

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

    private HttpResponse<String> get(String path) throws Exception {
        try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()) {
            var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                    .timeout(Duration.ofSeconds(5)).GET().build();
            return client.send(request, HttpResponse.BodyHandlers.ofString());
        }
    }
}
