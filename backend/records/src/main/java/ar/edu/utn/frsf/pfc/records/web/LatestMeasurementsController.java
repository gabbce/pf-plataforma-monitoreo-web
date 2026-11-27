package ar.edu.utn.frsf.pfc.records.web;

import java.util.regex.Pattern;

import jakarta.servlet.http.HttpServletRequest;

import ar.edu.utn.frsf.pfc.records.station.WebMachineAccessClient;
import ar.edu.utn.frsf.pfc.records.storage.MeasurementReads;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
class LatestMeasurementsController {

    private static final String SESSION_COOKIE = "PFC_SESSION";
    private static final Pattern SESSION_FORMAT = Pattern.compile("[A-Za-z0-9_-]{43}");
    private static final Logger LOG = LoggerFactory.getLogger(LatestMeasurementsController.class);

    private final WebMachineAccessClient access;
    private final MeasurementReads measurements;

    LatestMeasurementsController(WebMachineAccessClient access, MeasurementReads measurements) {
        this.access = access;
        this.measurements = measurements;
    }

    @GetMapping("/api/v1/machines/{chassis}/measurements/latest")
    ResponseEntity<?> latest(@PathVariable String chassis, HttpServletRequest request) {
        var token = sessionToken(request);
        if (token == null) {
            return Problem.SESSION_REQUIRED.response();
        }
        try {
            return switch (access.check(chassis, token)) {
                case ALLOWED -> ResponseEntity.ok().cacheControl(CacheControl.noStore())
                        .contentType(MediaType.APPLICATION_JSON).body(measurements.latest(chassis));
                case SESSION_REQUIRED -> Problem.SESSION_REQUIRED.response();
                case ACCESS_DENIED -> Problem.ACCESS_DENIED.response();
                case UNAVAILABLE -> Problem.SERVER_ERROR.response();
            };
        } catch (RuntimeException failure) {
            LOG.warn("Latest measurements request failed");
            return Problem.SERVER_ERROR.response();
        }
    }

    private static String sessionToken(HttpServletRequest request) {
        var cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        String token = null;
        for (var cookie : cookies) {
            if (SESSION_COOKIE.equals(cookie.getName())) {
                if (token != null || cookie.getValue() == null || !SESSION_FORMAT.matcher(cookie.getValue()).matches()) {
                    return null;
                }
                token = cookie.getValue();
            }
        }
        return token;
    }

    private enum Problem {
        SESSION_REQUIRED(HttpStatus.UNAUTHORIZED, "Se requiere iniciar sesión"),
        ACCESS_DENIED(HttpStatus.FORBIDDEN, "Acceso denegado"),
        SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "No se pudo completar la operación");

        private final HttpStatus status;
        private final String title;

        Problem(HttpStatus status, String title) {
            this.status = status;
            this.title = title;
        }

        private ResponseEntity<ProblemBody> response() {
            var response = ResponseEntity.status(status).cacheControl(CacheControl.noStore())
                    .contentType(MediaType.APPLICATION_PROBLEM_JSON);
            if (status == HttpStatus.UNAUTHORIZED) {
                response.header(HttpHeaders.WWW_AUTHENTICATE, "Session realm=\"PFC\"");
            }
            return response.body(new ProblemBody("about:blank", title, status.value(), name()));
        }
    }

    private record ProblemBody(String type, String title, int status, String code) {
    }
}
