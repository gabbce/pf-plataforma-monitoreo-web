package ar.edu.utn.frsf.pfc.management.web;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.DefaultCsrfToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class WebSessions implements CsrfTokenRepository {

    static final String CSRF_HEADER = "X-CSRF-TOKEN";

    private final JdbcClient jdbc;
    private final WebCredentials credentials;
    private final Clock clock;
    private final WebAuthConfiguration.Settings settings;

    WebSessions(JdbcClient jdbc, WebCredentials credentials, Clock clock, WebAuthConfiguration.Settings settings) {
        this.jdbc = jdbc;
        this.credentials = credentials;
        this.clock = clock;
        this.settings = settings;
    }

    @Override
    public CsrfToken generateToken(HttpServletRequest request) {
        return token(WebCookies.newToken());
    }

    @Override
    public CsrfToken loadToken(HttpServletRequest request) {
        return WebCookies.digestFrom(request, WebCookies.CONTEXT)
                .flatMap(digest -> jdbc.sql("""
                        SELECT csrf_token FROM web_login_context
                        WHERE context_digest = :digest AND expires_at > :now
                        """)
                        .param("digest", digest)
                        .param("now", now())
                        .query(String.class).optional())
                .map(WebSessions::token)
                .orElse(null);
    }

    @Override
    public void saveToken(CsrfToken csrf, HttpServletRequest request, HttpServletResponse response) {
        if (csrf == null) {
            WebCookies.digestFrom(request, WebCookies.CONTEXT).ifPresent(digest ->
                    jdbc.sql("DELETE FROM web_login_context WHERE context_digest = :digest")
                            .param("digest", digest).update());
            WebCookies.clear(response, WebCookies.CONTEXT);
            return;
        }
        // An invalid login POST cannot establish an anonymous context.
        if (!"GET".equals(request.getMethod())) {
            return;
        }
        var issued = now();
        jdbc.sql("DELETE FROM web_login_context WHERE expires_at <= :now").param("now", issued).update();
        var context = WebCookies.newToken();
        jdbc.sql("""
                INSERT INTO web_login_context (context_digest, csrf_token, expires_at)
                VALUES (:digest, :token, :expires)
                """)
                .param("digest", WebCookies.digest(context))
                .param("token", csrf.getToken())
                .param("expires", issued.plus(settings.csrfTtl()))
                .update();
        WebCookies.set(response, WebCookies.CONTEXT, context);
    }

    @Transactional
    public String login(String identifier, String password, HttpServletRequest request) {
        var contextDigest = WebCookies.digestFrom(request, WebCookies.CONTEXT)
                .orElseThrow(WebProblem.INVALID_CSRF::failure);
        var authentication = UsernamePasswordAuthenticationToken.unauthenticated(identifier, password);
        long userId;
        try {
            userId = (long) credentials.authenticate(authentication).getPrincipal();
        } catch (BadCredentialsException exception) {
            throw WebProblem.INVALID_CREDENTIALS.failure();
        } finally {
            authentication.eraseCredentials();
        }
        var createdAt = now();
        var consumed = jdbc.sql("""
                DELETE FROM web_login_context
                WHERE context_digest = :digest AND csrf_token = :token AND expires_at > :now
                """)
                .param("digest", contextDigest)
                .param("token", request.getHeader(CSRF_HEADER))
                .param("now", createdAt)
                .update();
        if (consumed != 1) {
            throw WebProblem.INVALID_CSRF.failure();
        }

        jdbc.sql("DELETE FROM web_session WHERE expires_at <= :now").param("now", createdAt).update();
        WebCookies.digestFrom(request, WebCookies.SESSION).ifPresent(digest ->
                jdbc.sql("DELETE FROM web_session WHERE session_digest = :digest").param("digest", digest).update());

        var session = WebCookies.newToken();
        jdbc.sql("""
                INSERT INTO web_session (session_digest, user_id, expires_at) VALUES (:digest, :user, :expires)
                """)
                .param("digest", WebCookies.digest(session))
                .param("user", userId)
                .param("expires", createdAt.plus(settings.sessionTtl()))
                .update();
        return session;
    }

    Optional<Long> authenticatedUser(HttpServletRequest request) {
        return WebCookies.digestFrom(request, WebCookies.SESSION)
                .flatMap(digest -> jdbc.sql("""
                        SELECT s.user_id FROM web_session s JOIN web_user u USING (user_id)
                        WHERE s.session_digest = :digest AND s.expires_at > :now AND u.active
                        """)
                        .param("digest", digest)
                        .param("now", now())
                        .query(Long.class).optional());
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);
    }

    private static CsrfToken token(String value) {
        return new DefaultCsrfToken(CSRF_HEADER, "_csrf", value);
    }
}
