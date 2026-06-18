package com.lextr.semanticlayer.api;

import com.lextr.semanticlayer.dto.FilterLookupPolicyDecisionDto;
import com.lextr.semanticlayer.dto.FilterLookupPolicyRequestDto;
import com.lextr.semanticlayer.dto.FilterLookupPreviewPolicyRequestDto;
import com.lextr.semanticlayer.service.FilterLookupPolicyClient;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringJUnitWebConfig(classes = {
        com.lextr.semanticlayer.SemanticLayerApplication.class,
        FilterLookupPreviewWireThroughTest.FilterLookupPreviewWireThroughTestConfiguration.class
})
@TestExecutionListeners(
        listeners = {
                DependencyInjectionTestExecutionListener.class,
                DirtiesContextBeforeModesTestExecutionListener.class,
                DirtiesContextTestExecutionListener.class
        },
        mergeMode = TestExecutionListeners.MergeMode.REPLACE_DEFAULTS
)
class FilterLookupPreviewWireThroughTest {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private ObjectRegistrationWireThroughTest.RecordingNamedParameterJdbcTemplate jdbcTemplate;

    @Autowired
    private RecordingFilterLookupPolicyClient filterLookupPolicyClient;

    @BeforeEach
    void resetDependencies() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        jdbcTemplate.reset();
        filterLookupPolicyClient.allowPreview();
    }

    @Test
    void honorsManualPreviewContractEndToEnd() throws Exception {
        OffsetDateTime logTimestamp = OffsetDateTime.parse("2026-06-18T04:45:30Z");
        jdbcTemplate.setResponses(List.of(
                List.of(filterLookupRow("LEDGER_SCOPE", "MANUAL_LIST", "IN_LIST", 10_000)),
                List.of(manualPreviewValueRow("LEDGER_SCOPE", "LEDGER_100")),
                List.of(executionLogRow(501L, "LEDGER_SCOPE", "preview-user", logTimestamp, 1, "IN_LIST", "SUCCESS", null))
        ));

        mockMvc.perform(post("/api/filter-lookups/preview")
                        .contentType("application/json")
                        .content("""
                                {
                                  "client_id": "client-a",
                                  "executed_by": "preview-user",
                                  "lookup_codes": ["LEDGER_SCOPE"]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].lookup_cd").value("LEDGER_SCOPE"))
                .andExpect(jsonPath("$[0].construction_type_cd").value("MANUAL_LIST"))
                .andExpect(jsonPath("$[0].execution_strategy_used_cd").value("IN_LIST"))
                .andExpect(jsonPath("$[0].phase1_row_count").value(1))
                .andExpect(jsonPath("$[0].result_status_cd").value("SUCCESS"))
                .andExpect(jsonPath("$[0].values[0].value_cd").value("LEDGER_100"))
                .andExpect(jsonPath("$[0].values[0].workflow_ref").value("WKFL-100"));

        assertEquals(3, jdbcTemplate.recordedSqls().size());
        assertTrue(jdbcTemplate.recordedSqls().get(0).contains("FROM meta.semantic_filter_lookup"));
        assertTrue(jdbcTemplate.recordedSqls().get(1).contains("FROM meta.filter_lookup_value flv"));
        assertTrue(jdbcTemplate.recordedSqls().get(2).contains("INSERT INTO meta.filter_lookup_exec_log"));
        assertEquals("client-a", jdbcTemplate.recordedParameters().get(0).get("client_id"));
        assertEquals("LEDGER_SCOPE", jdbcTemplate.recordedParameters().get(0).get("lookup_cd"));
        assertEquals("client-a", jdbcTemplate.recordedParameters().get(1).get("client_id"));
        assertEquals("LEDGER_SCOPE", jdbcTemplate.recordedParameters().get(1).get("lookup_cd"));
        assertEquals("preview-user", jdbcTemplate.recordedParameters().get(2).get("executed_by"));
        assertEquals(Integer.valueOf(1), jdbcTemplate.recordedParameters().get(2).get("phase1_row_count"));
        assertEquals("SUCCESS", jdbcTemplate.recordedParameters().get(2).get("result_status_cd"));

        assertEquals(1, filterLookupPolicyClient.previewRequests().size());
        FilterLookupPreviewPolicyRequestDto policyRequest = filterLookupPolicyClient.previewRequests().get(0);
        assertEquals("client-a", policyRequest.client_id());
        assertEquals("preview-user", policyRequest.executed_by());
        assertEquals("LEDGER_SCOPE", policyRequest.lookup_cd());
        assertEquals("MANUAL_LIST", policyRequest.construction_type_cd());
        assertEquals("IN_LIST", policyRequest.execution_strategy_cd());
    }

    @Test
    void honorsSqlPreviewContractEndToEnd() throws Exception {
        OffsetDateTime logTimestamp = OffsetDateTime.parse("2026-06-18T05:15:30Z");
        jdbcTemplate.setResponses(List.of(
                List.of(filterLookupRow("SQL_LEDGER_SCOPE", "SQL_QUERY", "DISTINCT", 250)),
                List.of(sqlPreviewValueRow("SQL_LEDGER_SCOPE", "LEDGER_900")),
                List.of(executionLogRow(601L, "SQL_LEDGER_SCOPE", "preview-user", logTimestamp, 1, "DISTINCT", "SUCCESS", null))
        ));

        mockMvc.perform(post("/api/filter-lookups/preview")
                        .contentType("application/json")
                        .content("""
                                {
                                  "client_id": "client-a",
                                  "executed_by": "preview-user",
                                  "lookup_codes": ["SQL_LEDGER_SCOPE"]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].lookup_cd").value("SQL_LEDGER_SCOPE"))
                .andExpect(jsonPath("$[0].construction_type_cd").value("SQL_QUERY"))
                .andExpect(jsonPath("$[0].execution_strategy_used_cd").value("DISTINCT"))
                .andExpect(jsonPath("$[0].phase1_row_count").value(1))
                .andExpect(jsonPath("$[0].result_status_cd").value("SUCCESS"))
                .andExpect(jsonPath("$[0].values[0].value_cd").value("LEDGER_900"))
                .andExpect(jsonPath("$[0].values[0].validated_flg").value(true));

        assertEquals(3, jdbcTemplate.recordedSqls().size());
        assertTrue(jdbcTemplate.recordedSqls().get(0).contains("FROM meta.semantic_filter_lookup"));
        assertTrue(jdbcTemplate.recordedSqls().get(1).contains("SELECT DISTINCT :lookup_cd AS lookup_cd"));
        assertTrue(jdbcTemplate.recordedSqls().get(1).contains("FROM meta.gl_balance"));
        assertTrue(jdbcTemplate.recordedSqls().get(1).contains("WHERE ledger_status = 'ACTIVE'"));
        assertTrue(jdbcTemplate.recordedSqls().get(2).contains("INSERT INTO meta.filter_lookup_exec_log"));
        assertEquals("SQL_LEDGER_SCOPE", jdbcTemplate.recordedParameters().get(0).get("lookup_cd"));
        assertEquals("SQL_LEDGER_SCOPE", jdbcTemplate.recordedParameters().get(1).get("lookup_cd"));
        assertEquals("client-a", jdbcTemplate.recordedParameters().get(1).get("client_id"));
        assertEquals(250, jdbcTemplate.recordedParameters().get(1).get("max_output_rows"));
        assertEquals("DISTINCT", jdbcTemplate.recordedParameters().get(2).get("execution_strategy_used_cd"));
        assertEquals("SUCCESS", jdbcTemplate.recordedParameters().get(2).get("result_status_cd"));

        assertEquals(1, filterLookupPolicyClient.previewRequests().size());
        FilterLookupPreviewPolicyRequestDto policyRequest = filterLookupPolicyClient.previewRequests().get(0);
        assertEquals("SQL_LEDGER_SCOPE", policyRequest.lookup_cd());
        assertEquals("SQL_QUERY", policyRequest.construction_type_cd());
        assertEquals("DISTINCT", policyRequest.execution_strategy_cd());
    }

    @Test
    void returnsUnprocessableEntityEndToEndWhenPreviewPolicyBlocks() throws Exception {
        OffsetDateTime logTimestamp = OffsetDateTime.parse("2026-06-18T05:45:30Z");
        jdbcTemplate.setResponses(List.of(
                List.of(filterLookupRow("LEDGER_SCOPE", "MANUAL_LIST", "IN_LIST", 10_000)),
                List.of(executionLogRow(701L, "LEDGER_SCOPE", "preview-user", logTimestamp, null, "IN_LIST", "BLOCKED", "GOV-FL-004"))
        ));
        filterLookupPolicyClient.denyPreview(
                "GOV-FL-004",
                "Anticipated values require approval before preview"
        );

        mockMvc.perform(post("/api/filter-lookups/preview")
                        .contentType("application/json")
                        .content("""
                                {
                                  "client_id": "client-a",
                                  "executed_by": "preview-user",
                                  "lookup_codes": ["LEDGER_SCOPE"]
                                }
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("GOV-FL-004"))
                .andExpect(jsonPath("$.message").value("Anticipated values require approval before preview"));

        assertEquals(2, jdbcTemplate.recordedSqls().size());
        assertTrue(jdbcTemplate.recordedSqls().get(0).contains("FROM meta.semantic_filter_lookup"));
        assertTrue(jdbcTemplate.recordedSqls().get(1).contains("INSERT INTO meta.filter_lookup_exec_log"));
        assertEquals("preview-user", jdbcTemplate.recordedParameters().get(1).get("executed_by"));
        assertEquals("BLOCKED", jdbcTemplate.recordedParameters().get(1).get("result_status_cd"));
        assertEquals("GOV-FL-004", jdbcTemplate.recordedParameters().get(1).get("blocked_by_policy_cd"));
        assertEquals(1, filterLookupPolicyClient.previewRequests().size());
    }

    private static Map<String, Object> filterLookupRow(String lookupCode,
                                                       String constructionTypeCode,
                                                       String executionStrategyCode,
                                                       Integer maxOutputRows) {
        Map<String, Object> row = new HashMap<>();
        row.put("id", 101L);
        row.put("lookup_cd", lookupCode);
        row.put("construction_type_cd", constructionTypeCode);
        row.put("manual_subtype_cd", "MANUAL_LIST".equals(constructionTypeCode) ? "HAND_TYPED" : null);
        row.put("filter_obj", "meta.gl_balance");
        row.put("filter_condition_txt", "ledger_status = 'ACTIVE'");
        row.put("filter_attr_cd", "ledger_id");
        row.put("validation_obj", "meta.ledger");
        row.put("validation_attr_cd", "ledger_id");
        row.put("suggested_target_attr_cd", "ledger_id");
        row.put("execution_strategy_cd", executionStrategyCode);
        row.put("max_input_set_size", 500);
        row.put("max_output_rows", maxOutputRows);
        row.put("cache_ttl_min", 60);
        row.put("review_period_days_override", null);
        row.put("rules_eligible_flg", true);
        row.put("qs_eligible_flg", true);
        row.put("ai_eligible_flg", false);
        row.put("replicate_to_ch_flg", false);
        row.put("description_txt", "Ledger scope values");
        row.put("client_id", "client-a");
        row.put("governance_status_cd", "REVIEW");
        row.put("health_status_cd", "PENDING");
        row.put("last_certified_ts", OffsetDateTime.parse("2026-06-16T10:15:30Z"));
        row.put("last_certified_by", "certifier");
        row.put("next_review_due_dt", LocalDate.parse("2026-08-02"));
        row.put("lifecycle_status_cd", "ACTIVE");
        row.put("created_ts", OffsetDateTime.parse("2026-06-16T10:15:30Z"));
        row.put("created_by", "producer");
        row.put("updated_ts", OffsetDateTime.parse("2026-06-17T10:15:30Z"));
        row.put("updated_by", "platform");
        return row;
    }

    private static Map<String, Object> manualPreviewValueRow(String lookupCode, String valueCode) {
        Map<String, Object> row = new HashMap<>();
        row.put("lookup_cd", lookupCode);
        row.put("client_id", "client-a");
        row.put("value_cd", valueCode);
        row.put("value_desc", "Ledger 100");
        row.put("lifecycle_status_cd", "ACTIVE");
        row.put("validated_flg", true);
        row.put("anticipated_dt", LocalDate.parse("2026-07-01"));
        row.put("workflow_ref", "WKFL-100");
        row.put("last_seen_in_source_ts", OffsetDateTime.parse("2026-06-18T10:15:30Z"));
        row.put("auto_expire_after_days", 14);
        row.put("alert_txt", "Pending activation");
        row.put("added_by", "producer");
        row.put("added_ts", OffsetDateTime.parse("2026-06-18T09:15:30Z"));
        row.put("certified_by", "reviewer");
        row.put("certified_ts", OffsetDateTime.parse("2026-06-18T11:15:30Z"));
        row.put("updated_ts", OffsetDateTime.parse("2026-06-18T12:15:30Z"));
        return row;
    }

    private static Map<String, Object> sqlPreviewValueRow(String lookupCode, String valueCode) {
        Map<String, Object> row = manualPreviewValueRow(lookupCode, valueCode);
        row.put("anticipated_dt", null);
        row.put("workflow_ref", null);
        row.put("added_by", null);
        row.put("added_ts", null);
        row.put("certified_by", null);
        row.put("certified_ts", null);
        row.put("updated_ts", null);
        row.put("auto_expire_after_days", null);
        row.put("alert_txt", null);
        return row;
    }

    private static Map<String, Object> executionLogRow(long id,
                                                       String lookupCode,
                                                       String executedBy,
                                                       OffsetDateTime executedTs,
                                                       Integer phase1RowCount,
                                                       String executionStrategyCode,
                                                       String resultStatusCode,
                                                       String blockedByPolicyCode) {
        Map<String, Object> row = new HashMap<>();
        row.put("id", id);
        row.put("lookup_cd", lookupCode);
        row.put("executed_by", executedBy);
        row.put("executed_ts", executedTs);
        row.put("phase1_duration_ms", 12);
        row.put("phase1_row_count", phase1RowCount);
        row.put("phase1_cache_hit_flg", false);
        row.put("execution_strategy_used_cd", executionStrategyCode);
        row.put("phase2_duration_ms", null);
        row.put("result_status_cd", resultStatusCode);
        row.put("error_txt", null);
        row.put("blocked_by_policy_cd", blockedByPolicyCode);
        return row;
    }

    @TestConfiguration
    static class FilterLookupPreviewWireThroughTestConfiguration {

        @Bean
        ObjectRegistrationWireThroughTest.RecordingNamedParameterJdbcTemplate recordingNamedParameterJdbcTemplate() {
            return new ObjectRegistrationWireThroughTest.RecordingNamedParameterJdbcTemplate();
        }

        @Bean
        RecordingFilterLookupPolicyClient recordingFilterLookupPolicyClient() {
            return new RecordingFilterLookupPolicyClient();
        }
    }

    static final class RecordingFilterLookupPolicyClient implements FilterLookupPolicyClient {

        private final List<FilterLookupPreviewPolicyRequestDto> previewRequests = new ArrayList<>();
        private FilterLookupPolicyDecisionDto previewDecision = new FilterLookupPolicyDecisionDto(true, null, null);

        void allowPreview() {
            previewRequests.clear();
            previewDecision = new FilterLookupPolicyDecisionDto(true, null, null);
        }

        void denyPreview(String code, String message) {
            previewRequests.clear();
            previewDecision = new FilterLookupPolicyDecisionDto(false, code, message);
        }

        List<FilterLookupPreviewPolicyRequestDto> previewRequests() {
            return previewRequests;
        }

        @Override
        public FilterLookupPolicyDecisionDto validateReviewPeriodFloor(FilterLookupPolicyRequestDto request) {
            return new FilterLookupPolicyDecisionDto(true, null, null);
        }

        @Override
        public FilterLookupPolicyDecisionDto validatePreviewExecution(FilterLookupPreviewPolicyRequestDto request) {
            previewRequests.add(request);
            return previewDecision;
        }
    }
}
