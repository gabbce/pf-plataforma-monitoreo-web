package ar.edu.utn.frsf.pfc.records.station;

import java.io.IOException;
import java.net.http.HttpResponse;

import org.springframework.stereotype.Component;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Component
public final class StationAuthorizationClient {

    private static final String CHECK_PATH = "/internal/v1/station-authorizations/check";
    private static final String CONTACT_PATH = "/internal/v1/station-authorizations/check-and-record-contact";
    private static final String OPERATION = "RECORDS_WRITE";
    // Size accepted by the private StationAuthorizationController in Management.
    private static final int MAX_CONTACT_BODY_BYTES = 4096;

    private final JsonMapper json;
    private final ManagementInternalHttp http;

    StationAuthorizationClient(ManagementInternalHttp http, JsonMapper json) {
        this.http = http;
        this.json = json;
    }

    public Decision check(String instanceId, String producerId, String stationToken) {
        return check(CHECK_PATH, instanceId, producerId, stationToken);
    }

    public Decision checkAndRecordContact(String instanceId, String producerId, String stationToken) {
        return check(CONTACT_PATH, instanceId, producerId, stationToken);
    }

    private Decision check(String path, String instanceId, String producerId, String stationToken) {
        try {
            var payload = new CheckRequest(instanceId, producerId, OPERATION, stationToken);
            var bodyBytes = json.writeValueAsBytes(payload);
            if (path.equals(CONTACT_PATH) && bodyBytes.length > MAX_CONTACT_BODY_BYTES) {
                return Decision.INVALID_SCOPE;
            }
            var response = http.post(path, bodyBytes, HttpResponse.BodyHandlers.ofInputStream());
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

    public enum Decision {
        ALLOWED,
        DENIED,
        UNAVAILABLE,
        INVALID_SCOPE
    }

    private record CheckRequest(String instanceId, String producerId, String operation, String stationToken) {
        @Override
        public String toString() {
            return "CheckRequest[redacted]";
        }
    }
}
