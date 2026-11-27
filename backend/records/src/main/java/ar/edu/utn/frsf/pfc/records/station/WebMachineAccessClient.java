package ar.edu.utn.frsf.pfc.records.station;

import java.io.IOException;
import java.net.http.HttpResponse.BodySubscribers;

import org.springframework.stereotype.Component;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectReader;
import tools.jackson.databind.json.JsonMapper;

@Component
public final class WebMachineAccessClient {

    private static final String PATH = "/internal/v1/web-machine-access/check";
    private static final int MAX_RESPONSE_BYTES = 1024;

    private final ManagementInternalHttp http;
    private final JsonMapper json;
    private final ObjectReader responseReader;

    WebMachineAccessClient(ManagementInternalHttp http, JsonMapper json) {
        this.http = http;
        this.json = json;
        this.responseReader = json.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS,
                DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY);
    }

    public Decision check(String chassis, String sessionToken) {
        try {
            var request = json.writeValueAsBytes(new Lookup(chassis, sessionToken));
            var response = http.post(PATH, request,
                    info -> BodySubscribers.limiting(BodySubscribers.ofByteArray(), MAX_RESPONSE_BYTES));
            if (response.statusCode() != 200) {
                return Decision.UNAVAILABLE;
            }
            return readDecision(response.body());
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            return Decision.UNAVAILABLE;
        } catch (IOException | JacksonException failure) {
            return Decision.UNAVAILABLE;
        }
    }

    private Decision readDecision(byte[] body) {
        var node = responseReader.readTree(body);
        if (node == null || !node.isObject() || node.size() != 1 || !node.path("decision").isString()) {
            return Decision.UNAVAILABLE;
        }
        return switch (node.get("decision").stringValue()) {
            case "ALLOWED" -> Decision.ALLOWED;
            case "SESSION_REQUIRED" -> Decision.SESSION_REQUIRED;
            case "ACCESS_DENIED" -> Decision.ACCESS_DENIED;
            default -> Decision.UNAVAILABLE;
        };
    }

    private record Lookup(String chassis, String sessionToken) {
        @Override
        public String toString() {
            return "Lookup[redacted]";
        }
    }

    public enum Decision {
        ALLOWED,
        SESSION_REQUIRED,
        ACCESS_DENIED,
        UNAVAILABLE
    }
}
