package ar.edu.utn.frsf.pfc.records.station;

import java.io.IOException;
import java.net.http.HttpResponse.BodySubscribers;
import java.time.Instant;

import org.springframework.stereotype.Component;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectReader;
import tools.jackson.databind.json.JsonMapper;

@Component
public final class DataContextClient {

    private static final String RESOLVE_PATH = "/internal/v1/data-context/resolve";
    private static final int MAX_RESPONSE_BYTES = 8192;

    private final ManagementInternalHttp http;
    private final JsonMapper json;
    private final ObjectReader responseReader;

    DataContextClient(ManagementInternalHttp http, JsonMapper json) {
        this.http = http;
        this.json = json;
        this.responseReader = json.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS,
                DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY);
    }

    public Result resolve(String instanceId, String definitionId, TimeValidity timeValidity, Instant occurredAt) {
        try {
            var lookup = new Lookup(instanceId, definitionId, timeValidity,
                    occurredAt == null ? null : occurredAt.toString());
            var response = http.post(RESOLVE_PATH, json.writeValueAsBytes(lookup),
                    info -> BodySubscribers.limiting(BodySubscribers.ofByteArray(), MAX_RESPONSE_BYTES));
            return switch (response.statusCode()) {
                case 200 -> readContext(response.body(), definitionId, timeValidity);
                case 400 -> new Result(Status.INVALID_REQUEST, null);
                case 404 -> new Result(Status.NOT_FOUND, null);
                default -> new Result(Status.UNAVAILABLE, null);
            };
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            return new Result(Status.UNAVAILABLE, null);
        } catch (IOException | JacksonException failure) {
            return new Result(Status.UNAVAILABLE, null);
        }
    }

    private Result readContext(byte[] body, String definitionId, TimeValidity timeValidity) {
        var node = responseReader.readTree(body);
        if (node == null || !node.isObject() || !text(node.path("typeId")) || !text(node.path("definitionId"))
                || !node.get("definitionId").stringValue().equals(definitionId)
                || !text(node.path("valueType")) || !node.path("unit").isString()
                || !(node.path("subsystemId").isNull() || text(node.path("subsystemId")))
                || !usableSource(node.path("sourceId"), timeValidity)) {
            return new Result(Status.UNAVAILABLE, null);
        }
        return new Result(Status.RESOLVED, new Context(node.get("typeId").stringValue(), definitionId,
                node.get("subsystemId").stringValue(), node.get("valueType").stringValue(),
                node.get("unit").stringValue(), node.get("sourceId").stringValue()));
    }

    private static boolean text(JsonNode node) {
        return node.isString() && !node.stringValue().isBlank();
    }

    private static boolean usableSource(JsonNode node, TimeValidity validity) {
        return validity == TimeValidity.VALID ? text(node) : validity == TimeValidity.INVALID && node.isNull();
    }

    public enum TimeValidity {
        VALID,
        INVALID
    }

    public enum Status {
        RESOLVED,
        INVALID_REQUEST,
        NOT_FOUND,
        UNAVAILABLE
    }

    public record Result(Status status, Context context) {
    }

    public record Context(String typeId, String definitionId, String subsystemId, String valueType, String unit,
                          String sourceId) {
    }

    private record Lookup(String instanceId, String definitionId, TimeValidity timeValidity, String occurredAt) {
    }
}
