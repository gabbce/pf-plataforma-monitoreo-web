package ar.edu.utn.frsf.pfc.management.web;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.regex.Pattern;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;

final class WebCookies {

    static final String API = "/api/v1";
    static final String CONTEXT = "PFC_LOGIN";
    static final String SESSION = "PFC_SESSION";

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Pattern TOKEN_FORMAT = Pattern.compile("[A-Za-z0-9_-]{43}");

    private WebCookies() {
    }

    static String newToken() {
        var bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String digest(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.US_ASCII)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    static Optional<String> digestFrom(HttpServletRequest request, String name) {
        var cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        String token = null;
        for (var cookie : cookies) {
            if (name.equals(cookie.getName())) {
                if (token != null || !TOKEN_FORMAT.matcher(cookie.getValue()).matches()) {
                    return Optional.empty();
                }
                token = cookie.getValue();
            }
        }
        return Optional.ofNullable(token).map(WebCookies::digest);
    }

    static void set(HttpServletResponse response, String name, String token) {
        add(response, builder(name, token).build());
    }

    static void clear(HttpServletResponse response, String name) {
        add(response, builder(name, "").maxAge(Duration.ZERO).build());
    }

    private static ResponseCookie.ResponseCookieBuilder builder(String name, String token) {
        return ResponseCookie.from(name, token).path(API).secure(true).httpOnly(true).sameSite("Lax");
    }

    private static void add(HttpServletResponse response, ResponseCookie cookie) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
