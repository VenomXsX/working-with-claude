package com.marlowefinch.ops;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** The JSON API through MockMvc, plus one real HTTP call to observe the error path. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("demo")
class DashboardControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private TestRestTemplate http;

    @Test
    void healthReportsUpAndTheFixedToday() throws Exception {
        mvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.today").value("2026-09-21"));
    }

    @Test
    void kpisDefaultToTheLast30DaysEndingToday() throws Exception {
        mvc.perform(get("/api/kpis"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.from").value("2026-08-22"))
                .andExpect(jsonPath("$.to").value("2026-09-21"))
                .andExpect(jsonPath("$.onTimeRate").value(0.937))
                .andExpect(jsonPath("$.openTickets").value(114))
                .andExpect(jsonPath("$.revenue").value(360095.5))
                .andExpect(jsonPath("$.orders").value(624));
    }

    @Test
    void kpisAcceptAnExplicitRange() throws Exception {
        mvc.perform(get("/api/kpis").param("from", "2026-07-01").param("to", "2026-07-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.from").value("2026-07-01"))
                .andExpect(jsonPath("$.to").value("2026-07-31"))
                .andExpect(jsonPath("$.orders").value(679))
                .andExpect(jsonPath("$.revenue").value(480209.5));
    }

    @Test
    void onTimeReturnsOneRowPerCarrier() throws Exception {
        mvc.perform(get("/api/deliveries/on-time"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(4)))
                .andExpect(jsonPath("$[1].carrier").value("Kessler Logistics"))
                .andExpect(jsonPath("$[1].delivered").value(265))
                .andExpect(jsonPath("$[1].onTime").value(238))
                .andExpect(jsonPath("$[1].rate").value(0.8981));
    }

    @Test
    void lateReturnsOrderCarrierDatesAndDaysLateAndRespectsTheLimit() throws Exception {
        mvc.perform(get("/api/deliveries/late").param("from", "2026-09-14").param("to", "2026-09-21").param("limit", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].orderRef").isString())
                .andExpect(jsonPath("$[0].carrier").isString())
                .andExpect(jsonPath("$[0].promisedDate").isString())
                .andExpect(jsonPath("$[0].deliveredDate").isString())
                .andExpect(jsonPath("$[0].daysLate").isNumber());
    }

    @Test
    void lateWithFromAfterToIsRejectedWith400InsteadOfAnEmptyList() throws Exception {
        mvc.perform(get("/api/deliveries/late").param("from", "2026-09-21").param("to", "2026-09-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasSize(1)))
                .andExpect(jsonPath("$.errors[0]").value("from must be on or before to"));
    }

    @Test
    void ticketsByCategoryReturnsOpenAndTotalPerCategory() throws Exception {
        mvc.perform(get("/api/tickets/by-category"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(5)))
                .andExpect(jsonPath("$[0].category").value("Delivery delay"))
                .andExpect(jsonPath("$[0].open").value(41))
                .andExpect(jsonPath("$[0].total").value(90));
    }

    @Test
    void vendorsIncludeDaysUntilContractEndAndTheNoticeWindowFlag() throws Exception {
        mvc.perform(get("/api/vendors"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(8)))
                .andExpect(jsonPath("$[0].name").value("Volta Parts GmbH"))
                .andExpect(jsonPath("$[0].contractEnd").value("2026-10-15"))
                .andExpect(jsonPath("$[0].noticeDays").value(30))
                .andExpect(jsonPath("$[0].daysUntilContractEnd").value(24))
                .andExpect(jsonPath("$[0].inNoticeWindow").value(true))
                .andExpect(jsonPath("$[7].inNoticeWindow").value(false));
    }

    private static final String BAD_FROM = "from must be an ISO date (YYYY-MM-DD)";
    private static final String BAD_TO = "to must be an ISO date (YYYY-MM-DD)";
    private static final String BAD_ORDER = "from must be on or before to";
    private static final String BAD_SPAN = "the range may span at most 366 days";
    private static final String BAD_LIMIT = "limit must be an integer between 1 and 500";

    private static final String[] RANGE_ENDPOINTS = {
        "/api/kpis", "/api/deliveries/on-time", "/api/deliveries/late", "/api/tickets/by-category"
    };

    private void expectSingleError(String url, String from, String to, String limit, String message) throws Exception {
        var req = get(url);
        if (from != null) req = req.param("from", from);
        if (to != null) req = req.param("to", to);
        if (limit != null) req = req.param("limit", limit);
        mvc.perform(req)
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.errors", hasSize(1)))
                .andExpect(jsonPath("$.errors[0]").value(message))
                .andExpect(jsonPath("$.length()").value(1));
    }

    /** Was malformedFromCurrentlyProducesA5xx: a malformed date used to be a 500, now a 400 (TODO-232). */
    @Test
    void malformedFromUsedToProduceA5xxAndNowProducesA400WithAnErrorsList() {
        ResponseEntity<String> response = http.getForEntity("/api/kpis?from=next-tuesday", String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).isEqualTo("{\"errors\":[\"" + BAD_FROM + "\"]}");
    }

    @Test
    void malformedFromReturns400OnEveryRangeEndpoint() throws Exception {
        for (String url : RANGE_ENDPOINTS) {
            expectSingleError(url, "next-tuesday", null, null, BAD_FROM);
        }
    }

    @Test
    void malformedToReturns400OnEveryRangeEndpoint() throws Exception {
        for (String url : RANGE_ENDPOINTS) {
            expectSingleError(url, null, "2026-13-45", null, BAD_TO);
        }
    }

    @Test
    void fromAfterToReturns400OnEveryRangeEndpoint() throws Exception {
        for (String url : RANGE_ENDPOINTS) {
            expectSingleError(url, "2026-09-21", "2026-09-01", null, BAD_ORDER);
        }
    }

    @Test
    void aRangeOf366DaysIsAccepted() throws Exception {
        for (String url : RANGE_ENDPOINTS) {
            mvc.perform(get(url).param("from", "2025-09-20").param("to", "2026-09-21"))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void aRangeOf367DaysIsRejectedOnEveryRangeEndpoint() throws Exception {
        for (String url : RANGE_ENDPOINTS) {
            expectSingleError(url, "2025-09-19", "2026-09-21", null, BAD_SPAN);
        }
    }

    @Test
    void limitZeroAnd501AreRejected() throws Exception {
        expectSingleError("/api/deliveries/late", null, null, "0", BAD_LIMIT);
        expectSingleError("/api/deliveries/late", null, null, "501", BAD_LIMIT);
    }

    @Test
    void negativeLimitIsRejected() throws Exception {
        expectSingleError("/api/deliveries/late", null, null, "-1", BAD_LIMIT);
    }

    @Test
    void limitOneAnd500AreAccepted() throws Exception {
        mvc.perform(get("/api/deliveries/late").param("limit", "1")).andExpect(status().isOk());
        mvc.perform(get("/api/deliveries/late").param("limit", "500")).andExpect(status().isOk());
    }

    @Test
    void nonNumericLimitIsRejected() throws Exception {
        expectSingleError("/api/deliveries/late", null, null, "abc", BAD_LIMIT);
        expectSingleError("/api/deliveries/late", null, null, "2.5", BAD_LIMIT);
    }

    @Test
    void severalProblemsInOneRequestProduceSeveralErrors() throws Exception {
        mvc.perform(get("/api/deliveries/late").param("from", "x").param("to", "y").param("limit", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasSize(3)))
                .andExpect(jsonPath("$.errors[0]").value(BAD_TO))
                .andExpect(jsonPath("$.errors[1]").value(BAD_FROM))
                .andExpect(jsonPath("$.errors[2]").value(BAD_LIMIT));
    }

    @Test
    void invalidDateAndInvalidLimitAreReportedTogether() throws Exception {
        mvc.perform(get("/api/deliveries/late").param("from", "2026-09-21").param("to", "2026-09-01").param("limit", "999"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasSize(2)))
                .andExpect(jsonPath("$.errors[0]").value(BAD_ORDER))
                .andExpect(jsonPath("$.errors[1]").value(BAD_LIMIT));
    }

    @Test
    void aValidRequestWithNoParametersStillUsesTheDefaults() throws Exception {
        mvc.perform(get("/api/deliveries/late"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(org.hamcrest.Matchers.lessThanOrEqualTo(20)));
        mvc.perform(get("/api/kpis").param("limit", "abc"))
                .andExpect(status().isOk());
    }

    @Test
    void blankParametersFallBackToDefaults() throws Exception {
        mvc.perform(get("/api/kpis").param("from", "").param("to", ""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.from").value("2026-08-22"))
                .andExpect(jsonPath("$.to").value("2026-09-21"));
    }

    @Test
    void vendorsAndHealthAreNotValidated() throws Exception {
        mvc.perform(get("/api/vendors").param("from", "garbage")).andExpect(status().isOk());
        mvc.perform(get("/api/health").param("from", "garbage")).andExpect(status().isOk());
    }
}
