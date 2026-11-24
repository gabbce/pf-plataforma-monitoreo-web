package ar.edu.utn.frsf.pfc.management.web;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
class WebCredentials implements AuthenticationManager {

    static final String TECHNICAL_SERVICE = "TECHNICAL_SERVICE";
    static final String OWNER = "OWNER";
    static final String MACHINE_READ = "MACHINE_READ";

    private static final int MAX_PASSWORD_BYTES = 72;
    private final JdbcClient jdbc;
    private final PasswordEncoder passwords;
    private final String dummyHash;

    WebCredentials(JdbcClient jdbc, PasswordEncoder passwords) {
        this.jdbc = jdbc;
        this.passwords = passwords;
        this.dummyHash = passwords.encode(WebCookies.newToken());
    }

    @Override
    public Authentication authenticate(Authentication authentication) {
        var account = jdbc.sql("""
                SELECT u.user_id, u.password_hash, u.active,
                       EXISTS(SELECT 1 FROM web_user_role r
                              WHERE r.user_id = u.user_id AND r.role_id IN (:technician, :owner)) AS web_role
                FROM web_user u WHERE u.identifier = :identifier
                """)
                .param("technician", TECHNICAL_SERVICE)
                .param("owner", OWNER)
                .param("identifier", authentication.getName())
                .query((rows, index) -> new Account(rows.getLong(1), rows.getString(2),
                        rows.getBoolean(3), rows.getBoolean(4)))
                .optional();
        var raw = (String) authentication.getCredentials();
        var supported = supports(raw);
        var hash = account.map(Account::passwordHash).orElse(dummyHash);
        var matches = passwords.matches(supported ? raw : "", hash);
        var allowed = account.filter(user -> user.active() && user.webRole());
        if (!supported || !matches || allowed.isEmpty()) {
            throw new BadCredentialsException("Invalid credentials");
        }
        return UsernamePasswordAuthenticationToken.authenticated(allowed.orElseThrow().id(), null, List.of());
    }

    static boolean supports(String password) {
        return password.getBytes(StandardCharsets.UTF_8).length <= MAX_PASSWORD_BYTES;
    }

    private record Account(long id, String passwordHash, boolean active, boolean webRole) {
    }
}
