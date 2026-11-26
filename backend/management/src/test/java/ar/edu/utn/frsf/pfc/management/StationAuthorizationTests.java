package ar.edu.utn.frsf.pfc.management;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Locale;

import ar.edu.utn.frsf.pfc.management.station.StationCredentials;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static ar.edu.utn.frsf.pfc.management.station.StationOperation.RECORDS_WRITE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
        "spring.flyway.enabled=false",
        "pfc.internal.records-token=" + StationAuthorizationTests.RECORDS_TOKEN
})
@ExtendWith(OutputCaptureExtension.class)
class StationAuthorizationTests {

    static final String RECORDS_TOKEN = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    private static final String STATION_TOKEN = "fedcba9876543210".repeat(4);
    private static final String BEARER = "Bearer " + RECORDS_TOKEN;
    private static final String JSON_TYPE = "application/json";
    private static final String VALID_REQUEST = """
            {"instanceId":"000123","producerId":"station-a","operation":"RECORDS_WRITE","stationToken":"%s"}
            """.formatted(STATION_TOKEN);

    @MockitoBean
    private JdbcClient jdbc;

    @MockitoBean
    private StationCredentials credentials;

    @LocalServerPort
    private int port;

    @Test
    void authenticatesBeforeConsideringTheMediaTypeOrBody() throws Exception {
        assertResponse(post("{", JSON_TYPE), 401);
        assertResponse(post("{", "multipart/form-data", "Basic " + RECORDS_TOKEN), 401);
        assertResponse(post("{", JSON_TYPE, "Bearer " + STATION_TOKEN), 401);
        assertResponse(post("{", JSON_TYPE, "Bearer " + RECORDS_TOKEN.toUpperCase(Locale.ROOT)), 401);
        assertResponse(post("{", JSON_TYPE, "Bearer  " + RECORDS_TOKEN), 401);
        assertResponse(post("{", JSON_TYPE, BEARER, BEARER), 401);
        verifyNoInteractions(credentials);
    }

    @Test
    void rejectsInvalidJsonAndUnknownOperationsWithoutQueryingTheDatabase(CapturedOutput output) throws Exception {
        for (var body : List.of("", "{", "{}", "null", VALID_REQUEST + "{}", VALID_REQUEST.replace("\"000123\"", "123"),
                VALID_REQUEST.replace("\"station-a\"", "null"), VALID_REQUEST.replace("RECORDS_WRITE", STATION_TOKEN),
                VALID_REQUEST.replace("\"producerId\"", "\"instanceId\""),
                VALID_REQUEST.replace("\"instanceId\"", "\"unexpected\":0,\"instanceId\""))) {
            assertResponse(post(body, JSON_TYPE, BEARER), 400);
        }
        assertResponse(post(VALID_REQUEST, "multipart/form-data", BEARER), 400);
        assertResponse(post(VALID_REQUEST.replace("RECORDS_WRITE", "CONFIGURATION_RESULTS_WRITE"), JSON_TYPE, BEARER), 403);
        verifyNoInteractions(credentials);
        assertThat(output.getAll()).doesNotContain(RECORDS_TOKEN, STATION_TOKEN);
    }

    @Test
    void rejectsAnOversizedBodyEvenWithChunkedTransfer() throws Exception {
        var oversized = " ".repeat(4096) + VALID_REQUEST;
        assertResponse(post(oversized, JSON_TYPE, BEARER), 400);

        var chunked = HttpRequest.BodyPublishers.ofInputStream(
                () -> new ByteArrayInputStream(oversized.getBytes(StandardCharsets.UTF_8)));
        assertThat(chunked.contentLength()).isEqualTo(-1);
        assertResponse(post(chunked, JSON_TYPE, BEARER), 400);
        verifyNoInteractions(credentials);
    }

    @Test
    void rejectsDependencyFailureWithoutExposingEitherCredential(CapturedOutput output) throws Exception {
        when(credentials.authorizes("000123", "station-a", RECORDS_WRITE, STATION_TOKEN.getBytes(StandardCharsets.US_ASCII)))
                .thenThrow(new IllegalStateException(RECORDS_TOKEN + STATION_TOKEN));

        assertResponse(post(VALID_REQUEST, JSON_TYPE, BEARER), 503);
        assertThat(output.getAll()).doesNotContain(RECORDS_TOKEN, STATION_TOKEN);
    }

    private HttpResponse<String> post(String body, String contentType, String... authorizations) throws Exception {
        return post(HttpRequest.BodyPublishers.ofString(body), contentType, authorizations);
    }

    private HttpResponse<String> post(HttpRequest.BodyPublisher body, String contentType, String... authorizations)
            throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port
                + "/internal/v1/station-authorizations/check")).timeout(Duration.ofSeconds(5))
                .header("Content-Type", contentType).POST(body);
        for (var authorization : authorizations) {
            request.header("Authorization", authorization);
        }
        try (var client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(5)).build()) {
            return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
        }
    }

    private static void assertResponse(HttpResponse<String> response, int status) {
        assertThat(response.statusCode()).isEqualTo(status);
        assertThat(response.headers().firstValue("Cache-Control")).contains("no-store");
        if (status == 401) {
            assertThat(response.headers().firstValue("WWW-Authenticate")).contains("Bearer");
        } else {
            assertThat(response.headers().firstValue("WWW-Authenticate")).isEmpty();
        }
        assertThat(response.body()).isEmpty();
    }
}
