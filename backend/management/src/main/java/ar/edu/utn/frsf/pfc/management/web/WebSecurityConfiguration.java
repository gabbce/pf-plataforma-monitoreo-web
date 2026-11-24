package ar.edu.utn.frsf.pfc.management.web;

import java.io.IOException;
import java.util.List;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import tools.jackson.databind.json.JsonMapper;

@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
class WebSecurityConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(WebSecurityConfiguration.class);

    @Bean
    SecurityFilterChain webApi(HttpSecurity http, WebSessions sessions, JsonMapper json) throws Exception {
        var matcher = PathPatternRequestMatcher.withDefaults();
        var csrfPath = WebCookies.API + "/auth/csrf";
        var loginPath = WebCookies.API + "/auth/session";
        var machinesPath = WebCookies.API + "/machines";
        var machinePath = machinesPath + "/{chassis}";
        var csrfResource = matcher.matcher(HttpMethod.GET, csrfPath);
        var login = matcher.matcher(HttpMethod.POST, loginPath);
        var machines = new OrRequestMatcher(matcher.matcher(HttpMethod.GET, machinesPath),
                matcher.matcher(HttpMethod.GET, machinePath));

        return http
                .securityMatcher(new OrRequestMatcher(matcher.matcher(csrfPath), matcher.matcher(loginPath),
                        matcher.matcher(machinesPath), matcher.matcher(machinePath)))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(csrf -> csrf.requireCsrfProtectionMatcher(login).csrfTokenRepository(sessions)
                        .csrfTokenRequestHandler(new HeaderOnlyCsrfHandler()))
                .addFilterBefore(new WebApiFilter(sessions, machines, json), CsrfFilter.class)
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, error) ->
                                WebProblem.SESSION_REQUIRED.write(response, json))
                        .accessDeniedHandler((request, response, error) ->
                                (error instanceof CsrfException ? WebProblem.INVALID_CSRF : WebProblem.ACCESS_DENIED)
                                        .write(response, json)))
                .authorizeHttpRequests(auth -> auth.requestMatchers(csrfResource, login).permitAll()
                        .requestMatchers(machines).authenticated()
                        .anyRequest().denyAll())
                .build();
    }

    private static final class HeaderOnlyCsrfHandler extends CsrfTokenRequestAttributeHandler {

        @Override
        public String resolveCsrfTokenValue(HttpServletRequest request, CsrfToken csrfToken) {
            return request.getHeader(csrfToken.getHeaderName());
        }
    }

    private static final class WebApiFilter extends OncePerRequestFilter {

        private final WebSessions sessions;
        private final RequestMatcher machines;
        private final JsonMapper json;

        private WebApiFilter(WebSessions sessions, RequestMatcher machines, JsonMapper json) {
            this.sessions = sessions;
            this.machines = machines;
            this.json = json;
        }

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws ServletException, IOException {
            response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
            try {
                if (machines.matches(request)) {
                    sessions.authenticatedUser(request).ifPresent(userId -> {
                        var context = SecurityContextHolder.createEmptyContext();
                        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(userId, null,
                                List.of()));
                        SecurityContextHolder.setContext(context);
                    });
                }
                chain.doFilter(request, response);
            } catch (ServletException | RuntimeException error) {
                if (response.isCommitted()) {
                    throw error;
                }
                LOG.error("Web API request failed", error);
                WebProblem.SERVER_ERROR.write(response, json);
            }
        }
    }
}
