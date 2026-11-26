package ar.edu.utn.frsf.pfc.management.station;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectReader;
import tools.jackson.databind.json.JsonMapper;

@RestController
class StationAuthorizationController {

    static final String PATH = "/internal/v1/station-authorizations/check";

    private static final int MAX_BODY_BYTES = 4096;
    private static final Logger LOG = LoggerFactory.getLogger(StationAuthorizationController.class);

    private final StationCredentials credentials;
    private final ObjectReader json;

    StationAuthorizationController(StationCredentials credentials, JsonMapper json) {
        this.credentials = credentials;
        this.json = json.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS,
                DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY);
    }

    @PostMapping(PATH)
    ResponseEntity<Void> check(HttpServletRequest request) {
        if (request.getContentLengthLong() > MAX_BODY_BYTES) {
            return ResponseEntity.badRequest().build();
        }
        var check = readCheck(request);
        if (check == null) {
            return ResponseEntity.badRequest().build();
        }
        if (check.operation != StationOperation.RECORDS_WRITE) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        try {
            if (credentials.authorizes(check.instanceId, check.producerId, check.operation, check.stationToken)) {
                return ResponseEntity.noContent().build();
            }
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        } catch (RuntimeException exception) {
            LOG.error("Station authorization lookup failed");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
    }

    private Check readCheck(HttpServletRequest request) {
        try {
            var body = request.getInputStream().readNBytes(MAX_BODY_BYTES + 1);
            if (body.length > MAX_BODY_BYTES) {
                return null;
            }
            var node = json.readTree(body);
            if (!node.isObject() || node.size() != 4 || !node.path("instanceId").isString()
                    || !node.path("producerId").isString() || !node.path("operation").isString()
                    || !node.path("stationToken").isString()) {
                return null;
            }
            return new Check(node.get("instanceId").stringValue(), node.get("producerId").stringValue(),
                    StationOperation.valueOf(node.get("operation").stringValue()),
                    node.get("stationToken").stringValue().getBytes(StandardCharsets.US_ASCII));
        } catch (IOException | JacksonException | IllegalArgumentException exception) {
            return null;
        }
    }

    // The request object retains the credential as bytes to keep it out of the record's string representation.
    private record Check(String instanceId, String producerId, StationOperation operation, byte[] stationToken) {
    }
}
