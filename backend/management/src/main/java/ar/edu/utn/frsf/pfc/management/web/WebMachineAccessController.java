package ar.edu.utn.frsf.pfc.management.web;

import java.io.IOException;
import java.util.Set;

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
public class WebMachineAccessController {

    public static final String PATH = "/internal/v1/web-machine-access/check";

    private static final int MAX_BODY_BYTES = 64 * 1024;
    private static final Set<String> REQUEST_FIELDS = Set.of("chassis", "sessionToken");
    private static final Logger LOG = LoggerFactory.getLogger(WebMachineAccessController.class);

    private final WebSessions sessions;
    private final WebMachineCatalog catalog;
    private final ObjectReader json;

    WebMachineAccessController(WebSessions sessions, WebMachineCatalog catalog, JsonMapper json) {
        this.sessions = sessions;
        this.catalog = catalog;
        this.json = json.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS,
                DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY);
    }

    @PostMapping(PATH)
    ResponseEntity<Result> check(HttpServletRequest request) {
        var lookup = readLookup(request);
        if (lookup == null) {
            return ResponseEntity.badRequest().build();
        }
        try {
            var session = sessions.authenticatedToken(lookup.sessionToken());
            if (session.isEmpty()) {
                return ResponseEntity.ok(new Result(Decision.SESSION_REQUIRED));
            }
            var allowed = catalog.canReadMachine(session.orElseThrow().userId(), lookup.chassis());
            return ResponseEntity.ok(new Result(allowed ? Decision.ALLOWED : Decision.ACCESS_DENIED));
        } catch (RuntimeException failure) {
            LOG.warn("Internal machine access check failed");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
    }

    private Lookup readLookup(HttpServletRequest request) {
        if (request.getContentLengthLong() > MAX_BODY_BYTES) {
            return null;
        }
        try {
            var bytes = request.getInputStream().readNBytes(MAX_BODY_BYTES + 1);
            if (bytes.length > MAX_BODY_BYTES) {
                return null;
            }
            var body = json.readTree(bytes);
            if (body == null || !body.isObject() || !body.propertyNames().equals(REQUEST_FIELDS)
                    || !body.path("chassis").isString() || !body.path("sessionToken").isString()) {
                return null;
            }
            var chassis = body.get("chassis").stringValue();
            if (chassis.isEmpty() || chassis.indexOf('\0') >= 0) {
                return null;
            }
            return new Lookup(chassis, body.get("sessionToken").stringValue());
        } catch (IOException | JacksonException failure) {
            return null;
        }
    }

    private record Lookup(String chassis, String sessionToken) {
        @Override
        public String toString() {
            return "Lookup[redacted]";
        }
    }

    record Result(Decision decision) {
    }

    enum Decision {
        ALLOWED,
        SESSION_REQUIRED,
        ACCESS_DENIED
    }
}
