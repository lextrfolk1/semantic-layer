package com.lextr.semanticlayer.api;

import com.lextr.semanticlayer.dto.FilterLookupBindingPolicyRequestDto;
import com.lextr.semanticlayer.dto.FilterLookupPolicyDecisionDto;
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
        FilterLookupBindingWireThroughTest.FilterLookupBindingWireThroughTestConfiguration.class
})
@TestExecutionListeners(
        listeners = {
                DependencyInjectionTestExecutionListener.class,
                DirtiesContextBeforeModesTestExecutionListener.class,
                DirtiesContextTestExecutionListener.class
        },
        mergeMode = TestExecutionListeners.MergeMode.REPLACE_DEFAULTS
)
class FilterLookupBindingWireThroughTest {

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
        filterLookupPolicyClient.allowBinding();
    }

    @Test
    void honorsFilterLookupBindingContractEndToEnd() throws Exception {
        OffsetDateTime now = OffsetDateTime.parse("2026-06-18T10:15:30Z");
        jdbcTemplate.setResponses(List.of(
                List.of(lookupRow("LEDGER_SCOPE", LocalDate.parse("2026-08-02"))),
                List.of(boundRow(now)),
                List.of(metadataChangeRow(now))
        ));

        mockMvc.perform(post("/api/filter-lookups/LEDGER_SCOPE/bindings")
                        .contentType("application/json")
                        .content("""
                                {
                                  "client_id": "client-a",
                                  "bound_obj": "meta.gl_balance",
                                  "bound_attr_cd": "ledger_id",
                                  "binding_context_cd": "PIPELINE",
                                  "binding_ref": "daily-pipeline",
                                  "bound_by": "binder"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(501))
                .andExpect(jsonPath("$.lookup_cd").value("LEDGER_SCOPE"))
                .andExpect(jsonPath("$.bound_obj").value("meta.gl_balance"))
                .andExpect(jsonPath("$.bound_attr_cd").value("ledger_id"))
                .andExpect(jsonPath("$.binding_context_cd").value("PIPELINE"))
                .andExpect(jsonPath("$.binding_ref").value("daily-pipeline"))
                .andExpect(jsonPath("$.bound_by").value("binder"))
                .andExpect(jsonPath("$.bound_ts").value("2026-06-18T10:15:30Z"))
                .andExpect(jsonPath("$.is_active_flg").value(true));

        assertEquals(3, jdbcTemplate.recordedSqls().size());
        assertTrue(jdbcTemplate.recordedSqls().get(0).contains("FROM meta.semantic_filter_lookup"));
        assertTrue(jdbcTemplate.recordedSqls().get(1).contains("INSERT INTO meta.filter_lookup_binding"));
        assertTrue(jdbcTemplate.recordedSqls().get(2).contains("INSERT INTO meta.metadata_change_history"));

        assertEquals("client-a", jdbcTemplate.recordedParameters().get(0).get("client_id"));
        assertEquals("LEDGER_SCOPE", jdbcTemplate.recordedParameters().get(0).get("lookup_cd"));
        assertEquals("client-a", jdbcTemplate.recordedParameters().get(1).get("client_id"));
        assertEquals("LEDGER_SCOPE", jdbcTemplate.recordedParameters().get(1).get("lookup_cd"));
        assertEquals("meta.gl_balance", jdbcTemplate.recordedParameters().get(1).get("bound_obj"));
        assertEquals("ledger_id", jdbcTemplate.recordedParameters().get(1).get("bound_attr_cd"));
        assertEquals("PIPELINE", jdbcTemplate.recordedParameters().get(1).get("binding_context_cd"));
        assertEquals("daily-pipeline", jdbcTemplate.recordedParameters().get(1).get("binding_ref"));
        assertEquals("binder", jdbcTemplate.recordedParameters().get(1).get("bound_by"));
        assertEquals("BOUND", jdbcTemplate.recordedParameters().get(2).get("change_type_cd"));
        assertEquals("LEDGER_SCOPE", jdbcTemplate.recordedParameters().get(2).get("entity_ref"));

        assertEquals(1, filterLookupPolicyClient.bindingRequests().size());
        FilterLookupBindingPolicyRequestDto policyRequest = filterLookupPolicyClient.bindingRequests().get(0);
        assertEquals("client-a", policyRequest.client_id());
        assertEquals("LEDGER_SCOPE", policyRequest.lookup_cd());
        assertEquals("PIPELINE", policyRequest.binding_context_cd());
        assertEquals(false, policyRequest.is_overdue());
    }

    @Test
    void returnsUnprocessableEntityEndToEndWhenOverduePolicyBlocksBinding() throws Exception {
        jdbcTemplate.setResponses(List.of(
                List.of(lookupRow("LEDGER_SCOPE", LocalDate.parse("2026-06-01"))) // overdue in June 18
        ));
        filterLookupPolicyClient.denyBinding(
                "POL-SV-002",
                "POL-SV-002: overdue lookup binding is blocked for PIPELINE"
        );

        mockMvc.perform(post("/api/filter-lookups/LEDGER_SCOPE/bindings")
                        .contentType("application/json")
                        .content("""
                                {
                                  "client_id": "client-a",
                                  "bound_obj": "meta.gl_balance",
                                  "bound_attr_cd": "ledger_id",
                                  "binding_context_cd": "PIPELINE",
                                  "binding_ref": "daily-pipeline",
                                  "bound_by": "binder"
                                }
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("POL-SV-002"))
                .andExpect(jsonPath("$.message").value("POL-SV-002: overdue lookup binding is blocked for PIPELINE"));

        assertEquals(1, jdbcTemplate.recordedSqls().size());
        assertTrue(jdbcTemplate.recordedSqls().get(0).contains("FROM meta.semantic_filter_lookup"));

        assertEquals(1, filterLookupPolicyClient.bindingRequests().size());
        FilterLookupBindingPolicyRequestDto policyRequest = filterLookupPolicyClient.bindingRequests().get(0);
        assertEquals("LEDGER_SCOPE", policyRequest.lookup_cd());
        assertEquals(true, policyRequest.is_overdue());
    }

    @Test
    void allowsQueryStudioBindingEvenWhenOverdue() throws Exception {
        OffsetDateTime now = OffsetDateTime.parse("2026-06-18T10:15:30Z");
        jdbcTemplate.setResponses(List.of(
                List.of(lookupRow("LEDGER_SCOPE", LocalDate.parse("2026-06-01"))), // overdue on June 18
                List.of(boundRow(now, "QUERY_STUDIO", "qs-session")),
                List.of(metadataChangeRow(now))
        ));

        mockMvc.perform(post("/api/filter-lookups/LEDGER_SCOPE/bindings")
                        .contentType("application/json")
                        .content("""
                                {
                                  "client_id": "client-a",
                                  "bound_obj": "meta.gl_balance",
                                  "bound_attr_cd": "ledger_id",
                                  "binding_context_cd": "QUERY_STUDIO",
                                  "binding_ref": "qs-session",
                                  "bound_by": "binder"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(501))
                .andExpect(jsonPath("$.lookup_cd").value("LEDGER_SCOPE"))
                .andExpect(jsonPath("$.binding_context_cd").value("QUERY_STUDIO"));

        assertEquals(3, jdbcTemplate.recordedSqls().size());
        assertEquals(1, filterLookupPolicyClient.bindingRequests().size());
        FilterLookupBindingPolicyRequestDto policyRequest = filterLookupPolicyClient.bindingRequests().get(0);
        assertEquals("QUERY_STUDIO", policyRequest.binding_context_cd());
        assertEquals(true, policyRequest.is_overdue());
    }

    private static Map<String, Object> lookupRow(String lookupCode, LocalDate nextReviewDueDate) {
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
        row.put("review_period_days_override", null);
        row.put("rules_eligible_flg", true);
        row.put("qs_eligible_flg", true);
        row.put("ai_eligible_flg", false);
        row.put("replicate_to_ch_flg", false);
        row.put("description_txt", "Ledger scope values");
        row.put("client_id", "client-a");
        row.put("governance_status_cd", "ACTIVE");
        row.put("health_status_cd", "HEALTHY");
        row.put("last_certified_ts", OffsetDateTime.parse("2026-06-16T10:15:30Z"));
        row.put("last_certified_by", "certifier");
        row.put("next_review_due_dt", nextReviewDueDate);
        row.put("lifecycle_status_cd", "ACTIVE");
        row.put("created_ts", OffsetDateTime.parse("2026-06-16T10:15:30Z"));
        row.put("created_by", "producer");
        row.put("updated_ts", OffsetDateTime.parse("2026-06-17T10:15:30Z"));
        row.put("updated_by", "platform");
        return row;
    }

    private static Map<String, Object> boundRow(OffsetDateTime boundTs) {
        return boundRow(boundTs, "PIPELINE", "daily-pipeline");
    }

    private static Map<String, Object> boundRow(OffsetDateTime boundTs, String bindingContext, String bindingRef) {
        Map<String, Object> row = new HashMap<>();
        row.put("id", 501L);
        row.put("client_id", "client-a");
        row.put("lookup_cd", "LEDGER_SCOPE");
        row.put("bound_obj", "meta.gl_balance");
        row.put("bound_attr_cd", "ledger_id");
        row.put("binding_context_cd", bindingContext);
        row.put("binding_ref", bindingRef);
        row.put("bound_by", "binder");
        row.put("bound_ts", boundTs);
        row.put("is_active_flg", true);
        return row;
    }

    private static Map<String, Object> metadataChangeRow(OffsetDateTime changedTs) {
        Map<String, Object> row = new HashMap<>();
        row.put("id", 401L);
        row.put("entity_type_cd", "FILTER_LOOKUP");
        row.put("entity_ref", "LEDGER_SCOPE");
        row.put("change_type_cd", "BOUND");
        row.put("changed_by", "binder");
        row.put("changed_ts", changedTs);
        row.put("old_value_json", null);
        row.put("new_value_json", null);
        row.put("change_reason_txt", "Bound filter lookup LEDGER_SCOPE");
        return row;
    }

    @TestConfiguration
    static class FilterLookupBindingWireThroughTestConfiguration {

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

        private final List<FilterLookupBindingPolicyRequestDto> bindingRequests = new ArrayList<>();
        private FilterDecisionHolder bindingDecisionHolder = new FilterDecisionHolder(true, null, null);

        void allowBinding() {
            bindingRequests.clear();
            bindingDecisionHolder = new FilterDecisionHolder(true, null, null);
        }

        void denyBinding(String code, String message) {
            bindingRequests.clear();
            bindingDecisionHolder = new FilterDecisionHolder(false, code, message);
        }

        List<FilterLookupBindingPolicyRequestDto> bindingRequests() {
            return bindingRequests;
        }

        @Override
        public FilterLookupPolicyDecisionDto validateReviewPeriodFloor(com.lextr.semanticlayer.dto.FilterLookupPolicyRequestDto request) {
            return new FilterLookupPolicyDecisionDto(true, null, null);
        }

        @Override
        public FilterLookupPolicyDecisionDto validateBinding(FilterLookupBindingPolicyRequestDto request) {
            bindingRequests.add(request);
            return new FilterLookupPolicyDecisionDto(bindingDecisionHolder.allowed, bindingDecisionHolder.code, bindingDecisionHolder.message);
        }
    }

    private static class FilterDecisionHolder {
        private final boolean allowed;
        private final String code;
        private final String message;

        FilterDecisionHolder(boolean allowed, String code, String message) {
            this.allowed = allowed;
            this.code = code;
            this.message = message;
        }
    }
}
