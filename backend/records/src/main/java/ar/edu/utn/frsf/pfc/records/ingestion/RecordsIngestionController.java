package ar.edu.utn.frsf.pfc.records.ingestion;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectReader;
import tools.jackson.databind.json.JsonMapper;

@RestController
class RecordsIngestionController {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final Pattern STATION_TOKEN = Pattern.compile("[0-9a-f]{64}");

    private final RecordsIngestion ingestion;
    private final ObjectReader json;
    private final int maxBodyBytes;
    private final int maxRecords;

    RecordsIngestionController(RecordsIngestion ingestion, JsonMapper json,
            @Value("${pfc.records.ingestion.max-body-bytes:65536}") int maxBodyBytes,
            @Value("${pfc.records.ingestion.max-records:100}") int maxRecords) {
        if (maxBodyBytes <= 0 || maxBodyBytes == Integer.MAX_VALUE || maxRecords <= 0) {
            throw new IllegalArgumentException("Invalid records ingestion limits");
        }
        this.ingestion = ingestion;
        this.json = json.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS,
                DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY, DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
        this.maxBodyBytes = maxBodyBytes;
        this.maxRecords = maxRecords;
    }

    @PostMapping("/integration/v1/records")
    ResponseEntity<RecordsIngestion.Results> receive(HttpServletRequest request) {
        var stationToken = stationToken(request);
        if (stationToken == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).cacheControl(CacheControl.noStore())
                    .header(HttpHeaders.WWW_AUTHENTICATE, "Bearer").build();
        }
        if (!isJson(request.getContentType())) {
            return empty(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        }
        if (request.getContentLengthLong() > maxBodyBytes) {
            return empty(HttpStatus.CONTENT_TOO_LARGE);
        }
        List<RecordsIngestion.Submission> batch;
        try {
            var body = request.getInputStream().readNBytes(maxBodyBytes + 1);
            if (body.length > maxBodyBytes) {
                return empty(HttpStatus.CONTENT_TOO_LARGE);
            }
            var envelope = json.readTree(body);
            if (envelope == null || !envelope.isObject() || envelope.size() != 1) {
                return empty(HttpStatus.BAD_REQUEST);
            }
            var items = envelope.path("records");
            if (!items.isArray() || items.isEmpty()) {
                return empty(HttpStatus.BAD_REQUEST);
            }
            if (items.size() > maxRecords) {
                return empty(HttpStatus.CONTENT_TOO_LARGE);
            }
            batch = new ArrayList<RecordsIngestion.Submission>(items.size());
            for (var item : items) {
                var submission = RecordsIngestion.Submission.from(item);
                if (submission == null) {
                    // With no usable ID there is no contractual, correlatable result for the item.
                    return empty(HttpStatus.BAD_REQUEST);
                }
                batch.add(submission);
            }
        } catch (IOException | JacksonException failure) {
            return empty(HttpStatus.BAD_REQUEST);
        }
        var result = ingestion.receive(batch, stationToken);
        return switch (result.decision()) {
            case ALLOWED -> ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(result.results());
            case DENIED, INVALID_SCOPE -> empty(HttpStatus.FORBIDDEN);
            case UNAVAILABLE -> empty(HttpStatus.SERVICE_UNAVAILABLE);
        };
    }

    private static ResponseEntity<RecordsIngestion.Results> empty(HttpStatus status) {
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).build();
    }

    private static String stationToken(HttpServletRequest request) {
        var headers = request.getHeaders(HttpHeaders.AUTHORIZATION);
        if (!headers.hasMoreElements()) {
            return null;
        }
        var value = headers.nextElement();
        if (headers.hasMoreElements() || value.length() != BEARER_PREFIX.length() + 64
                || !value.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            return null;
        }
        var token = value.substring(BEARER_PREFIX.length());
        return STATION_TOKEN.matcher(token).matches() ? token : null;
    }

    private static boolean isJson(String contentType) {
        if (contentType == null) {
            return false;
        }
        try {
            return MediaType.APPLICATION_JSON.equalsTypeAndSubtype(MediaType.parseMediaType(contentType));
        } catch (IllegalArgumentException failure) {
            return false;
        }
    }
}
