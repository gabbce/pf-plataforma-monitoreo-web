package ar.edu.utn.frsf.pfc.records.web;

import java.time.Instant;
import java.util.List;

import jakarta.servlet.http.Cookie;

import ar.edu.utn.frsf.pfc.records.station.WebMachineAccessClient;
import ar.edu.utn.frsf.pfc.records.storage.MeasurementReads;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static ar.edu.utn.frsf.pfc.records.station.WebMachineAccessClient.Decision.ACCESS_DENIED;
import static ar.edu.utn.frsf.pfc.records.station.WebMachineAccessClient.Decision.ALLOWED;
import static ar.edu.utn.frsf.pfc.records.station.WebMachineAccessClient.Decision.SESSION_REQUIRED;
import static ar.edu.utn.frsf.pfc.records.station.WebMachineAccessClient.Decision.UNAVAILABLE;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = LatestMeasurementsController.class)
class LatestMeasurementsControllerTests {

    private static final String CHASSIS = "000123";
    private static final String PATH = "/api/v1/machines/" + CHASSIS + "/measurements/latest";
    private static final String TOKEN = "a".repeat(43);
    private static final String COOKIE_NAME = "PFC_SESSION";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private WebMachineAccessClient access;

    @MockitoBean
    private MeasurementReads reads;

    @Test
    void onlyReadsAfterAuthorizationAndKeepsTheTextualChassis() throws Exception {
        var asOf = Instant.parse("2026-01-15T12:05:00.702321Z");
        when(access.check(CHASSIS, TOKEN)).thenReturn(ALLOWED);
        when(reads.latest(CHASSIS)).thenReturn(new MeasurementReads.Latest(asOf, List.of()));

        mvc.perform(get(PATH).cookie(new Cookie(COOKIE_NAME, TOKEN)))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.asOf").value(asOf.toString()))
                .andExpect(jsonPath("$.items").isEmpty());

        var ordered = inOrder(access, reads);
        ordered.verify(access).check(CHASSIS, TOKEN);
        ordered.verify(reads).latest(CHASSIS);
    }

    @Test
    void failsClosedForMissingMalformedOrDuplicateCookiesBeforeContactingManagement() throws Exception {
        for (var cookies : List.of(new Cookie[] {new Cookie("other", TOKEN)},
                new Cookie[] {new Cookie(COOKIE_NAME, "invalid")},
                new Cookie[] {new Cookie(COOKIE_NAME, TOKEN), new Cookie(COOKIE_NAME, TOKEN)})) {
            mvc.perform(get(PATH).cookie(cookies))
                    .andExpect(status().isUnauthorized())
                    .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Session realm=\"PFC\""))
                    .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                    .andExpect(jsonPath("$.code").value("SESSION_REQUIRED"));
        }
        mvc.perform(get(PATH))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(access, reads);
    }

    @Test
    void distinguishesSessionDenialAndUnavailableManagementWithoutReadingMeasurements() throws Exception {
        when(access.check(CHASSIS, TOKEN)).thenReturn(SESSION_REQUIRED, ACCESS_DENIED, UNAVAILABLE);

        mvc.perform(get(PATH).cookie(new Cookie(COOKIE_NAME, TOKEN)))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Session realm=\"PFC\""))
                .andExpect(jsonPath("$.code").value("SESSION_REQUIRED"));
        mvc.perform(get(PATH).cookie(new Cookie(COOKIE_NAME, TOKEN)))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.WWW_AUTHENTICATE))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        mvc.perform(get(PATH).cookie(new Cookie(COOKIE_NAME, TOKEN)))
                .andExpect(status().isInternalServerError())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(header().doesNotExist(HttpHeaders.WWW_AUTHENTICATE))
                .andExpect(jsonPath("$.code").value("SERVER_ERROR"));

        verifyNoInteractions(reads);
    }

    @Test
    void returnsContractErrorWhenStorageFailsWithoutLeakingTheCause() throws Exception {
        when(access.check(CHASSIS, TOKEN)).thenReturn(ALLOWED);
        when(reads.latest(CHASSIS)).thenThrow(new IllegalStateException("internal database detail"));

        mvc.perform(get(PATH).cookie(new Cookie(COOKIE_NAME, TOKEN)))
                .andExpect(status().isInternalServerError())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("about:blank"))
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.code").value("SERVER_ERROR"))
                .andExpect(jsonPath("$.detail").doesNotExist());
    }
}
