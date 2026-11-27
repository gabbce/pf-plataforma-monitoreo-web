package ar.edu.utn.frsf.pfc.records.ingestion;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;

import ar.edu.utn.frsf.pfc.records.station.DataContextClient;
import ar.edu.utn.frsf.pfc.records.station.DataContextClient.Context;
import ar.edu.utn.frsf.pfc.records.station.DataContextClient.TimeValidity;
import ar.edu.utn.frsf.pfc.records.station.StationAuthorizationClient;
import ar.edu.utn.frsf.pfc.records.station.StationAuthorizationClient.Decision;
import ar.edu.utn.frsf.pfc.records.storage.DataRecord;
import ar.edu.utn.frsf.pfc.records.storage.StoredDataRecords;
import ar.edu.utn.frsf.pfc.records.storage.StoredDataRecords.StorageClass;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = RecordsIngestionController.class,
        properties = {"pfc.records.ingestion.max-body-bytes=8192", "pfc.records.ingestion.max-records=8"})
@Import(RecordsIngestion.class)
class RecordsIngestionControllerTests {

    private static final String PATH = "/integration/v1/records";
    private static final String TOKEN = "a".repeat(64);
    private static final String INSTANCE = "000123";
    private static final String PRODUCER = "station-1";
    private static final String RPM = "engine.rpm";
    private static final Instant OCCURRED_AT = Instant.parse("2026-04-03T12:00:00.123456789Z");
    private static final Instant PRIOR_RECEIPT = Instant.parse("2026-04-03T12:00:05Z");
    private static final String PRECISE_VALUE = "123.4567890123456789012345678901234";
    private static final Context RPM_CONTEXT = new Context("embolsadora", RPM, "engine", "NUMBER", "rpm", "rpm-b");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JsonMapper json;

    @MockitoBean
    private StationAuthorizationClient authorization;

    @MockitoBean
    private DataContextClient catalog;

    @MockitoBean
    private StoredDataRecords storage;

    @Test
    void requiresASingleValidBearerBeforeReadingTheBody() throws Exception {
        mvc.perform(post(PATH).content("not a JSON payload"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"));
        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN, "Bearer " + TOKEN).content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Basic " + TOKEN).content("{}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(authorization, catalog, storage);
    }

    @Test
    void rejectsMalformedOrUncorrelatableBatchesBeforeContactOrStorage() throws Exception {
        for (var body : List.of(
                "{}",
                "{\"records\":[]}",
                "{\"records\":[],\"records\":[]}",
                batch(record("valid"), "{\"instanceId\":\"000123\"}"),
                batch(record("valid"), "{\"recordId\":123}"),
                batch(record("valid"), "{\"recordId\":\" \"}"),
                batch(record("valid"), "null"),
                batch(record("valid")) + "{}")) {
            mvc.perform(request(body)).andExpect(status().isBadRequest());
        }
        verifyNoInteractions(authorization, catalog, storage);
    }

    @Test
    void rejectsNonJsonAndEnforcesBothLimitsWithoutAuthorizingTheBatch() throws Exception {
        mvc.perform(post(PATH).header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN)
                .contentType(MediaType.TEXT_PLAIN).content("hello"))
                .andExpect(status().isUnsupportedMediaType());
        mvc.perform(request(" ".repeat(8193))).andExpect(status().isContentTooLarge());
        var tooManyRecords = IntStream.range(0, 9).mapToObj(index -> record("id-" + index)).toArray(String[]::new);
        mvc.perform(request(batch(tooManyRecords))).andExpect(status().isContentTooLarge());
        verifyNoInteractions(authorization, catalog, storage);
    }

    @Test
    void deniesAnUnauthorizedScopeAndChecksEachCandidateOnlyOnce() throws Exception {
        when(authorization.checkAndRecordContact(INSTANCE, PRODUCER, TOKEN)).thenReturn(Decision.DENIED);

        mvc.perform(request(batch(record("first"), record("second")))).andExpect(status().isForbidden());

        verify(authorization).checkAndRecordContact(INSTANCE, PRODUCER, TOKEN);
        verifyNoMoreInteractions(authorization);
        verifyNoInteractions(catalog, storage);
    }

    @Test
    void rejectsAnOversizedScopeWithoutBlockingAValidScopeLaterInTheBatch() throws Exception {
        var oversizedScope = "x".repeat(4096);
        when(authorization.checkAndRecordContact(oversizedScope, PRODUCER, TOKEN))
                .thenReturn(Decision.INVALID_SCOPE);
        allowContact();
        when(storage.recognize(any(DataRecord.class)))
                .thenReturn(stored(StoredDataRecords.Status.ALREADY_STORED, StorageClass.OPERATIONAL));
        var submitted = batch(record("large-scope").replace(INSTANCE, oversizedScope), record("valid"));

        var response = mvc.perform(request(submitted)).andExpect(status().isOk()).andReturn().getResponse();
        assertThat(json.readTree(response.getContentAsString())).isEqualTo(json.readTree("""
                {"results":[
                  {"recordId":"large-scope","status":"REJECTED","reason":"INVALID_RECORD"},
                  {"recordId":"valid","status":"ALREADY_STORED","storageClass":"OPERATIONAL"}
                ]}
                """));
        verify(storage).recognize(argThat(data -> data.recordId().equals("valid")));
        verifyNoMoreInteractions(storage);
        verifyNoInteractions(catalog);
    }

    @Test
    void doesNotTreatIndeterminateAuthorizationAsDenialOrProcessAnyRecord() throws Exception {
        when(authorization.checkAndRecordContact("000124", PRODUCER, TOKEN)).thenReturn(Decision.DENIED);
        when(authorization.checkAndRecordContact(INSTANCE, PRODUCER, TOKEN)).thenReturn(Decision.UNAVAILABLE);

        mvc.perform(request(batch(record("foreign").replace(INSTANCE, "000124"), record("retry"))))
                .andExpect(status().isServiceUnavailable());

        var order = inOrder(authorization);
        order.verify(authorization).checkAndRecordContact("000124", PRODUCER, TOKEN);
        order.verify(authorization).checkAndRecordContact(INSTANCE, PRODUCER, TOKEN);
        verifyNoInteractions(catalog, storage);
    }

    @Test
    void confirmsOnlyDurableRecordsAndReturnsMixedResultsInSubmissionOrder() throws Exception {
        when(authorization.checkAndRecordContact("000124", PRODUCER, TOKEN)).thenReturn(Decision.DENIED);
        allowContact();
        when(storage.recognize(any(DataRecord.class))).thenAnswer(invocation -> {
            DataRecord record = invocation.getArgument(0);
            return switch (record.recordId()) {
                case "duplicate" -> stored(StoredDataRecords.Status.ALREADY_STORED, StorageClass.OPERATIONAL);
                case "conflict" -> stored(StoredDataRecords.Status.CONFLICT, null);
                default -> stored(StoredDataRecords.Status.NOT_FOUND, null);
            };
        });
        when(catalog.resolve(INSTANCE, RPM, TimeValidity.VALID, OCCURRED_AT)).thenReturn(resolved(RPM_CONTEXT));
        var reviewContext = new Context("embolsadora", RPM, "engine", "NUMBER", "rpm", null);
        when(catalog.resolve(INSTANCE, RPM, TimeValidity.INVALID, null)).thenReturn(resolved(reviewContext));
        when(storage.store(any(DataRecord.class), eq(RPM_CONTEXT)))
                .thenReturn(stored(StoredDataRecords.Status.STORED, StorageClass.OPERATIONAL));
        when(storage.store(any(DataRecord.class), eq(reviewContext)))
                .thenReturn(stored(StoredDataRecords.Status.STORED, StorageClass.REVIEW));
        when(catalog.resolve(INSTANCE, "unregistered", TimeValidity.VALID, OCCURRED_AT))
                .thenReturn(new DataContextClient.Result(DataContextClient.Status.NOT_FOUND, null));
        var review = record("review").replace("\"" + OCCURRED_AT + "\"", "null").replace("\"timeValidity\":\"VALID\"",
                "\"timeValidity\":\"INVALID\"");
        var submitted = batch(record("foreign").replace(INSTANCE, "000124"), record("new"), review,
                record("duplicate"), record("conflict"), record("unknown").replace(RPM, "unregistered"),
                record("wrong-kind").replace("\"DATA\"", "\"STATE\""),
                record("unexpected").replace("\"unit\"", "\"extra\":true,\"unit\""));

        var response = mvc.perform(request(submitted)).andExpect(status().isOk()).andReturn().getResponse();
        assertThat(json.readTree(response.getContentAsString())).isEqualTo(json.readTree("""
                {"results":[
                  {"recordId":"foreign","status":"REJECTED","reason":"IDENTITY_OUT_OF_SCOPE"},
                  {"recordId":"new","status":"ACCEPTED","storageClass":"OPERATIONAL"},
                  {"recordId":"review","status":"ACCEPTED","storageClass":"REVIEW"},
                  {"recordId":"duplicate","status":"ALREADY_STORED","storageClass":"OPERATIONAL"},
                  {"recordId":"conflict","status":"REJECTED","reason":"RECORD_ID_CONFLICT"},
                  {"recordId":"unknown","status":"REJECTED","reason":"DATA_CONTEXT_NOT_FOUND"},
                  {"recordId":"wrong-kind","status":"REJECTED","reason":"UNSUPPORTED_KIND"},
                  {"recordId":"unexpected","status":"REJECTED","reason":"INVALID_RECORD"}
                ]}
                """));
        var order = inOrder(authorization, storage, catalog);
        order.verify(authorization).checkAndRecordContact("000124", PRODUCER, TOKEN);
        order.verify(authorization).checkAndRecordContact(INSTANCE, PRODUCER, TOKEN);
        order.verify(storage).recognize(argThat(data -> data.recordId().equals("new")));
        order.verify(catalog).resolve(INSTANCE, RPM, TimeValidity.VALID, OCCURRED_AT);
        order.verify(storage).store(argThat(data -> data.instanceId().equals(INSTANCE)
                && data.payload().value().compareTo(new BigDecimal(PRECISE_VALUE)) == 0
                && data.appliedConfigVersion().equals("cfg-original")), eq(RPM_CONTEXT));
        verifyNoMoreInteractions(authorization);
    }

    @Test
    void confirmsAReviewReplayWithoutCatalogAndRetriesOnlyTheUnavailableRecords() throws Exception {
        allowContact();
        when(storage.recognize(any(DataRecord.class))).thenAnswer(invocation -> {
            DataRecord record = invocation.getArgument(0);
            return switch (record.recordId()) {
                case "previous-review" -> stored(StoredDataRecords.Status.ALREADY_STORED, StorageClass.REVIEW);
                case "journal-failure" -> stored(StoredDataRecords.Status.RETRY, null);
                default -> stored(StoredDataRecords.Status.NOT_FOUND, null);
            };
        });
        when(catalog.resolve(INSTANCE, RPM, TimeValidity.VALID, OCCURRED_AT))
                .thenReturn(new DataContextClient.Result(DataContextClient.Status.UNAVAILABLE, null));
        var previousReview = record("previous-review").replace("\"timeValidity\":\"VALID\"",
                "\"timeValidity\":\"INVALID\"");

        var response = mvc.perform(request(batch(previousReview, record("new"), record("journal-failure"))))
                .andExpect(status().isOk()).andReturn().getResponse();
        assertThat(json.readTree(response.getContentAsString())).isEqualTo(json.readTree("""
                {"results":[
                  {"recordId":"previous-review","status":"ALREADY_STORED","storageClass":"REVIEW"},
                  {"recordId":"new","status":"RETRY","reason":"CATALOG_UNAVAILABLE"},
                  {"recordId":"journal-failure","status":"RETRY","reason":"STORAGE_UNAVAILABLE"}
                ]}
                """));
        verify(catalog).resolve(INSTANCE, RPM, TimeValidity.VALID, OCCURRED_AT);
        verifyNoMoreInteractions(catalog);
    }

    @Test
    void distinguishesInvalidDataFromCatalogMismatchAndRetainsValidZero() throws Exception {
        allowContact();
        when(storage.recognize(any(DataRecord.class))).thenReturn(stored(StoredDataRecords.Status.NOT_FOUND, null));
        when(catalog.resolve(INSTANCE, RPM, TimeValidity.VALID, OCCURRED_AT)).thenReturn(resolved(RPM_CONTEXT));
        when(storage.store(any(DataRecord.class), eq(RPM_CONTEXT)))
                .thenReturn(stored(StoredDataRecords.Status.STORED, StorageClass.OPERATIONAL));
        var zero = record("zero").replace(PRECISE_VALUE, "0");
        var invalidZero = zero.replace("\"zero\"", "\"invalid-zero\"")
                .replace("\"quality\":\"VALID\"", "\"quality\":\"MISSING\"");
        var noTime = record("no-time").replace("\"" + OCCURRED_AT + "\"", "null");
        var wrongUnit = record("wrong-unit").replace("\"unit\":\"rpm\"", "\"unit\":\"h\"");
        var response = mvc.perform(request(batch(invalidZero, noTime, wrongUnit, zero)))
                .andExpect(status().isOk()).andReturn().getResponse();

        assertThat(json.readTree(response.getContentAsString())).isEqualTo(json.readTree("""
                {"results":[
                  {"recordId":"invalid-zero","status":"REJECTED","reason":"INVALID_RECORD"},
                  {"recordId":"no-time","status":"REJECTED","reason":"INVALID_RECORD"},
                  {"recordId":"wrong-unit","status":"REJECTED","reason":"DATA_CONTEXT_MISMATCH"},
                  {"recordId":"zero","status":"ACCEPTED","storageClass":"OPERATIONAL"}
                ]}
                """));
        verify(storage).store(argThat(data -> data.recordId().equals("zero")
                && data.payload().value().signum() == 0), eq(RPM_CONTEXT));
    }

    private void allowContact() {
        when(authorization.checkAndRecordContact(INSTANCE, PRODUCER, TOKEN)).thenReturn(Decision.ALLOWED);
    }

    private static StoredDataRecords.Result stored(StoredDataRecords.Status status, StorageClass storageClass) {
        return new StoredDataRecords.Result(status, storageClass, storageClass == null ? null : PRIOR_RECEIPT);
    }

    private static DataContextClient.Result resolved(Context context) {
        return new DataContextClient.Result(DataContextClient.Status.RESOLVED, context);
    }

    private static String record(String id) {
        return """
                {"schemaVersion":1,"recordId":"%s","instanceId":"000123","producerId":"station-1",
                 "sequence":1,"kind":"DATA","definitionId":"engine.rpm","subsystemId":"engine",
                 "occurredAt":"%s","timeValidity":"VALID","appliedConfigVersion":"cfg-original",
                 "payload":{"value":%s,"unit":"rpm","quality":"VALID"}}
                """.formatted(id, OCCURRED_AT, PRECISE_VALUE);
    }

    private static String batch(String... records) {
        return "{\"records\":[" + String.join(",", records) + "]}";
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request(String body) {
        return post(PATH).header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN)
                .contentType(MediaType.APPLICATION_JSON).content(body);
    }
}
