package com.lextr.semanticlayer.api;

import com.lextr.semanticlayer.dto.FilterLookupPolicyDecisionDto;
import com.lextr.semanticlayer.dto.FilterLookupPolicyRequestDto;
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
        FilterLookupRegistrationWireThroughTest.FilterLookupRegistrationWireThroughTestConfiguration.class
})
@TestExecutionListeners(
        listeners = {
                DependencyInjectionTestExecutionListener.class,
                DirtiesContextBeforeModesTestExecutionListener.class,
                DirtiesContextTestExecutionListener.class
        },
        mergeMode = TestExecutionListeners.MergeMode.REPLACE_DEFAULTS
)
class FilterLookupRegistrationWireThroughTest {

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
        filterLookupPolicyClient.allow();
    }

    @Test
    void honorsFilterLookupRegistrationContractEndToEnd() throws Exception {
        OffsetDateTime timestamp = OffsetDateTime.parse("2026-06-18T04:45:30Z");
        jdbcTemplate.setResponses(List.of(
                List.of(policyPresetRow()),
                List.of(filterLookupRow(timestamp)),
                List.of(workflowTaskRow(timestamp)),
                List.of(metadataChangeRow(timestamp))
        ));

        mockMvc.perform(post("/api/filter-lookups")
                        .contentType("application/json")
                        .content(validRequestJson(45)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(101))
                .andExpect(jsonPath("$.lookup_cd").value("LEDGER_SCOPE"))
                .andExpect(jsonPath("$.construction_type_cd").value("MANUAL_LIST"))
                .andExpect(jsonPath("$.review_period_days_override").value(45))
                .andExpect(jsonPath("$.governance_status_cd").value("REVIEW"))
                .andExpect(jsonPath("$.health_status_cd").value("PENDING"))
                .andExpect(jsonPath("$.next_review_due_dt").value("2026-08-02"))
                .andExpect(jsonPath("$.lifecycle_status_cd").value("ACTIVE"))
                .andExpect(jsonPath("$.workflow_task_id").value(301))
                .andExpect(jsonPath("$.workflow_status_cd").value("PENDING"));

        assertEquals(4, jdbcTemplate.recordedSqls().size());
        assertTrue(jdbcTemplate.recordedSqls().get(0).contains("FROM governance.policy_preset"));
        assertTrue(jdbcTemplate.recordedSqls().get(1).contains("INSERT INTO meta.semantic_filter_lookup"));
        assertTrue(jdbcTemplate.recordedSqls().get(2).contains("INSERT INTO wkfl.workflow_task"));
        assertTrue(jdbcTemplate.recordedSqls().get(3).contains("INSERT INTO meta.metadata_change_history"));

        assertEquals("GOV-FL-001", jdbcTemplate.recordedParameters().get(0).get("policy_cd"));
        assertEquals("FILTER_LOOKUP", jdbcTemplate.recordedParameters().get(0).get("policy_scope_cd"));
        assertEquals("LEDGER_SCOPE", jdbcTemplate.recordedParameters().get(1).get("lookup_cd"));
        assertEquals(Integer.valueOf(45), jdbcTemplate.recordedParameters().get(1).get("review_period_days_override"));
        assertEquals("REVIEW", jdbcTemplate.recordedParameters().get(1).get("governance_status_cd"));
        assertEquals("PENDING", jdbcTemplate.recordedParameters().get(2).get("task_status_cd"));
        assertEquals("REGISTERED", jdbcTemplate.recordedParameters().get(3).get("change_type_cd"));

        assertEquals(1, filterLookupPolicyClient.recordedRequests().size());
        FilterLookupPolicyRequestDto policyRequest = filterLookupPolicyClient.recordedRequests().get(0);
        assertEquals("client-a", policyRequest.client_id());
        assertEquals("LEDGER_SCOPE", policyRequest.lookup_cd());
        assertEquals("GOV-FL-001", policyRequest.policy_cd());
        assertEquals(Integer.valueOf(90), policyRequest.review_period_floor_days());
        assertEquals(Integer.valueOf(45), policyRequest.review_period_days_override());
    }

    @Test
    void returnsUnprocessableEntityEndToEndWhenPolicyBlocksLooserOverride() throws Exception {
        jdbcTemplate.setResponses(List.of(List.of(policyPresetRow())));
        filterLookupPolicyClient.deny(
                "GOV-FL-001",
                "GOV-FL-001: review period override 120 cannot be looser than governance floor 90"
        );

        mockMvc.perform(post("/api/filter-lookups")
                        .contentType("application/json")
                        .content(validRequestJson(120)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("GOV-FL-001"))
                .andExpect(jsonPath("$.message").value("GOV-FL-001: review period override 120 cannot be looser than governance floor 90"));

        assertEquals(1, jdbcTemplate.recordedSqls().size());
        assertTrue(jdbcTemplate.recordedSqls().get(0).contains("FROM governance.policy_preset"));
        assertEquals(1, filterLookupPolicyClient.recordedRequests().size());
    }

    private static String validRequestJson(int reviewPeriodDaysOverride) {
        return """
                {
                  "client_id": "client-a",
                  "lookup_cd": "LEDGER_SCOPE",
                  "construction_type_cd": "MANUAL_LIST",
                  "manual_subtype_cd": "HAND_TYPED",
                  "filter_obj": "meta.gl_balance",
                  "filter_condition_txt": "ledger_status = 'ACTIVE'",
                  "filter_attr_cd": "ledger_id",
                  "validation_obj": "meta.ledger",
                  "validation_attr_cd": "ledger_id",
                  "suggested_target_attr_cd": "ledger_id",
                  "execution_strategy_cd": "IN_LIST",
                  "max_input_set_size": 500,
                  "max_output_rows": 10000,
                  "cache_ttl_min": 60,
                  "review_period_days_override": %d,
                  "rules_eligible_flg": true,
                  "qs_eligible_flg": true,
                  "ai_eligible_flg": false,
                  "replicate_to_ch_flg": false,
                  "description_txt": "Ledger scope values for governance testing",
                  "registered_by": "producer"
                }
                """.formatted(reviewPeriodDaysOverride);
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

    private static Map<String, Object> filterLookupRow(OffsetDateTime timestamp) {
        Map<String, Object> row = new HashMap<>();
        row.put("id", 101L);
        row.put("lookup_cd", "LEDGER_SCOPE");
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
        row.put("review_period_days_override", 45);
        row.put("rules_eligible_flg", true);
        row.put("qs_eligible_flg", true);
        row.put("ai_eligible_flg", false);
        row.put("replicate_to_ch_flg", false);
        row.put("description_txt", "Ledger scope values for governance testing");
        row.put("client_id", "client-a");
        row.put("governance_status_cd", "REVIEW");
        row.put("health_status_cd", "PENDING");
        row.put("last_certified_ts", null);
        row.put("last_certified_by", null);
        row.put("next_review_due_dt", LocalDate.parse("2026-08-02"));
        row.put("lifecycle_status_cd", "ACTIVE");
        row.put("created_ts", timestamp);
        row.put("created_by", "producer");
        row.put("updated_ts", timestamp);
        row.put("updated_by", "producer");
        return row;
    }

    private static Map<String, Object> workflowTaskRow(OffsetDateTime timestamp) {
        Map<String, Object> row = new HashMap<>();
        row.put("id", 301L);
        row.put("task_type_cd", "FILTER_LOOKUP_REGISTRATION");
        row.put("entity_type_cd", "FILTER_LOOKUP");
        row.put("entity_ref", "LEDGER_SCOPE");
        row.put("task_status_cd", "PENDING");
        row.put("submitted_by", "producer");
        row.put("submitted_ts", timestamp);
        row.put("assigned_to", null);
        row.put("due_dt", LocalDate.parse("2026-08-02"));
        row.put("description_txt", "Review filter lookup LEDGER_SCOPE");
        row.put("client_id", "client-a");
        row.put("approved_by", null);
        row.put("approved_ts", null);
        row.put("approval_note_txt", null);
        return row;
    }

    private static Map<String, Object> metadataChangeRow(OffsetDateTime timestamp) {
        Map<String, Object> row = new HashMap<>();
        row.put("id", 401L);
        row.put("entity_type_cd", "FILTER_LOOKUP");
        row.put("entity_ref", "LEDGER_SCOPE");
        row.put("change_type_cd", "REGISTERED");
        row.put("changed_by", "producer");
        row.put("changed_ts", timestamp);
        row.put("old_value_json", null);
        row.put("new_value_json", null);
        row.put("change_reason_txt", "Registered filter lookup LEDGER_SCOPE");
        return row;
    }

    @TestConfiguration
    static class FilterLookupRegistrationWireThroughTestConfiguration {

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

        private final List<FilterLookupPolicyRequestDto> recordedRequests = new ArrayList<>();
        private FilterLookupPolicyDecisionDto decision = new FilterLookupPolicyDecisionDto(true, null, null);

        void allow() {
            recordedRequests.clear();
            decision = new FilterLookupPolicyDecisionDto(true, null, null);
        }

        void deny(String code, String message) {
            recordedRequests.clear();
            decision = new FilterLookupPolicyDecisionDto(false, code, message);
        }

        List<FilterLookupPolicyRequestDto> recordedRequests() {
            return recordedRequests;
        }

        @Override
        public FilterLookupPolicyDecisionDto validateReviewPeriodFloor(FilterLookupPolicyRequestDto request) {
            recordedRequests.add(request);
            return decision;
        }
    }
}
