package com.lextr.semanticlayer.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener;
import org.springframework.test.context.support.DirtiesContextBeforeModesTestExecutionListener;
import org.springframework.test.context.support.DirtiesContextTestExecutionListener;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringJUnitWebConfig(classes = {
        com.lextr.semanticlayer.SemanticLayerApplication.class,
        FilterLookupReadWireThroughTest.FilterLookupReadWireThroughTestConfiguration.class
})
@TestExecutionListeners(
        listeners = {
                DependencyInjectionTestExecutionListener.class,
                DirtiesContextBeforeModesTestExecutionListener.class,
                DirtiesContextTestExecutionListener.class
        },
        mergeMode = TestExecutionListeners.MergeMode.REPLACE_DEFAULTS
)
class FilterLookupReadWireThroughTest {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private ObjectRegistrationWireThroughTest.RecordingNamedParameterJdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetTemplate() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        jdbcTemplate.reset();
    }

    @Test
    void honorsFilterLookupListContractEndToEnd() throws Exception {
        jdbcTemplate.setResponses(List.of(
                List.of(policyPresetRow()),
                List.of(filterLookupRow("LEDGER_SCOPE", 45)),
                List.of(valueCountRow(2L))
        ));

        mockMvc.perform(get("/api/filter-lookups")
                        .queryParam("client_id", "client-a")
                        .queryParam("governance_status_cd", "REVIEW")
                        .queryParam("health_status_cd", "PENDING")
                        .queryParam("lifecycle_status_cd", "ACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].lookup_cd").value("LEDGER_SCOPE"))
                .andExpect(jsonPath("$[0].effective_review_period_days").value(45))
                .andExpect(jsonPath("$[0].effective_review_period_source_cd").value("LOOKUP_OVERRIDE"))
                .andExpect(jsonPath("$[0].health_status_cd").value("PENDING"))
                .andExpect(jsonPath("$[0].value_count").value(2));

        assertEquals(3, jdbcTemplate.recordedSqls().size());
        assertTrue(jdbcTemplate.recordedSqls().get(0).contains("FROM governance.policy_preset"));
        assertTrue(jdbcTemplate.recordedSqls().get(1).contains("FROM meta.semantic_filter_lookup"));
        assertTrue(jdbcTemplate.recordedSqls().get(2).contains("FROM meta.filter_lookup_value"));
        assertEquals("client-a", jdbcTemplate.recordedParameters().get(1).get("client_id"));
        assertEquals("REVIEW", jdbcTemplate.recordedParameters().get(1).get("governance_status_cd"));
        assertEquals("PENDING", jdbcTemplate.recordedParameters().get(1).get("health_status_cd"));
        assertEquals("ACTIVE", jdbcTemplate.recordedParameters().get(1).get("lifecycle_status_cd"));
    }

    @Test
    void honorsFilterLookupDetailContractEndToEnd() throws Exception {
        jdbcTemplate.setResponses(List.of(
                List.of(policyPresetRow()),
                List.of(filterLookupRow("LEDGER_SCOPE", null)),
                List.of(valueCountRow(0L))
        ));

        mockMvc.perform(get("/api/filter-lookups/{lookup_code}", "LEDGER_SCOPE")
                        .queryParam("client_id", "client-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lookup_cd").value("LEDGER_SCOPE"))
                .andExpect(jsonPath("$.effective_review_period_days").value(90))
                .andExpect(jsonPath("$.effective_review_period_source_cd").value("GOV_DEFAULT"))
                .andExpect(jsonPath("$.value_count").value(0));

        assertEquals(3, jdbcTemplate.recordedSqls().size());
        assertTrue(jdbcTemplate.recordedSqls().get(1).contains("lookup_cd = :lookup_cd"));
        assertEquals("client-a", jdbcTemplate.recordedParameters().get(1).get("client_id"));
        assertEquals("LEDGER_SCOPE", jdbcTemplate.recordedParameters().get(1).get("lookup_cd"));
    }

    @Test
    void returnsNotFoundEndToEndWhenLookupUnknown() throws Exception {
        jdbcTemplate.setResponses(List.of(
                List.of(policyPresetRow()),
                List.of()
        ));

        mockMvc.perform(get("/api/filter-lookups/{lookup_code}", "UNKNOWN_LOOKUP")
                        .queryParam("client_id", "client-a"))
                .andExpect(status().isNotFound());

        assertEquals(2, jdbcTemplate.recordedSqls().size());
        assertTrue(jdbcTemplate.recordedSqls().get(1).contains("lookup_cd = :lookup_cd"));
        assertEquals("client-a", jdbcTemplate.recordedParameters().get(1).get("client_id"));
        assertEquals("UNKNOWN_LOOKUP", jdbcTemplate.recordedParameters().get(1).get("lookup_cd"));
    }

    private static Map<String, Object> policyPresetRow() {
        Map<String, Object> row = new HashMap<>();
        row.put("policy_cd", "GOV-FL-001");
        row.put("policy_nm", "Minimum review frequency (floor, days)");
        row.put("policy_scope_cd", "FILTER_LOOKUP");
        row.put("default_value_txt", "90");
        row.put("data_type_cd", "INTEGER");
        row.put("is_overrideable_flg", true);
        row.put("override_requires_approval_flg", true);
        row.put("effective_from_dt", LocalDate.parse("2026-01-01"));
        row.put("effective_to_dt", null);
        row.put("approved_by", "governance-owner");
        row.put("approved_ts", OffsetDateTime.parse("2026-01-01T00:00:00Z"));
        row.put("created_ts", OffsetDateTime.parse("2026-01-01T00:00:00Z"));
        row.put("created_by", "governance-owner");
        return row;
    }

    private static Map<String, Object> filterLookupRow(String lookupCode, Integer overrideDays) {
        Map<String, Object> row = new HashMap<>();
        row.put("id", 101L);
        row.put("lookup_cd", lookupCode);
        row.put("construction_type_cd", "MANUAL_LIST");
        row.put("manual_subtype_cd", "HAND_TYPED");
        row.put("filter_obj", "meta.gl_balance");
        row.put("filter_condition_txt", "ledger_status = 'ACTIVE'");
        row.put("filter_attr_cd", "ledger_id");
        row.put("validation_obj", "meta.ledger");
        row.put("validation_attr_cd", "ledger_id");
        row.put("suggested_target_attr_cd", "ledger_id");
        row.put("execution_strategy_cd", "IN_LIST");
        row.put("max_input_set_size", 500);
        row.put("max_output_rows", 10000);
        row.put("cache_ttl_min", 60);
        row.put("review_period_days_override", overrideDays);
        row.put("rules_eligible_flg", true);
        row.put("qs_eligible_flg", true);
        row.put("ai_eligible_flg", false);
        row.put("replicate_to_ch_flg", false);
        row.put("description_txt", "Ledger scope values");
        row.put("client_id", "client-a");
        row.put("governance_status_cd", "REVIEW");
        row.put("health_status_cd", "PENDING");
        row.put("last_certified_ts", OffsetDateTime.parse("2026-06-16T10:15:30+05:30"));
        row.put("last_certified_by", "certifier");
        row.put("next_review_due_dt", LocalDate.parse("2026-08-02"));
        row.put("lifecycle_status_cd", "ACTIVE");
        row.put("created_ts", OffsetDateTime.parse("2026-06-16T10:15:30+05:30"));
        row.put("created_by", "producer");
        row.put("updated_ts", OffsetDateTime.parse("2026-06-17T10:15:30+05:30"));
        row.put("updated_by", "platform");
        return row;
    }

    private static Map<String, Object> valueCountRow(long valueCount) {
        Map<String, Object> row = new HashMap<>();
        row.put("lookup_cd", "LEDGER_SCOPE");
        row.put("client_id", "client-a");
        row.put("value_count", valueCount);
        return row;
    }

    @TestConfiguration
    static class FilterLookupReadWireThroughTestConfiguration {

        @Bean
        ObjectRegistrationWireThroughTest.RecordingNamedParameterJdbcTemplate recordingNamedParameterJdbcTemplate() {
            return new ObjectRegistrationWireThroughTest.RecordingNamedParameterJdbcTemplate();
        }
    }
}
