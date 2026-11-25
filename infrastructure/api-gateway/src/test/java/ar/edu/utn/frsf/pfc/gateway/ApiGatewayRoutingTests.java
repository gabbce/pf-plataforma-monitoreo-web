package ar.edu.utn.frsf.pfc.gateway;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApiGatewayRoutingTests {

    private static HttpServer management;
    private static HttpServer records;

    @LocalServerPort
    private int port;

    @DynamicPropertySource
    static void upstreams(DynamicPropertyRegistry registry) throws IOException {
        management = startUpstream("management");
        records = startUpstream("records");
        registry.add("platform.management-url", () -> address(management));
        registry.add("platform.records-url", () -> address(records));
    }

    @AfterAll
    static void stopUpstreams() {
        if (management != null) {
            management.stop(0);
        }
        if (records != null) {
            records.stop(0);
        }
    }

    @Test
    void routesTheThreeIntegrationOperationsToTheirService() throws Exception {
        assertRouted("POST", "/integration/v1/records", "records");
        assertRouted("GET", "/integration/v1/configuration?instanceId=001", "management");
        assertRouted("POST", "/integration/v1/configuration-results", "management");
    }

    @Test
    void routesWebReadsToManagementWithoutChangingTheirPaths() throws Exception {
        assertRouted("GET", "/api/v1/auth/csrf", "management");
        assertRouted("GET", "/api/v1/auth/csrf?purpose=logout", "management");
        assertRouted("GET", "/api/v1/machines", "management");
        assertRouted("GET", "/api/v1/machines/000123", "management");
    }

    @Test
    void forwardsLoginAndLogoutRequestsAndSessionCookies() throws Exception {
        String path = "/api/v1/auth/session";
        String loginBody = "{\"identifier\":\"reader\",\"password\":\"example\"}";
        String preAuthCookie = "PFC_LOGIN=context";
        String loginToken = "test-login-token";
        String logoutToken = "test-logout-token";
        String session = "PFC_SESSION=session";
        String sessionCookie = "PFC_SESSION=session; Path=/api/v1; Secure; HttpOnly; SameSite=Lax";
        String clearedCookie = "PFC_SESSION=; Path=/api/v1; Max-Age=0; Secure; HttpOnly; SameSite=Lax";
        var forwardedLogin = new CompletableFuture<ForwardedAccessRequest>();
        var forwardedLogout = new CompletableFuture<ForwardedAccessRequest>();
        management.createContext(path, exchange -> {
            try (exchange) {
                var headers = exchange.getRequestHeaders();
                var forwarded = new ForwardedAccessRequest(exchange.getRequestMethod(), exchange.getRequestURI().toString(),
                        headers.getFirst("Cookie"), headers.getFirst("X-CSRF-TOKEN"), headers.getFirst("Content-Type"),
                        new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                if ("DELETE".equals(forwarded.method())) {
                    forwardedLogout.complete(forwarded);
                    exchange.getResponseHeaders().add("Set-Cookie", clearedCookie);
                } else {
                    forwardedLogin.complete(forwarded);
                    exchange.getResponseHeaders().add("Set-Cookie", sessionCookie);
                }
                exchange.getResponseHeaders().add("Cache-Control", "no-store");
                exchange.sendResponseHeaders(204, -1);
            }
        });

        var login = send(request(path)
                .header("Cookie", preAuthCookie)
                .header("X-CSRF-TOKEN", loginToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(loginBody))
                .build());

        assertThat(forwardedLogin.get(5, TimeUnit.SECONDS)).isEqualTo(new ForwardedAccessRequest(
                "POST", path, preAuthCookie, loginToken, "application/json", loginBody));
        assertThat(login.statusCode()).isEqualTo(204);
        assertThat(login.headers().allValues("Set-Cookie")).containsExactly(sessionCookie);
        assertThat(login.headers().allValues("Cache-Control")).containsExactly("no-store");

        var logout = send(request(path).header("Cookie", session).header("X-CSRF-TOKEN", logoutToken).DELETE().build());

        assertThat(forwardedLogout.get(5, TimeUnit.SECONDS)).isEqualTo(new ForwardedAccessRequest(
                "DELETE", path, session, logoutToken, null, ""));
        assertThat(logout.statusCode()).isEqualTo(204);
        assertThat(logout.body()).isEmpty();
        assertThat(logout.headers().allValues("Set-Cookie")).containsExactly(clearedCookie);
        assertThat(logout.headers().allValues("Cache-Control")).containsExactly("no-store");
    }

    @Test
    void forwardsManagementSessionChallenge() throws Exception {
        String path = "/api/v1/machines/000999";
        String challenge = "Session realm=\"PFC\"";
        String problem = "{\"type\":\"about:blank\",\"title\":\"Session required\","
                + "\"status\":401,\"code\":\"SESSION_REQUIRED\"}";
        management.createContext(path, exchange -> {
            try (exchange) {
                exchange.getResponseHeaders().add("WWW-Authenticate", challenge);
                exchange.getResponseHeaders().add("Cache-Control", "no-store");
                exchange.getResponseHeaders().add("Content-Type", "application/problem+json");
                var payload = problem.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(401, payload.length);
                exchange.getResponseBody().write(payload);
            }
        });

        var response = send("GET", path);

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.headers().allValues("WWW-Authenticate")).containsExactly(challenge);
        assertThat(response.headers().allValues("Cache-Control")).containsExactly("no-store");
        assertThat(response.headers().allValues("Content-Type")).containsExactly("application/problem+json");
        assertThat(response.body()).isEqualTo(problem);
    }

    @Test
    void rejectsUnconfiguredMethodsAndPaths() throws Exception {
        assertNotRouted("GET", "/integration/v1/records");
        assertNotRouted("POST", "/integration/v1/configuration");
        assertNotRouted("POST", "/integration/v1/configuration-results/extra");
        assertNotRouted("POST", "/integration/v1/records/");
        assertNotRouted("GET", "/api/unknown");
    }

    @Test
    void rejectsOtherWebMethodsAndPaths() throws Exception {
        assertNotRouted("POST", "/api/v1/auth/csrf");
        assertNotRouted("GET", "/api/v1/auth/session");
        assertNotRouted("POST", "/api/v1/machines");
        assertNotRouted("HEAD", "/api/v1/machines/000123");
        assertNotRouted("GET", "/api/v1/auth/csrf/");
        assertNotRouted("POST", "/api/v1/auth/session/extra");
        assertNotRouted("DELETE", "/api/v1/auth/session/");
        assertNotRouted("DELETE", "/api/v1/auth/session/extra");
        assertNotRouted("PUT", "/api/v1/auth/session");
        assertNotRouted("GET", "/api/v1/machines/");
        assertNotRouted("GET", "/api/v1/machines/000123/");
        assertNotRouted("GET", "/api/v1/machines/000123/measurements/latest");
        assertNotRouted("GET", "/api/v1/machines/000123/measurements/history");
        assertNotRouted("GET", "/api/v1/actuator/health");
        assertNotRouted("GET", "/integration/v1/auth/csrf");
        assertNotRouted("POST", "/api/v1/records");
    }

    private void assertRouted(String method, String path, String service) throws Exception {
        var response = send(method, path);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo(service + " " + method + " " + path);
    }

    private void assertNotRouted(String method, String path) throws Exception {
        assertThat(send(method, path).statusCode()).as("%s %s", method, path).isEqualTo(404);
    }

    private HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(5));
    }

    private HttpResponse<String> send(String method, String path) throws Exception {
        return send(request(path).method(method, HttpRequest.BodyPublishers.noBody()).build());
    }

    private HttpResponse<String> send(HttpRequest request) throws Exception {
        try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()) {
            return client.send(request, HttpResponse.BodyHandlers.ofString());
        }
    }

    private static HttpServer startUpstream(String service) throws IOException {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            try (exchange) {
                var payload = (service + " " + exchange.getRequestMethod() + " " + exchange.getRequestURI())
                        .getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, payload.length);
                exchange.getResponseBody().write(payload);
            }
        });
        server.start();
        return server;
    }

    private static String address(HttpServer server) {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private record ForwardedAccessRequest(String method, String path, String cookie, String csrfToken, String contentType,
            String body) {
    }
}
