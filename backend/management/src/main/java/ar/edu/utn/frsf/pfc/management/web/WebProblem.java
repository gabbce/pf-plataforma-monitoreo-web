package ar.edu.utn.frsf.pfc.management.web;

import java.io.IOException;

import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import tools.jackson.databind.json.JsonMapper;

enum WebProblem {
    INVALID_REQUEST(400, "Solicitud inválida"),
    INVALID_CREDENTIALS(401, "No se pudo iniciar sesión"),
    INVALID_CSRF(403, "Solicitud rechazada"),
    SESSION_REQUIRED(401, "Se requiere iniciar sesión"),
    ACCESS_DENIED(403, "Acceso denegado"),
    SERVER_ERROR(500, "No se pudo completar la operación");

    private final int status;
    private final String title;

    WebProblem(int status, String title) {
        this.status = status;
        this.title = title;
    }

    Failure failure() {
        return new Failure(this);
    }

    void write(HttpServletResponse response, JsonMapper json) throws IOException {
        response.setStatus(status);
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        if (status == 401) {
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Session realm=\"PFC\"");
        }
        json.writeValue(response.getOutputStream(), new Body("about:blank", title, status, name()));
    }

    private record Body(String type, String title, int status, String code) {
    }

    static final class Failure extends RuntimeException {

        private final WebProblem problem;

        private Failure(WebProblem problem) {
            super(problem.name());
            this.problem = problem;
        }

        WebProblem problem() {
            return problem;
        }
    }
}
