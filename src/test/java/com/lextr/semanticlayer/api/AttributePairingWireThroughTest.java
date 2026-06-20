package com.lextr.semanticlayer.api;

import com.lextr.semanticlayer.dto.AttributePairingPolicyDecisionDto;
import com.lextr.semanticlayer.dto.AttributePairingPolicyRequestDto;
import com.lextr.semanticlayer.service.AttributePairingPolicyClient;
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
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.web.context.WebApplicationContext;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringJUnitWebConfig(classes = {
        com.lextr.semanticlayer.SemanticLayerApplication.class,
        AttributePairingWireThroughTest.AttributePairingWireThroughTestConfiguration.class
})
@TestExecutionListeners(
        listeners = {
                DependencyInjectionTestExecutionListener.class,
                DirtiesContextBeforeModesTestExecutionListener.class,
                DirtiesContextTestExecutionListener.class
        },
        mergeMode = TestExecutionListeners.MergeMode.REPLACE_DEFAULTS
)
class AttributePairingWireThroughTest {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private ObjectRegistrationWireThroughTest.RecordingNamedParameterJdbcTemplate jdbcTemplate;

    @Autowired
    private RecordingAttributePairingPolicyClient attributePairingPolicyClient;

    @BeforeEach
    void resetDependencies() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        jdbcTemplate.reset();
        attributePairingPolicyClient.allow();
    }

    @Test
    void honorsAttributePairingRegistrationContractEndToEnd() throws Exception {
        OffsetDateTime timestamp = OffsetDateTime.parse("2026-06-18T10:15:30Z");
        jdbcTemplate.setResponses(List.of(
                List.of(objectRow()),
                List.of(attributeRow("customer_nm"), attributeRow("customer_id")),
                List.of(Map.of("indexed_flg", true)),
                List.of(pairingRow(timestamp)),
                List.of(workflowTaskRow(timestamp)),
                List.of(metadataChangeRow(timestamp))
        ));

        mockMvc.perform(post("/api/attribute-pairings")
                        .contentType("application/json")
                        .content("""
                                {
                                  "client_id": "client-a",
                                  "pairing_cd": "CUSTOMER_NAME_TO_ID",
                                  "pairing_nm": "Customer Name To Id",
                                  "schema_cd": "meta",
                                  "object_cd": "customer",
                                  "display_attribute_cd": "customer_nm",
                                  "filter_attribute_cd": "customer_id",
                                  "pairing_type_cd": "DISPLAY_TO_FILTER",
                                  "lookup_strategy_cd": "CACHED_LOOKUP",
                                  "lookup_inline_map_jsonb": "{\\"Acme Corp\\":\\"CUST-100\\"}",
                                  "lookup_cache_enabled_flg": true,
                                  "lookup_cache_ttl_seconds_nbr": 3600,
                                  "cardinality_cd": "ONE_TO_ONE",
                                  "is_bidirectional_flg": false,
                                  "is_cross_engine_flg": false,
                                  "filter_attribute_indexed_flg": true,
                                  "filter_attribute_index_type_cd": "BTREE",
                                  "performance_gain_pct_est_nbr": 20,
                                  "ai_context_txt": "Resolve customer name to indexed customer id",
                                  "registered_by": "producer"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(101))
                .andExpect(jsonPath("$.pairing_cd").value("CUSTOMER_NAME_TO_ID"))
                .andExpect(jsonPath("$.schema_cd").value("meta"))
                .andExpect(jsonPath("$.object_cd").value("customer"))
                .andExpect(jsonPath("$.display_attribute_cd").value("customer_nm"))
                .andExpect(jsonPath("$.filter_attribute_cd").value("customer_id"))
                .andExpect(jsonPath("$.lifecycle_status_cd").value("ACTIVE"))
                .andExpect(jsonPath("$.governance_review_status_cd").value("PENDING"));

        assertEquals(6, jdbcTemplate.recordedSqls().size());
        assertTrue(jdbcTemplate.recordedSqls().get(0).contains("FROM meta.object_catalog"));
        assertTrue(jdbcTemplate.recordedSqls().get(1).contains("FROM meta.attribute_catalog"));
        assertTrue(jdbcTemplate.recordedSqls().get(2).contains("pg_index"));
        assertTrue(jdbcTemplate.recordedSqls().get(3).contains("INSERT INTO meta.attribute_pairing_catalog"));
        assertTrue(jdbcTemplate.recordedSqls().get(4).contains("INSERT INTO wkfl.workflow_task"));
        assertTrue(jdbcTemplate.recordedSqls().get(5).contains("INSERT INTO meta.metadata_change_history"));

        assertEquals("meta", jdbcTemplate.recordedParameters().get(0).get("schema_cd"));
        assertEquals("customer", jdbcTemplate.recordedParameters().get(0).get("object_cd"));
        assertEquals("client-a", jdbcTemplate.recordedParameters().get(1).get("client_id"));
        assertEquals("customer_id", jdbcTemplate.recordedParameters().get(2).get("attribute_cd"));
        assertEquals("CUSTOMER_NAME_TO_ID", jdbcTemplate.recordedParameters().get(3).get("pairing_cd"));
        assertEquals("ACTIVE", jdbcTemplate.recordedParameters().get(3).get("lifecycle_status_cd"));
        assertEquals("ATTRIBUTE_PAIRING_REGISTRATION", jdbcTemplate.recordedParameters().get(4).get("task_type_cd"));
        assertEquals("REGISTERED", jdbcTemplate.recordedParameters().get(5).get("change_type_cd"));

        assertEquals(1, attributePairingPolicyClient.recordedRequests().size());
        AttributePairingPolicyRequestDto policyRequest = attributePairingPolicyClient.recordedRequests().get(0);
        assertEquals("client-a", policyRequest.client_id());
        assertEquals("CUSTOMER_NAME_TO_ID", policyRequest.pairing_cd());
        assertEquals(false, policyRequest.is_cross_engine_flg());
    }

    @Test
    void honorsAttributePairingResolutionContractEndToEnd() throws Exception {
        OffsetDateTime timestamp = OffsetDateTime.parse("2026-06-18T10:15:30Z");
        jdbcTemplate.setResponses(List.of(
                List.of(pairingRow(timestamp)),
                List.of(),
                List.of(cacheRow(timestamp))
        ));

        mockMvc.perform(post("/api/attribute-pairings/resolve")
                        .contentType("application/json")
                        .content("""
                                {
                                  "client_id": "client-a",
                                  "schema_cd": "meta",
                                  "object_cd": "customer",
                                  "display_attribute_cd": "customer_nm",
                                  "display_value_txt": "Acme Corp"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pairing_cd").value("CUSTOMER_NAME_TO_ID"))
                .andExpect(jsonPath("$.schema_cd").value("meta"))
                .andExpect(jsonPath("$.object_cd").value("customer"))
                .andExpect(jsonPath("$.display_attribute_cd").value("customer_nm"))
                .andExpect(jsonPath("$.filter_attribute_cd").value("customer_id"))
                .andExpect(jsonPath("$.display_value_txt").value("Acme Corp"))
                .andExpect(jsonPath("$.filter_value_txt").value("CUST-100"))
                .andExpect(jsonPath("$.is_one_to_many_flg").value(false))
                .andExpect(jsonPath("$.cache_hit_flg").value(false));

        assertEquals(3, jdbcTemplate.recordedSqls().size());
        assertTrue(jdbcTemplate.recordedSqls().get(0).contains("FROM meta.attribute_pairing_catalog"));
        assertTrue(jdbcTemplate.recordedSqls().get(1).contains("FROM meta.attribute_pairing_value_cache"));
        assertTrue(jdbcTemplate.recordedSqls().get(2).contains("ON CONFLICT"));

        assertEquals("client-a", jdbcTemplate.recordedParameters().get(0).get("client_id"));
        assertEquals("meta", jdbcTemplate.recordedParameters().get(0).get("schema_cd"));
        assertEquals("customer", jdbcTemplate.recordedParameters().get(0).get("object_cd"));
        assertEquals("customer_nm", jdbcTemplate.recordedParameters().get(0).get("display_attribute_cd"));
        assertEquals("Acme Corp", jdbcTemplate.recordedParameters().get(1).get("display_value_txt"));
        assertEquals("CUST-100", jdbcTemplate.recordedParameters().get(2).get("filter_value_txt"));
    }

    @Test
    void returnsUnprocessableEntityEndToEndWhenPolicyBlocksCrossEnginePairing() throws Exception {
        jdbcTemplate.setResponses(List.of(
                List.of(objectRow()),
                List.of(attributeRow("customer_nm"), attributeRow("customer_id")),
                List.of(Map.of("indexed_flg", true))
        ));
        attributePairingPolicyClient.deny(
                "POL-CE-002",
                "POL-CE-002: cross-engine pairing is not allowed for pairing_cd CUSTOMER_NAME_TO_ID"
        );

        mockMvc.perform(post("/api/attribute-pairings")
                        .contentType("application/json")
                        .content("""
                                {
                                  "client_id": "client-a",
                                  "pairing_cd": "CUSTOMER_NAME_TO_ID",
                                  "pairing_nm": "Customer Name To Id",
                                  "schema_cd": "meta",
                                  "object_cd": "customer",
                                  "display_attribute_cd": "customer_nm",
                                  "filter_attribute_cd": "customer_id",
                                  "pairing_type_cd": "DISPLAY_TO_FILTER",
                                  "lookup_strategy_cd": "CACHED_LOOKUP",
                                  "cardinality_cd": "ONE_TO_ONE",
                                  "is_bidirectional_flg": false,
                                  "is_cross_engine_flg": true,
                                  "filter_attribute_indexed_flg": true,
                                  "registered_by": "producer"
                                }
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("POL-CE-002"))
                .andExpect(jsonPath("$.message").value("POL-CE-002: cross-engine pairing is not allowed for pairing_cd CUSTOMER_NAME_TO_ID"));

        assertEquals(3, jdbcTemplate.recordedSqls().size());
        assertTrue(jdbcTemplate.recordedSqls().get(0).contains("FROM meta.object_catalog"));
        assertTrue(jdbcTemplate.recordedSqls().get(1).contains("FROM meta.attribute_catalog"));
        assertTrue(jdbcTemplate.recordedSqls().get(2).contains("pg_index"));

        assertEquals(1, attributePairingPolicyClient.recordedRequests().size());
        AttributePairingPolicyRequestDto policyRequest = attributePairingPolicyClient.recordedRequests().get(0);
        assertEquals(true, policyRequest.is_cross_engine_flg());
    }

    private static Map<String, Object> objectRow() {
        Map<String, Object> row = new HashMap<>();
        row.put("object_id", UUID.fromString("00000000-0000-0000-0000-000000000301"));
        row.put("client_id", "client-a");
        row.put("object_cd", "customer");
        row.put("object_nm", "Customer");
        row.put("effective_object_nm", "Customer");
        row.put("object_type_cd", "TABLE");
        row.put("schema_cd", "meta");
        row.put("connection_id", UUID.fromString("00000000-0000-0000-0000-000000000401"));
        row.put("lifecycle_status_cd", "ACTIVE");
        row.put("created_ts", OffsetDateTime.parse("2026-06-18T10:15:30Z"));
        row.put("created_by", "producer");
        row.put("updated_ts", OffsetDateTime.parse("2026-06-18T10:15:30Z"));
        row.put("updated_by", "producer");
        return row;
    }

    private static Map<String, Object> attributeRow(String attributeCode) {
        Map<String, Object> row = new HashMap<>();
        row.put("attribute_id", UUID.fromString(
                "customer_nm".equals(attributeCode)
                        ? "00000000-0000-0000-0000-000000000501"
                        : "00000000-0000-0000-0000-000000000502"
        ));
        row.put("object_id", UUID.fromString("00000000-0000-0000-0000-000000000301"));
        row.put("client_id", "client-a");
        row.put("attribute_cd", attributeCode);
        row.put("attribute_nm", attributeCode);
        row.put("effective_attribute_nm", attributeCode);
        row.put("data_type_cd", "VARCHAR");
        row.put("taxonomy_cd", "TAX");
        row.put("taxonomy_source_cd", "SRC");
        row.put("taxonomy_jurisdiction_cd", "US");
        row.put("created_ts", OffsetDateTime.parse("2026-06-18T10:15:30Z"));
        row.put("created_by", "producer");
        row.put("updated_ts", OffsetDateTime.parse("2026-06-18T10:15:30Z"));
        row.put("updated_by", "producer");
        return row;
    }

    private static Map<String, Object> pairingRow(OffsetDateTime timestamp) {
        Map<String, Object> row = new HashMap<>();
        row.put("id", 101L);
        row.put("pairing_cd", "CUSTOMER_NAME_TO_ID");
        row.put("pairing_nm", "Customer Name To Id");
        row.put("schema_cd", "meta");
        row.put("object_cd", "customer");
        row.put("display_attribute_cd", "customer_nm");
        row.put("filter_attribute_cd", "customer_id");
        row.put("pairing_type_cd", "DISPLAY_TO_FILTER");
        row.put("lookup_strategy_cd", "CACHED_LOOKUP");
        row.put("lookup_inline_map_jsonb", "{\"Acme Corp\":\"CUST-100\"}");
        row.put("lookup_sql_template_txt", null);
        row.put("lookup_cache_enabled_flg", true);
        row.put("lookup_cache_ttl_seconds_nbr", 3600);
        row.put("cardinality_cd", "ONE_TO_ONE");
        row.put("is_bidirectional_flg", false);
        row.put("is_cross_engine_flg", false);
        row.put("filter_attribute_indexed_flg", true);
        row.put("filter_attribute_index_type_cd", "BTREE");
        row.put("performance_gain_pct_est_nbr", 20);
        row.put("ai_context_txt", "Resolve customer name to indexed customer id");
        row.put("client_id", "client-a");
        row.put("lifecycle_status_cd", "ACTIVE");
        row.put("governance_review_status_cd", "PENDING");
        row.put("version_nbr", 1);
        row.put("created_ts", timestamp);
        row.put("created_by", "producer");
        row.put("updated_ts", timestamp);
        row.put("updated_by", "producer");
        return row;
    }

    private static Map<String, Object> workflowTaskRow(OffsetDateTime timestamp) {
        Map<String, Object> row = new HashMap<>();
        row.put("id", 201L);
        row.put("task_type_cd", "ATTRIBUTE_PAIRING_REGISTRATION");
        row.put("entity_type_cd", "ATTRIBUTE_PAIRING");
        row.put("entity_ref", "CUSTOMER_NAME_TO_ID");
        row.put("task_status_cd", "PENDING");
        row.put("submitted_by", "producer");
        row.put("submitted_ts", timestamp);
        row.put("assigned_to", null);
        row.put("due_dt", null);
        row.put("description_txt", "Review attribute pairing CUSTOMER_NAME_TO_ID");
        row.put("client_id", "client-a");
        row.put("approved_by", null);
        row.put("approved_ts", null);
        row.put("approval_note_txt", null);
        return row;
    }

    private static Map<String, Object> metadataChangeRow(OffsetDateTime timestamp) {
        Map<String, Object> row = new HashMap<>();
        row.put("id", 301L);
        row.put("entity_type_cd", "ATTRIBUTE_PAIRING");
        row.put("entity_ref", "CUSTOMER_NAME_TO_ID");
        row.put("change_type_cd", "REGISTERED");
        row.put("changed_by", "producer");
        row.put("changed_ts", timestamp);
        row.put("old_value_json", null);
        row.put("new_value_json", null);
        row.put("change_reason_txt", "Registered attribute pairing CUSTOMER_NAME_TO_ID for customer");
        return row;
    }

    private static Map<String, Object> cacheRow(OffsetDateTime timestamp) {
        Map<String, Object> row = new HashMap<>();
        row.put("id", 401L);
        row.put("pairing_cd", "CUSTOMER_NAME_TO_ID");
        row.put("client_id", "client-a");
        row.put("display_value_txt", "Acme Corp");
        row.put("filter_value_txt", "CUST-100");
        row.put("is_one_to_many_flg", false);
        row.put("hit_count_nbr", 0L);
        row.put("last_hit_ts", null);
        row.put("cached_ts", timestamp);
        row.put("expires_ts", timestamp.plusSeconds(3600));
        return row;
    }

    @TestConfiguration
    static class AttributePairingWireThroughTestConfiguration {

        @Bean
        ObjectRegistrationWireThroughTest.RecordingNamedParameterJdbcTemplate recordingNamedParameterJdbcTemplate() {
            return new ObjectRegistrationWireThroughTest.RecordingNamedParameterJdbcTemplate();
        }

        @Bean
        RecordingAttributePairingPolicyClient recordingAttributePairingPolicyClient() {
            return new RecordingAttributePairingPolicyClient();
        }

        @Bean(name = "semanticLayerTransactionOperations")
        TransactionOperations semanticLayerTransactionOperations() {
            return new TransactionOperations() {
                @Override
                public <T> T execute(org.springframework.transaction.support.TransactionCallback<T> action) {
                    return action.doInTransaction(new SimpleTransactionStatus());
                }
            };
        }
    }

    static final class RecordingAttributePairingPolicyClient implements AttributePairingPolicyClient {

        private final List<AttributePairingPolicyRequestDto> recordedRequests = new ArrayList<>();
        private AttributePairingPolicyDecisionDto decision = new AttributePairingPolicyDecisionDto(true, null, null);

        void allow() {
            recordedRequests.clear();
            decision = new AttributePairingPolicyDecisionDto(true, null, null);
        }

        void deny(String code, String message) {
            recordedRequests.clear();
            decision = new AttributePairingPolicyDecisionDto(false, code, message);
        }

        List<AttributePairingPolicyRequestDto> recordedRequests() {
            return recordedRequests;
        }

        @Override
        public AttributePairingPolicyDecisionDto validateCrossEngine(AttributePairingPolicyRequestDto request) {
            recordedRequests.add(request);
            return decision;
        }
    }

    private static final class SimpleTransactionStatus implements TransactionStatus {

        @Override
        public boolean isNewTransaction() {
            return true;
        }

        @Override
        public boolean hasSavepoint() {
            return false;
        }

        @Override
        public void setRollbackOnly() {
        }

        @Override
        public boolean isRollbackOnly() {
            return false;
        }

        @Override
        public void flush() {
        }

        @Override
        public boolean isCompleted() {
            return false;
        }

        @Override
        public Object createSavepoint() {
            return null;
        }

        @Override
        public void rollbackToSavepoint(Object savepoint) {
        }

        @Override
        public void releaseSavepoint(Object savepoint) {
        }
    }
}
