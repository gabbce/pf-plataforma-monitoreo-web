package ar.edu.utn.frsf.pfc.management.station;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

import ar.edu.utn.frsf.pfc.management.catalog.DataContextController;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
class StationAuthorizationSecurity {

    private static final String RECORDS_TOKEN_PROPERTY = "pfc.internal.records-token";

    @Bean
    @Order(1)
    SecurityFilterChain stationAuthorization(HttpSecurity http, Environment environment) throws Exception {
        var paths = PathPatternRequestMatcher.withDefaults();
        var internalRecordsRequests = new OrRequestMatcher(
                paths.matcher(HttpMethod.POST, StationAuthorizationController.PATH),
                paths.matcher(HttpMethod.POST, StationAuthorizationController.CONTACT_PATH),
                paths.matcher(HttpMethod.POST, DataContextController.PATH));

        return http.securityMatcher(internalRecordsRequests)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(AbstractHttpConfigurer::disable)
                .addFilterBefore(new RecordsAuthenticationFilter(configuredToken(environment)),
                        AnonymousAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                .build();
    }

    private static byte[] configuredToken(Environment environment) {
        try {
            var token = environment.getProperty(RECORDS_TOKEN_PROPERTY, "").getBytes(StandardCharsets.US_ASCII);
            return StationToken.isValid(token) ? token : null;
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static final class RecordsAuthenticationFilter extends OncePerRequestFilter {

        private static final String BEARER_PREFIX = "Bearer ";

        private final byte[] token;

        private RecordsAuthenticationFilter(byte[] token) {
            this.token = token;
        }

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws ServletException, IOException {
            response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
            if (!authenticated(request)) {
                response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                return;
            }
            // Reject other media types before MVC can parse form or multipart bodies.
            if (!hasJsonContentType(request)) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                return;
            }
            var context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated("records", null, List.of()));
            SecurityContextHolder.setContext(context);
            chain.doFilter(request, response);
        }

        private boolean authenticated(HttpServletRequest request) {
            var headers = request.getHeaders(HttpHeaders.AUTHORIZATION);
            if (token == null || !headers.hasMoreElements()) {
                return false;
            }
            var value = headers.nextElement();
            return !headers.hasMoreElements() && value.length() == BEARER_PREFIX.length() + token.length
                    && value.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())
                    && MessageDigest.isEqual(token, value.substring(BEARER_PREFIX.length()).getBytes(StandardCharsets.US_ASCII));
        }

        private static boolean hasJsonContentType(HttpServletRequest request) {
            var contentType = request.getContentType();
            if (contentType == null) {
                return false;
            }
            try {
                return MediaType.APPLICATION_JSON.equalsTypeAndSubtype(MediaType.parseMediaType(contentType));
            } catch (InvalidMediaTypeException exception) {
                return false;
            }
        }
    }
}
