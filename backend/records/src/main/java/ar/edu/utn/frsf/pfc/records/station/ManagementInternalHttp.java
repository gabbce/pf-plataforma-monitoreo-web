package ar.edu.utn.frsf.pfc.records.station;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;

import jakarta.annotation.PreDestroy;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@EnableConfigurationProperties(ManagementInternalHttp.Properties.class)
final class ManagementInternalHttp {

    private static final Duration TIMEOUT = Duration.ofSeconds(2);
    private static final Pattern SERVICE_TOKEN = Pattern.compile("[0-9a-f]{64}");

    private final HttpClient transport;
    private final URI base;
    private final String token;

    ManagementInternalHttp(Properties properties) {
        base = baseUri(properties.managementUrl());
        var configuredToken = properties.recordsToken();
        token = configuredToken != null && SERVICE_TOKEN.matcher(configuredToken).matches() ? configuredToken : null;
        transport = HttpClient.newBuilder().connectTimeout(TIMEOUT)
                .followRedirects(HttpClient.Redirect.NEVER).proxy(HttpClient.Builder.NO_PROXY).build();
    }

    <T> HttpResponse<T> post(String path, byte[] payload, HttpResponse.BodyHandler<T> bodyHandler)
            throws IOException, InterruptedException {
        if (base == null || token == null) {
            throw new IOException("Management connection is not configured");
        }
        var request = HttpRequest.newBuilder(base.resolve(path)).timeout(TIMEOUT)
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(payload)).build();
        var pending = transport.sendAsync(request, bodyHandler);
        try {
            return pending.get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (ExecutionException | TimeoutException failure) {
            pending.cancel(true);
            throw new IOException("Management request failed", failure);
        } catch (InterruptedException failure) {
            pending.cancel(true);
            throw failure;
        }
    }

    @PreDestroy
    void close() {
        transport.close();
    }

    private static URI baseUri(String value) {
        if (value == null) {
            return null;
        }
        try {
            var base = new URI(value);
            boolean http = "http".equalsIgnoreCase(base.getScheme()) || "https".equalsIgnoreCase(base.getScheme());
            int port = base.getPort();
            if (!http || base.getHost() == null || base.getRawAuthority().endsWith(":")
                    || (port != -1 && (port < 1 || port > 65535)) || base.getRawUserInfo() != null
                    || base.getRawQuery() != null || base.getRawFragment() != null
                    || (!base.getRawPath().isEmpty() && !base.getRawPath().equals("/"))) {
                return null;
            }
            return base;
        } catch (URISyntaxException failure) {
            return null;
        }
    }

    @ConfigurationProperties("pfc.internal")
    record Properties(String managementUrl, String recordsToken) {
        @Override
        public String toString() {
            return "Properties[redacted]";
        }
    }
}
