package com.lextr.semanticlayer.api;

import com.lextr.semanticlayer.dto.FilterLookupCertificationPolicyRequestDto;
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
        FilterLookupCertificationWireThroughTest.FilterLookupCertificationWireThroughTestConfiguration.class
})
@TestExecutionListeners(
        listeners = {
                DependencyInjectionTestExecutionListener.class,
                DirtiesContextBeforeModesTestExecutionListener.class,
                DirtiesContextTestExecutionListener.class
        },
        mergeMode = TestExecutionListeners.MergeMode.REPLACE_DEFAULTS
)
class FilterLookupCertificationWireThroughTest {

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
        filterLookupPolicyClient.allowCertification();
    }

    @Test
    void honorsFilterLookupCertificationContractEndToEnd() throws Exception {
        OffsetDateTime certificationTimestamp = OffsetDateTime.parse("2026-06-18T04:45:30Z");
        jdbcTemplate.setResponses(List.of(
                List.of(lookupRow("LEDGER_SCOPE", "PENDING", null, null, LocalDate.parse("2026-08-02"))),
                List.of(policyPresetRow()),
                List.of(staleValueCountRow(0L)),
                List.of(certifiedLookupRow("LEDGER_SCOPE", certificationTimestamp, "certifier", LocalDate.parse("2026-09-16"))),
                List.of(metadataChangeRow(certificationTimestamp)),
                List.of(valueCountRow(2L))
        ));

        mockMvc.perform(post("/api/filter-lookups/LEDGER_SCOPE/certify")
                        .contentType("application/json")
                        .content("""
                                {
                                  "client_id": "client-a",
                                  "certified_by": "certifier"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lookup_cd").value("LEDGER_SCOPE"))
                .andExpect(jsonPath("$.health_status_cd").value("HEALTHY"))
                .andExpect(jsonPath("$.value_count").value(2))
                .andExpect(jsonPath("$.effective_review_period_days").value(90))
                .andExpect(jsonPath("$.effective_review_period_source_cd").value("GOV_DEFAULT"))
                .andExpect(jsonPath("$.last_certified_by").value("certifier"))
                .andExpect(jsonPath("$.last_certified_ts").value("2026-06-18T04:45:30Z"))
                .andExpect(jsonPath("$.next_review_due_dt").value("2026-09-16"));

        assertEquals(6, jdbcTemplate.recordedSqls().size());
        assertTrue(jdbcTemplate.recordedSqls().get(0).contains("FROM meta.semantic_filter_lookup"));
        assertTrue(jdbcTemplate.recordedSqls().get(1).contains("FROM governance.policy_preset"));
        assertTrue(jdbcTemplate.recordedSqls().get(2).contains("COUNT(*) AS stale_value_count"));
        assertTrue(jdbcTemplate.recordedSqls().get(3).contains("UPDATE meta.semantic_filter_lookup"));
        assertTrue(jdbcTemplate.recordedSqls().get(4).contains("INSERT INTO meta.metadata_change_history"));
        assertTrue(jdbcTemplate.recordedSqls().get(5).contains("COUNT(*) AS value_count"));

        assertEquals("client-a", jdbcTemplate.recordedParameters().get(0).get("client_id"));
        assertEquals("LEDGER_SCOPE", jdbcTemplate.recordedParameters().get(0).get("lookup_cd"));
        assertEquals("GOV-FL-001", jdbcTemplate.recordedParameters().get(1).get("policy_cd"));
        assertEquals("LEDGER_SCOPE", jdbcTemplate.recordedParameters().get(2).get("lookup_cd"));
        assertEquals("HEALTHY", jdbcTemplate.recordedParameters().get(3).get("health_status_cd"));
        assertEquals("certifier", jdbcTemplate.recordedParameters().get(3).get("last_certified_by"));
        assertEquals("CERTIFIED", jdbcTemplate.recordedParameters().get(4).get("change_type_cd"));
        assertEquals("LEDGER_SCOPE", jdbcTemplate.recordedParameters().get(4).get("entity_ref"));
        assertEquals("client-a", jdbcTemplate.recordedParameters().get(5).get("client_id"));

        assertEquals(1, filterLookupPolicyClient.certificationRequests().size());
        FilterLookupCertificationPolicyRequestDto policyRequest = filterLookupPolicyClient.certificationRequests().get(0);
        assertEquals("client-a", policyRequest.client_id());
        assertEquals("LEDGER_SCOPE", policyRequest.lookup_cd());
        assertEquals("certifier", policyRequest.certified_by());
        assertEquals("PENDING", policyRequest.current_health_status_cd());
        assertEquals(0L, policyRequest.stale_value_count());
    }

    @Test
    void returnsUnprocessableEntityEndToEndWhenStaleValuePolicyBlocksCertification() throws Exception {
        jdbcTemplate.setResponses(List.of(
                List.of(lookupRow("LEDGER_SCOPE", "PENDING", null, null, LocalDate.parse("2026-08-02"))),
                List.of(policyPresetRow()),
                List.of(staleValueCountRow(3L))
        ));
        filterLookupPolicyClient.denyCertification(
                "POL-SV-001",
                "POL-SV-001: certification blocked for LEDGER_SCOPE because 3 values are inactive in source"
        );

        mockMvc.perform(post("/api/filter-lookups/LEDGER_SCOPE/certify")
                        .contentType("application/json")
                        .content("""
                                {
                                  "client_id": "client-a",
                                  "certified_by": "certifier"
                                }
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("POL-SV-001"))
                .andExpect(jsonPath("$.message").value("POL-SV-001: certification blocked for LEDGER_SCOPE because 3 values are inactive in source"));

        assertEquals(3, jdbcTemplate.recordedSqls().size());
        assertTrue(jdbcTemplate.recordedSqls().get(0).contains("FROM meta.semantic_filter_lookup"));
        assertTrue(jdbcTemplate.recordedSqls().get(1).contains("FROM governance.policy_preset"));
        assertTrue(jdbcTemplate.recordedSqls().get(2).contains("COUNT(*) AS stale_value_count"));

        assertEquals(1, filterLookupPolicyClient.certificationRequests().size());
        FilterLookupCertificationPolicyRequestDto policyRequest = filterLookupPolicyClient.certificationRequests().get(0);
        assertEquals("LEDGER_SCOPE", policyRequest.lookup_cd());
        assertEquals(3L, policyRequest.stale_value_count());
    }

    private static Map<String, Object> lookupRow(String lookupCode,
                                                 String healthStatusCode,
                                                 OffsetDateTime lastCertifiedTs,
                                                 String lastCertifiedBy,
                                                 LocalDate nextReviewDueDate) {
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
        row.put("health_status_cd", healthStatusCode);
        row.put("last_certified_ts", lastCertifiedTs);
        row.put("last_certified_by", lastCertifiedBy);
        row.put("next_review_due_dt", nextReviewDueDate);
        row.put("lifecycle_status_cd", "ACTIVE");
        row.put("created_ts", OffsetDateTime.parse("2026-06-16T10:15:30Z"));
        row.put("created_by", "producer");
        row.put("updated_ts", OffsetDateTime.parse("2026-06-17T10:15:30Z"));
        row.put("updated_by", "platform");
        return row;
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

    private static Map<String, Object> staleValueCountRow(long staleValueCount) {
        Map<String, Object> row = new HashMap<>();
        row.put("stale_value_count", staleValueCount);
        return row;
    }

    private static Map<String, Object> certifiedLookupRow(String lookupCode,
                                                          OffsetDateTime certificationTimestamp,
                                                          String certifiedBy,
                                                          LocalDate nextReviewDueDate) {
        Map<String, Object> row = lookupRow(lookupCode, "HEALTHY", certificationTimestamp, certifiedBy, nextReviewDueDate);
        row.put("updated_ts", certificationTimestamp);
        row.put("updated_by", certifiedBy);
        return row;
    }

    private static Map<String, Object> metadataChangeRow(OffsetDateTime changedTs) {
        Map<String, Object> row = new HashMap<>();
        row.put("id", 401L);
        row.put("entity_type_cd", "FILTER_LOOKUP");
        row.put("entity_ref", "LEDGER_SCOPE");
        row.put("change_type_cd", "CERTIFIED");
        row.put("changed_by", "certifier");
        row.put("changed_ts", changedTs);
        row.put("old_value_json", null);
        row.put("new_value_json", null);
        row.put("change_reason_txt", "Certified filter lookup LEDGER_SCOPE");
        return row;
    }

    private static Map<String, Object> valueCountRow(long valueCount) {
        Map<String, Object> row = new HashMap<>();
        row.put("value_count", valueCount);
        return row;
    }

    @TestConfiguration
    static class FilterLookupCertificationWireThroughTestConfiguration {

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

        private final List<FilterLookupCertificationPolicyRequestDto> certificationRequests = new ArrayList<>();
        private FilterLookupPolicyDecisionDto certificationDecision = new FilterLookupPolicyDecisionDto(true, null, null);

        void allowCertification() {
            certificationRequests.clear();
            certificationDecision = new FilterLookupPolicyDecisionDto(true, null, null);
        }

        void denyCertification(String code, String message) {
            certificationRequests.clear();
            certificationDecision = new FilterLookupPolicyDecisionDto(false, code, message);
        }

        List<FilterLookupCertificationPolicyRequestDto> certificationRequests() {
            return certificationRequests;
        }

        @Override
        public FilterLookupPolicyDecisionDto validateReviewPeriodFloor(FilterLookupPolicyRequestDto request) {
            return new FilterLookupPolicyDecisionDto(true, null, null);
        }

        @Override
        public FilterLookupPolicyDecisionDto validateCertification(FilterLookupCertificationPolicyRequestDto request) {
            certificationRequests.add(request);
            return certificationDecision;
        }
    }
}
