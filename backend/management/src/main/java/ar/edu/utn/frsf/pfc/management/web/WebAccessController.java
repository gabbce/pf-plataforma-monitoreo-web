package ar.edu.utn.frsf.pfc.management.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping(WebCookies.API + "/auth")
class WebAccessController {

    private final WebSessions sessions;

    WebAccessController(WebSessions sessions) {
        this.sessions = sessions;
    }

    @GetMapping(path = "/csrf", produces = MediaType.APPLICATION_JSON_VALUE)
    CsrfResponse csrf(@RequestAttribute("org.springframework.security.web.csrf.CsrfToken") CsrfToken csrf,
            HttpServletRequest request) {
        if (request.getParameterValues("purpose") != null && !isLogoutPurpose(request)) {
            throw WebProblem.INVALID_REQUEST.failure();
        }
        return new CsrfResponse(csrf.getToken());
    }

    static boolean isLogoutPurpose(HttpServletRequest request) {
        var purposes = request.getParameterValues("purpose");
        return purposes != null && purposes.length == 1 && "logout".equals(purposes[0]);
    }

    @PostMapping("/session")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void login(@RequestBody JsonNode body, HttpServletRequest request, HttpServletResponse response) {
        var contentType = MediaType.parseMediaType(request.getContentType());
        if (!MediaType.APPLICATION_JSON.includes(contentType) || !body.isObject() || body.size() != 2) {
            throw WebProblem.INVALID_REQUEST.failure();
        }
        var identifier = requiredString(body.path("identifier"));
        var password = requiredString(body.path("password"));
        var token = sessions.login(identifier, password, request);
        WebCookies.set(response, WebCookies.SESSION, token);
        WebCookies.clear(response, WebCookies.CONTEXT);
    }

    @DeleteMapping("/session")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void logout(@RequestAttribute(WebSessions.LOGOUT_SESSION) WebSessions.ActiveSession session,
            HttpServletResponse response) {
        sessions.logout(session);
        WebCookies.clear(response, WebCookies.SESSION);
    }

    private static String requiredString(JsonNode node) {
        if (!node.isString() || node.stringValue().isEmpty()) {
            throw WebProblem.INVALID_REQUEST.failure();
        }
        return node.stringValue();
    }

    private record CsrfResponse(String token) {
    }
}
