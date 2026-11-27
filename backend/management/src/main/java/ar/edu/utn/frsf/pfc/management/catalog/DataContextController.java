package ar.edu.utn.frsf.pfc.management.catalog;

import java.io.IOException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;

import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectReader;
import tools.jackson.databind.json.JsonMapper;

@RestController
public class DataContextController {

    public static final String PATH = "/internal/v1/data-context/resolve";

    private static final int MAX_BODY_BYTES = 4096;
    private static final Logger LOG = LoggerFactory.getLogger(DataContextController.class);

    private final DataSourceResolver catalog;
    private final ObjectReader json;

    DataContextController(DataSourceResolver catalog, JsonMapper json) {
        this.catalog = catalog;
        this.json = json.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS,
                DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY);
    }

    @PostMapping(path = PATH, produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<DataSourceResolver.DataContext> resolve(HttpServletRequest request) {
        var lookup = readLookup(request);
        if (lookup == null) {
            return ResponseEntity.badRequest().build();
        }
        try {
            // An untrusted occurrence must never select a historical source.
            var effectiveOccurrence = lookup.timeValidity() == TimeValidity.VALID ? lookup.occurredAt() : null;
            return catalog.findDataContext(lookup.instanceId(), lookup.definitionId(), effectiveOccurrence)
                    .filter(context -> effectiveOccurrence == null || context.sourceId() != null)
                    .map(ResponseEntity::ok)
                    .orElseGet(() -> ResponseEntity.notFound().build());
        } catch (DataAccessException failure) {
            LOG.error("DATA context lookup failed");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
    }

    private Lookup readLookup(HttpServletRequest request) {
        if (request.getContentLengthLong() > MAX_BODY_BYTES) {
            return null;
        }
        try {
            var body = request.getInputStream().readNBytes(MAX_BODY_BYTES + 1);
            if (body.length > MAX_BODY_BYTES) {
                return null;
            }
            var node = json.readTree(body);
            if (node == null || !node.isObject()
                    || !identifier(node.path("instanceId")) || !identifier(node.path("definitionId"))
                    || !node.path("timeValidity").isString()
                    || node.size() != (node.has("occurredAt") ? 4 : 3)) {
                return null;
            }
            var validity = TimeValidity.valueOf(node.get("timeValidity").stringValue());
            var mark = node.path("occurredAt");
            var occurredAt = mark.isMissingNode() || mark.isNull() ? null : parseOccurrence(mark);
            if (validity == TimeValidity.VALID && occurredAt == null) {
                return null;
            }
            return new Lookup(node.get("instanceId").stringValue(), node.get("definitionId").stringValue(),
                    validity, occurredAt);
        } catch (IOException | JacksonException | IllegalArgumentException | DateTimeParseException failure) {
            return null;
        }
    }

    private static boolean identifier(JsonNode node) {
        return node.isString() && !node.stringValue().isBlank() && node.stringValue().indexOf('\0') == -1;
    }

    private static Instant parseOccurrence(JsonNode node) {
        if (!node.isString() || !node.stringValue().endsWith("Z")) {
            throw new IllegalArgumentException("Expected a UTC occurrence");
        }
        return OffsetDateTime.parse(node.stringValue()).toInstant();
    }

    private enum TimeValidity {
        VALID,
        INVALID
    }

    private record Lookup(String instanceId, String definitionId, TimeValidity timeValidity, Instant occurredAt) {
    }
}
