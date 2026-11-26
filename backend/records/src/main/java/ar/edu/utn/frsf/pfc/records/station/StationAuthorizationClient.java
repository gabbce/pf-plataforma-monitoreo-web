package ar.edu.utn.frsf.pfc.records.station;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.regex.Pattern;

import jakarta.annotation.PreDestroy;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Component
@EnableConfigurationProperties(StationAuthorizationClient.InternalProperties.class)
public final class StationAuthorizationClient {

    private static final String CHECK_PATH = "/internal/v1/station-authorizations/check";
    private static final String OPERATION = "RECORDS_WRITE";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(2);
    private static final Pattern SERVICE_TOKEN = Pattern.compile("[0-9a-f]{64}");

    private final JsonMapper json;
    private final HttpClient transport;
    private final URI endpoint;
    private final String serviceToken;

    StationAuthorizationClient(InternalProperties properties, JsonMapper json) {
        this.json = json;
        this.endpoint = checkEndpoint(properties.managementUrl());
        var configuredToken = properties.recordsToken();
        this.serviceToken = configuredToken != null && SERVICE_TOKEN.matcher(configuredToken).matches()
                ? configuredToken : null;
        this.transport = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NEVER)
                .proxy(HttpClient.Builder.NO_PROXY)
                .build();
    }

    public Decision check(String instanceId, String producerId, String stationToken) {
        if (endpoint == null || serviceToken == null) {
            return Decision.UNAVAILABLE;
        }

        try {
            var payload = new CheckRequest(instanceId, producerId, OPERATION, stationToken);
            var request = HttpRequest.newBuilder(endpoint)
                    .timeout(REQUEST_TIMEOUT)
                    .header("Authorization", "Bearer " + serviceToken)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(json.writeValueAsBytes(payload)))
                    .build();
            var response = transport.send(request, HttpResponse.BodyHandlers.ofInputStream());
            // Closing the stream avoids waiting for a response body that the contract does not use.
            try (var body = response.body()) {
                return switch (response.statusCode()) {
                    case 204 -> Decision.ALLOWED;
                    case 403 -> Decision.DENIED;
                    default -> Decision.UNAVAILABLE;
                };
            }
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            return Decision.UNAVAILABLE;
        } catch (IOException | JacksonException failure) {
            return Decision.UNAVAILABLE;
        }
    }

    @PreDestroy
    void close() {
        transport.close();
    }

    private static URI checkEndpoint(String baseUrl) {
        if (baseUrl == null) {
            return null;
        }
        try {
            var base = new URI(baseUrl);
            boolean http = "http".equalsIgnoreCase(base.getScheme()) || "https".equalsIgnoreCase(base.getScheme());
            int port = base.getPort();
            if (!http || base.getHost() == null || base.getRawAuthority().endsWith(":")
                    || (port != -1 && (port < 1 || port > 65535)) || base.getRawUserInfo() != null
                    || base.getRawQuery() != null || base.getRawFragment() != null
                    || (!base.getRawPath().isEmpty() && !base.getRawPath().equals("/"))) {
                return null;
            }
            return base.resolve(CHECK_PATH);
        } catch (URISyntaxException failure) {
            return null;
        }
    }

    public enum Decision {
        ALLOWED,
        DENIED,
        UNAVAILABLE
    }

    @ConfigurationProperties("pfc.internal")
    record InternalProperties(String managementUrl, String recordsToken) {
        @Override
        public String toString() {
            return "InternalProperties[redacted]";
        }
    }

    private record CheckRequest(String instanceId, String producerId, String operation, String stationToken) {
        @Override
        public String toString() {
            return "CheckRequest[redacted]";
        }
    }
}
