package com.lextr.semanticlayer.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lextr.semanticlayer.dto.RelationshipPolicyDecisionDto;
import com.lextr.semanticlayer.dto.RelationshipPolicyRequestDto;
import com.lextr.semanticlayer.model.RelationshipGraphProjectionRequest;
import com.lextr.semanticlayer.service.RelationshipGraphProjectionClient;
import com.lextr.semanticlayer.service.RelationshipPolicyClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener;
import org.springframework.test.context.support.DirtiesContextBeforeModesTestExecutionListener;
import org.springframework.test.context.support.DirtiesContextTestExecutionListener;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
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
        RelationshipRegistrationWireThroughTest.RelationshipRegistrationWireThroughTestConfiguration.class
})
@TestExecutionListeners(
        listeners = {
                DependencyInjectionTestExecutionListener.class,
                DirtiesContextBeforeModesTestExecutionListener.class,
                DirtiesContextTestExecutionListener.class
        },
        mergeMode = TestExecutionListeners.MergeMode.REPLACE_DEFAULTS
)
class RelationshipRegistrationWireThroughTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private ObjectRegistrationWireThroughTest.RecordingNamedParameterJdbcTemplate jdbcTemplate;

    @Autowired
    private RecordingRelationshipPolicyClient relationshipPolicyClient;

    @Autowired
    private RecordingRelationshipGraphProjectionClient relationshipGraphProjectionClient;

    @BeforeEach
    void resetDependencies() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        jdbcTemplate.reset();
        relationshipPolicyClient.allow();
        relationshipGraphProjectionClient.reset(true);
    }

    @Test
    void honorsRelationshipRegistrationContractEndToEnd() throws Exception {
        UUID parentConnectionId = UUID.fromString("00000000-0000-0000-0000-000000000201");
        UUID childConnectionId = UUID.fromString("00000000-0000-0000-0000-000000000202");
        UUID workflowTaskId = UUID.fromString("00000000-0000-0000-0000-000000000301");
        UUID changeHistoryId = UUID.fromString("00000000-0000-0000-0000-000000000401");
        OffsetDateTime timestamp = OffsetDateTime.parse("2026-06-18T04:45:30Z");
        jdbcTemplate.setResponses(List.of(
                List.of(objectRow(parentConnectionId, "meta", "gl_balance")),
                List.of(objectRow(childConnectionId, "meta", "ledger")),
                List.of(connectionRow(parentConnectionId, "POSTGRES", "PG_META")),
                List.of(connectionRow(childConnectionId, "POSTGRES", "PG_LEDGER")),
                List.of(relationshipRow(null, timestamp, timestamp)),
                List.of(workflowTaskRow(workflowTaskId, timestamp)),
                List.of(metadataChangeRow(changeHistoryId, timestamp)),
                List.of(relationshipRow(timestamp, timestamp, timestamp))
        ));

        MvcResult result = mockMvc.perform(post("/api/relationships")
                        .contentType("application/json")
                        .content(validRequestJson(false)))
                .andReturn();

        Throwable resolvedException = result.getResolvedException();
        Throwable cause = resolvedException == null ? null : resolvedException.getCause();
        assertEquals(201, result.getResponse().getStatus(), resolvedException + " cause=" + cause);
        Map<String, Object> response = OBJECT_MAPPER.readValue(
                result.getResponse().getContentAsString(),
                new TypeReference<>() {
                }
        );
        assertEquals(101, response.get("id"));
        assertEquals("GL_TO_LEDGER", response.get("relationship_cd"));
        assertEquals("meta", response.get("parent_schema_cd"));
        assertEquals("gl_balance", response.get("parent_object_cd"));
        assertEquals("ledger", response.get("child_object_cd"));
        assertEquals("FOREIGN_KEY", response.get("relationship_type_cd"));
        assertEquals(false, response.get("is_cross_engine_flg"));
        assertEquals("2026-06-18T04:45:30Z", response.get("neo4j_synced_ts"));
        assertEquals("DRAFT", response.get("lifecycle_status_cd"));

        assertEquals(8, jdbcTemplate.recordedSqls().size());
        assertTrue(jdbcTemplate.recordedSqls().get(0).contains("FROM meta.object_catalog"));
        assertTrue(jdbcTemplate.recordedSqls().get(2).contains("FROM meta.data_connection"));
        assertTrue(jdbcTemplate.recordedSqls().get(4).contains("INSERT INTO meta.semantic_relationship_catalog"));
        assertTrue(jdbcTemplate.recordedSqls().get(5).contains("INSERT INTO wkfl.workflow_task"));
        assertTrue(jdbcTemplate.recordedSqls().get(6).contains("INSERT INTO meta.metadata_change_history"));
        assertTrue(jdbcTemplate.recordedSqls().get(7).contains("UPDATE meta.semantic_relationship_catalog"));

        assertEquals("meta", jdbcTemplate.recordedParameters().get(0).get("schema_cd"));
        assertEquals("gl_balance", jdbcTemplate.recordedParameters().get(0).get("object_cd"));
        assertEquals("client-a", jdbcTemplate.recordedParameters().get(2).get("client_id"));
        assertEquals(parentConnectionId, jdbcTemplate.recordedParameters().get(2).get("connection_id"));
        assertEquals("GL_TO_LEDGER", jdbcTemplate.recordedParameters().get(4).get("relationship_cd"));
        assertEquals("DRAFT", jdbcTemplate.recordedParameters().get(4).get("lifecycle_status_cd"));
        assertEquals("PENDING_APPROVAL", jdbcTemplate.recordedParameters().get(5).get("task_status_cd"));
        assertEquals("REGISTERED", jdbcTemplate.recordedParameters().get(6).get("change_type_cd"));
        assertEquals("GL_TO_LEDGER", jdbcTemplate.recordedParameters().get(7).get("relationship_cd"));
        assertTrue(jdbcTemplate.recordedParameters().get(7).get("neo4j_synced_ts") instanceof OffsetDateTime);

        assertEquals(1, relationshipPolicyClient.recordedRequests().size());
        RelationshipPolicyRequestDto policyRequest = relationshipPolicyClient.recordedRequests().get(0);
        assertEquals("client-a", policyRequest.client_id());
        assertEquals("GL_TO_LEDGER", policyRequest.relationship_cd());
        assertEquals("POSTGRES", policyRequest.parent_engine_cd());
        assertEquals("POSTGRES", policyRequest.child_engine_cd());
        assertTrue(!policyRequest.is_cross_engine_flg());

        assertEquals(1, relationshipGraphProjectionClient.recordedRequests().size());
        RelationshipGraphProjectionRequest projectionRequest = relationshipGraphProjectionClient.recordedRequests().get(0);
        assertEquals("GL_TO_LEDGER", projectionRequest.relationship_cd());
        assertEquals("POSTGRES", projectionRequest.parent_engine_cd());
        assertEquals("POSTGRES", projectionRequest.child_engine_cd());
        assertEquals(jdbcTemplate.recordedParameters().get(7).get("neo4j_synced_ts"), projectionRequest.projected_ts());
    }

    @Test
    void returnsUnprocessableEntityEndToEndWhenPolicyBlocksCrossEngineRelationship() throws Exception {
        UUID parentConnectionId = UUID.fromString("00000000-0000-0000-0000-000000000201");
        UUID childConnectionId = UUID.fromString("00000000-0000-0000-0000-000000000202");
        jdbcTemplate.setResponses(List.of(
                List.of(objectRow(parentConnectionId, "meta", "gl_balance")),
                List.of(objectRow(childConnectionId, "meta", "ledger")),
                List.of(connectionRow(parentConnectionId, "POSTGRES", "PG_META")),
                List.of(connectionRow(childConnectionId, "CLICKHOUSE", "CH_LEDGER"))
        ));
        relationshipPolicyClient.deny("POL-CE-001", "Cross-engine relationships are not allowed");

        mockMvc.perform(post("/api/relationships")
                        .contentType("application/json")
                        .content(validRequestJson(false)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("POL-CE-001"))
                .andExpect(jsonPath("$.message").value("Cross-engine relationships are not allowed"));

        assertEquals(4, jdbcTemplate.recordedSqls().size());
        assertTrue(jdbcTemplate.recordedSqls().get(0).contains("FROM meta.object_catalog"));
        assertTrue(jdbcTemplate.recordedSqls().get(2).contains("FROM meta.data_connection"));
        assertEquals(1, relationshipPolicyClient.recordedRequests().size());
        assertTrue(relationshipPolicyClient.recordedRequests().get(0).is_cross_engine_flg());
        assertEquals(0, relationshipGraphProjectionClient.recordedRequests().size());
    }

    private static String validRequestJson(boolean crossEngineFlag) {
        return """
                {
                  "relationship_cd": "GL_TO_LEDGER",
                  "parent_schema_cd": "meta",
                  "parent_object_cd": "gl_balance",
                  "parent_attribute_cd": "ledger_id",
                  "child_schema_cd": "meta",
                  "child_object_cd": "ledger",
                  "child_attribute_cd": "ledger_id",
                  "relationship_type_cd": "FOREIGN_KEY",
                  "cardinality_cd": "MANY_TO_ONE",
                  "join_type_cd": "INNER",
                  "is_enforced_flg": true,
                  "is_nullable_flg": false,
                  "is_cross_engine_flg": %s,
                  "relationship_desc": "GL balances map to ledger master rows",
                  "ai_join_guidance_txt": "Join on ledger identifier",
                  "registered_by": "producer"
                }
                """.formatted(crossEngineFlag);
    }

    private static Map<String, Object> objectRow(UUID connectionId, String schemaCode, String objectCode) {
        Map<String, Object> row = new HashMap<>();
        row.put("object_id", UUID.nameUUIDFromBytes((schemaCode + "." + objectCode).getBytes()));
        row.put("client_id", "client-a");
        row.put("object_cd", objectCode);
        row.put("object_nm", objectCode.toUpperCase());
        row.put("effective_object_nm", objectCode.toUpperCase());
        row.put("object_type_cd", "TABLE");
        row.put("schema_cd", schemaCode);
        row.put("connection_id", connectionId);
        row.put("lifecycle_status_cd", "ACTIVE");
        row.put("created_ts", OffsetDateTime.parse("2026-06-17T10:15:30Z"));
        row.put("created_by", "producer");
        row.put("updated_ts", OffsetDateTime.parse("2026-06-17T10:15:30Z"));
        row.put("updated_by", "producer");
        return row;
    }

    private static Map<String, Object> connectionRow(UUID connectionId, String engineCode, String connectionCode) {
        Map<String, Object> row = new HashMap<>();
        row.put("connection_id", connectionId);
        row.put("connection_cd", connectionCode);
        row.put("connection_nm", connectionCode);
        row.put("effective_connection_nm", connectionCode);
        row.put("engine_cd", engineCode);
        row.put("connection_type_cd", "WAREHOUSE");
        row.put("source_mode_cd", "BATCH");
        row.put("host_nm", "localhost");
        row.put("port_nbr", 5432);
        row.put("database_nm", "lextr");
        row.put("schema_nm_default", "meta");
        row.put("is_default_flg", false);
        row.put("is_active_flg", true);
        row.put("created_ts", OffsetDateTime.parse("2026-06-17T10:15:30Z"));
        row.put("created_by", "producer");
        row.put("updated_ts", OffsetDateTime.parse("2026-06-17T10:15:30Z"));
        row.put("updated_by", "producer");
        return row;
    }

    private static Map<String, Object> relationshipRow(OffsetDateTime neo4jSyncedTs,
                                                       OffsetDateTime createdTs,
                                                       OffsetDateTime updatedTs) {
        Map<String, Object> row = new HashMap<>();
        row.put("id", 101L);
        row.put("relationship_cd", "GL_TO_LEDGER");
        row.put("parent_schema_cd", "meta");
        row.put("parent_object_cd", "gl_balance");
        row.put("parent_attribute_cd", "ledger_id");
        row.put("child_schema_cd", "meta");
        row.put("child_object_cd", "ledger");
        row.put("child_attribute_cd", "ledger_id");
        row.put("relationship_type_cd", "FOREIGN_KEY");
        row.put("cardinality_cd", "MANY_TO_ONE");
        row.put("join_type_cd", "INNER");
        row.put("is_enforced_flg", true);
        row.put("is_nullable_flg", false);
        row.put("is_cross_engine_flg", false);
        row.put("relationship_desc", "GL balances map to ledger master rows");
        row.put("ai_join_guidance_txt", "Join on ledger identifier");
        row.put("neo4j_synced_ts", neo4jSyncedTs);
        row.put("lifecycle_status_cd", "DRAFT");
        row.put("created_ts", createdTs);
        row.put("created_by", "producer");
        row.put("updated_ts", updatedTs);
        row.put("updated_by", "producer");
        return row;
    }

    private static Map<String, Object> workflowTaskRow(UUID workflowTaskId, OffsetDateTime timestamp) {
        Map<String, Object> row = new HashMap<>();
        row.put("workflow_task_id", workflowTaskId);
        row.put("client_id", "client-a");
        row.put("workflow_type_cd", "RELATIONSHIP_REGISTRATION");
        row.put("entity_type_cd", "RELATIONSHIP");
        row.put("entity_id", UUID.fromString("e6f58d10-7ef3-34d4-b62d-01a331dff8f5"));
        row.put("task_status_cd", "PENDING_APPROVAL");
        row.put("created_ts", timestamp);
        row.put("created_by", "producer");
        row.put("updated_ts", timestamp);
        row.put("updated_by", "producer");
        return row;
    }

    private static Map<String, Object> metadataChangeRow(UUID changeHistoryId, OffsetDateTime timestamp) {
        Map<String, Object> row = new HashMap<>();
        row.put("change_history_id", changeHistoryId);
        row.put("client_id", "client-a");
        row.put("entity_type_cd", "RELATIONSHIP");
        row.put("entity_id", UUID.fromString("e6f58d10-7ef3-34d4-b62d-01a331dff8f5"));
        row.put("change_type_cd", "REGISTERED");
        row.put("change_summary_txt", "Registered relationship GL_TO_LEDGER");
        row.put("created_ts", timestamp);
        row.put("created_by", "producer");
        return row;
    }

    @TestConfiguration
    static class RelationshipRegistrationWireThroughTestConfiguration {

        @Bean
        ObjectRegistrationWireThroughTest.RecordingNamedParameterJdbcTemplate recordingNamedParameterJdbcTemplate() {
            return new ObjectRegistrationWireThroughTest.RecordingNamedParameterJdbcTemplate();
        }

        @Bean
        RecordingRelationshipPolicyClient recordingRelationshipPolicyClient() {
            return new RecordingRelationshipPolicyClient();
        }

        @Bean
        @Primary
        RecordingRelationshipGraphProjectionClient recordingRelationshipGraphProjectionClient() {
            return new RecordingRelationshipGraphProjectionClient();
        }
    }

    static final class RecordingRelationshipPolicyClient implements RelationshipPolicyClient {

        private final List<RelationshipPolicyRequestDto> recordedRequests = new ArrayList<>();
        private RelationshipPolicyDecisionDto decision = new RelationshipPolicyDecisionDto(true, null, null);

        void allow() {
            recordedRequests.clear();
            decision = new RelationshipPolicyDecisionDto(true, null, null);
        }

        void deny(String code, String message) {
            recordedRequests.clear();
            decision = new RelationshipPolicyDecisionDto(false, code, message);
        }

        List<RelationshipPolicyRequestDto> recordedRequests() {
            return recordedRequests;
        }

        @Override
        public RelationshipPolicyDecisionDto validateCrossEngine(RelationshipPolicyRequestDto request) {
            recordedRequests.add(request);
            return decision;
        }
    }

    static final class RecordingRelationshipGraphProjectionClient implements RelationshipGraphProjectionClient {

        private final List<RelationshipGraphProjectionRequest> recordedRequests = new ArrayList<>();
        private boolean result = true;

        void reset(boolean result) {
            recordedRequests.clear();
            this.result = result;
        }

        List<RelationshipGraphProjectionRequest> recordedRequests() {
            return recordedRequests;
        }

        @Override
        public boolean projectRelationship(RelationshipGraphProjectionRequest request) {
            recordedRequests.add(request);
            return result;
        }
    }
}
