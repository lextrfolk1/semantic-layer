package com.lextr.semanticlayer.api;

import com.lextr.semanticlayer.dao.ObjectExposureReadDao;
import com.lextr.semanticlayer.dto.ConsumptionExposureDto;
import com.lextr.semanticlayer.dto.ConsumptionLayerDto;
import com.lextr.semanticlayer.dto.ConsumptionPolicyDecisionDto;
import com.lextr.semanticlayer.dto.ConsumptionPolicyRequestDto;
import com.lextr.semanticlayer.dto.ConsumptionPromotionRequestDto;
import com.lextr.semanticlayer.dto.WorkflowApprovalRequestDto;
import com.lextr.semanticlayer.dto.WorkflowTaskResponseDto;
import com.lextr.semanticlayer.model.AttributeAccessGrantRecord;
import com.lextr.semanticlayer.model.AttributeExposureRecord;
import com.lextr.semanticlayer.model.ObjectExposureAccessAuditWriteRequest;
import com.lextr.semanticlayer.model.ObjectExposureRecord;
import com.lextr.semanticlayer.service.ConsumptionPolicyClient;
import com.lextr.semanticlayer.service.WorkflowApprovalService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.jdbc.datasource.AbstractDataSource;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener;
import org.springframework.test.context.support.DirtiesContextBeforeModesTestExecutionListener;
import org.springframework.test.context.support.DirtiesContextTestExecutionListener;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import javax.sql.DataSource;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringJUnitWebConfig(classes = {
        com.lextr.semanticlayer.SemanticLayerApplication.class,
        ConsumptionWireThroughTest.ConsumptionWireThroughTestConfiguration.class
})
@TestExecutionListeners(
        listeners = {
                DependencyInjectionTestExecutionListener.class,
                DirtiesContextBeforeModesTestExecutionListener.class,
                DirtiesContextTestExecutionListener.class
        },
        mergeMode = TestExecutionListeners.MergeMode.REPLACE_DEFAULTS
)
class ConsumptionWireThroughTest {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private RecordingNamedParameterJdbcTemplate jdbcTemplate;

    @Autowired
    private RecordingObjectExposureReadDao objectExposureReadDao;

    @Autowired
    private RecordingConsumptionPolicyClient consumptionPolicyClient;

    @Autowired
    private RecordingWorkflowApprovalService workflowApprovalService;

    @BeforeEach
    void resetDependencies() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        jdbcTemplate.reset();
        objectExposureReadDao.reset();
        consumptionPolicyClient.allow();
        workflowApprovalService.reset();
    }

    @Test
    void honorsConsumptionContractEndToEnd() throws Exception {
        UUID objectId = UUID.fromString("00000000-0000-0000-0000-000000000101");
        objectExposureReadDao.setObject(objectRecord(objectId));
        OffsetDateTime timestamp = OffsetDateTime.parse("2026-06-20T10:15:30Z");
        jdbcTemplate.setResponses(List.of(
                List.of(layerRow(timestamp)),
                List.of(layerRow(timestamp)),
                List.of(exposureRow(timestamp)),
                List.of(exposureRow(timestamp)),
                List.of(),
                List.of(workflowTaskRow(timestamp)),
                List.of(pendingPromotionRow(timestamp)),
                List.of(appliedPromotionRow(timestamp))
        ));

        mockMvc.perform(get("/api/consumption/layers")
                        .queryParam("client_id", "client-a")
                        .queryParam("lifecycle_status_cd", "ACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].client_id").value("client-a"))
                .andExpect(jsonPath("$[0].layer_cd").value("CL-01"))
                .andExpect(jsonPath("$[0].lifecycle_status_cd").value("ACTIVE"));

        mockMvc.perform(get("/api/consumption/layers/CL-01")
                        .queryParam("client_id", "client-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.client_id").value("client-a"))
                .andExpect(jsonPath("$.layer_cd").value("CL-01"));

        mockMvc.perform(get("/api/consumption/exposures")
                        .queryParam("client_id", "client-a")
                        .queryParam("object_id", objectId.toString())
                        .queryParam("structure_type_cd", "TECHNICAL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].client_id").value("client-a"))
                .andExpect(jsonPath("$[0].outbound_cd").value("OB-01"))
                .andExpect(jsonPath("$[0].attributes_jsonb[0]").value("ledger_id"))
                .andExpect(jsonPath("$[0].sdlc_status_cd").value("DEV"));

        mockMvc.perform(post("/api/consumption/exposures/101/promote")
                        .queryParam("client_id", "client-a")
                        .contentType("application/json")
                        .content("""
                                {
                                  "target_sdlc_status_cd": "QA",
                                  "promoted_by": "approver",
                                  "promotion_reason_txt": "Promote for QA"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.client_id").value("client-a"))
                .andExpect(jsonPath("$.outbound_cd").value("OB-01"))
                .andExpect(jsonPath("$.sdlc_status_cd").value("QA"))
                .andExpect(jsonPath("$.version_nbr").value(2));

        assertEquals(9, jdbcTemplate.recordedSqls().size());
        assertTrue(jdbcTemplate.recordedSqls().get(0).contains("FROM meta.consumption_layer"));
        assertTrue(jdbcTemplate.recordedSqls().get(1).contains("FROM meta.consumption_layer"));
        assertTrue(jdbcTemplate.recordedSqls().get(2).contains("FROM meta.consumption_outbound"));
        assertTrue(jdbcTemplate.recordedSqls().get(3).contains("FROM meta.consumption_outbound"));
        assertTrue(jdbcTemplate.recordedSqls().get(4).contains("FROM meta.consumption_promotion"));
        assertTrue(jdbcTemplate.recordedSqls().get(5).contains("INSERT INTO wkfl.workflow_task"));
        assertTrue(jdbcTemplate.recordedSqls().get(6).contains("INSERT INTO meta.consumption_promotion"));
        assertTrue(jdbcTemplate.recordedSqls().get(7).contains("UPDATE meta.consumption_promotion"));
        assertTrue(jdbcTemplate.recordedSqls().get(8).contains("INSERT INTO meta.metadata_change_history"));
        assertEquals("client-a", jdbcTemplate.recordedParameters().get(0).get("client_id"));
        assertEquals("CL-01", jdbcTemplate.recordedParameters().get(1).get("layer_cd"));
        assertEquals("client-a", jdbcTemplate.recordedParameters().get(2).get("client_id"));
        assertEquals(objectId, jdbcTemplate.recordedParameters().get(2).get("object_id"));
        assertEquals("client-a", jdbcTemplate.recordedParameters().get(3).get("client_id"));
        assertEquals(101L, jdbcTemplate.recordedParameters().get(3).get("outbound_id"));
        assertEquals("client-a", jdbcTemplate.recordedParameters().get(4).get("client_id"));
        assertEquals(101L, jdbcTemplate.recordedParameters().get(4).get("outbound_id"));
        assertEquals("client-a", jdbcTemplate.recordedParameters().get(5).get("client_id"));
        assertEquals("101", jdbcTemplate.recordedParameters().get(5).get("entity_ref"));
        assertEquals("PENDING", jdbcTemplate.recordedParameters().get(5).get("task_status_cd"));
        assertEquals("PENDING_APPROVAL", jdbcTemplate.recordedParameters().get(6).get("promotion_status_cd"));
        assertEquals(301L, jdbcTemplate.recordedParameters().get(7).get("id"));
        assertEquals("PROMOTED", jdbcTemplate.recordedParameters().get(8).get("change_type_cd"));

        assertEquals(1, consumptionPolicyClient.recordedRequests().size());
        ConsumptionPolicyRequestDto policyRequest = consumptionPolicyClient.recordedRequests().get(0);
        assertEquals("client-a", policyRequest.client_id());
        assertEquals(101L, policyRequest.exposure_id());
        assertEquals("DEV", policyRequest.source_sdlc_status_cd());
        assertEquals("QA", policyRequest.target_sdlc_status_cd());

        assertEquals(1, workflowApprovalService.recordedRequests().size());
        WorkflowApprovalRequestDto approvalRequest = workflowApprovalService.recordedRequests().get(0);
        assertEquals("client-a", approvalRequest.client_id());
        assertEquals("approver", approvalRequest.approved_by());
        assertEquals("Promote for QA", approvalRequest.approval_note_txt());
    }

    @Test
    void returnsUnprocessableEntityEndToEndWhenPolicyBlocksPromotion() throws Exception {
        UUID objectId = UUID.fromString("00000000-0000-0000-0000-000000000101");
        objectExposureReadDao.setObject(objectRecord(objectId));
        jdbcTemplate.setResponses(List.of(
                List.of(exposureRow(OffsetDateTime.parse("2026-06-20T10:15:30Z")))
        ));
        consumptionPolicyClient.deny("POL-CL-001", "Consumption layer blocked");

        mockMvc.perform(post("/api/consumption/exposures/101/promote")
                        .queryParam("client_id", "client-a")
                        .contentType("application/json")
                        .content("""
                                {
                                  "target_sdlc_status_cd": "QA",
                                  "promoted_by": "approver",
                                  "promotion_reason_txt": "Promote for QA"
                                }
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("POL-CL-001"))
                .andExpect(jsonPath("$.message").value("Consumption layer blocked"));

        assertEquals(1, jdbcTemplate.recordedSqls().size());
        assertTrue(jdbcTemplate.recordedSqls().get(0).contains("FROM meta.consumption_outbound"));
        assertEquals(1, consumptionPolicyClient.recordedRequests().size());
        assertEquals(0, workflowApprovalService.recordedRequests().size());
    }

    private static Map<String, Object> layerRow(OffsetDateTime timestamp) {
        Map<String, Object> row = new HashMap<>();
        row.put("id", 11L);
        row.put("client_id", "client-a");
        row.put("layer_cd", "CL-01");
        row.put("layer_nm", "Finance Layer");
        row.put("layer_desc_txt", "Finance outbound descriptor");
        row.put("layer_type_cd", "DATA_ASSET");
        row.put("lifecycle_status_cd", "ACTIVE");
        row.put("created_ts", timestamp);
        row.put("created_by", "owner");
        row.put("updated_ts", timestamp);
        row.put("updated_by", "owner");
        return row;
    }

    private static Map<String, Object> exposureRow(OffsetDateTime timestamp) {
        Map<String, Object> row = new HashMap<>();
        row.put("id", 101L);
        row.put("client_id", "client-a");
        row.put("layer_cd", "CL-01");
        row.put("object_id", 202L);
        row.put("outbound_cd", "OB-01");
        row.put("outbound_nm", "Outbound 01");
        row.put("structure_type_cd", "TECHNICAL");
        row.put("description_txt", "Technical exposure");
        row.put("attributes_jsonb", "[\"ledger_id\",\"company_id\"]");
        row.put("sdlc_status_cd", "DEV");
        row.put("version_nbr", 1);
        row.put("created_ts", timestamp);
        row.put("created_by", "owner");
        row.put("updated_ts", timestamp);
        row.put("updated_by", "owner");
        return row;
    }

    private static Map<String, Object> pendingPromotionRow(OffsetDateTime timestamp) {
        Map<String, Object> row = new HashMap<>();
        row.put("id", 301L);
        row.put("client_id", "client-a");
        row.put("outbound_id", 101L);
        row.put("source_sdlc_status_cd", "DEV");
        row.put("target_sdlc_status_cd", "QA");
        row.put("validation_status_cd", "VALIDATED");
        row.put("opa_decision_cd", "ALLOW");
        row.put("workflow_task_id", 501L);
        row.put("promotion_status_cd", "PENDING_APPROVAL");
        row.put("version_nbr", 1);
        row.put("applied_ts", null);
        row.put("applied_by", null);
        row.put("created_ts", timestamp);
        row.put("created_by", "approver");
        row.put("updated_ts", timestamp);
        row.put("updated_by", "approver");
        return row;
    }

    private static Map<String, Object> appliedPromotionRow(OffsetDateTime timestamp) {
        Map<String, Object> row = new HashMap<>();
        row.put("id", 301L);
        row.put("client_id", "client-a");
        row.put("outbound_id", 101L);
        row.put("source_sdlc_status_cd", "DEV");
        row.put("target_sdlc_status_cd", "QA");
        row.put("validation_status_cd", "VALIDATED");
        row.put("opa_decision_cd", "ALLOW");
        row.put("workflow_task_id", 501L);
        row.put("promotion_status_cd", "APPLIED");
        row.put("version_nbr", 2);
        row.put("applied_ts", timestamp);
        row.put("applied_by", "approver");
        row.put("created_ts", timestamp);
        row.put("created_by", "approver");
        row.put("updated_ts", timestamp);
        row.put("updated_by", "approver");
        return row;
    }

    private static Map<String, Object> workflowTaskRow(OffsetDateTime timestamp) {
        Map<String, Object> row = new HashMap<>();
        row.put("id", 501L);
        row.put("task_type_cd", "CONSUMPTION_PROMOTE");
        row.put("entity_type_cd", "CONSUMPTION_EXPOSURE");
        row.put("entity_ref", "101");
        row.put("task_status_cd", "PENDING");
        row.put("submitted_by", "approver");
        row.put("submitted_ts", timestamp);
        row.put("assigned_to", null);
        row.put("due_dt", null);
        row.put("description_txt", "Promote outbound exposure OB-01 to QA");
        row.put("client_id", "client-a");
        row.put("approved_by", null);
        row.put("approved_ts", null);
        row.put("approval_note_txt", null);
        return row;
    }

    private static ObjectExposureRecord objectRecord(UUID objectId) {
        return new ObjectExposureRecord(
                objectId,
                "client-a",
                "GL_BALANCE",
                "GL Balance",
                "GL Balance Override",
                "TABLE",
                "meta",
                UUID.fromString("00000000-0000-0000-0000-000000000201"),
                "CONFIDENTIAL",
                true,
                true,
                "ACTIVE",
                OffsetDateTime.parse("2026-06-16T10:15:30+05:30"),
                "producer",
                OffsetDateTime.parse("2026-06-17T10:15:30+05:30"),
                "platform"
        );
    }

    @TestConfiguration
    static class ConsumptionWireThroughTestConfiguration {

        @Bean
        @Primary
        RecordingNamedParameterJdbcTemplate recordingNamedParameterJdbcTemplate() {
            return new RecordingNamedParameterJdbcTemplate();
        }

        @Bean
        @Primary
        RecordingObjectExposureReadDao recordingObjectExposureReadDao() {
            return new RecordingObjectExposureReadDao();
        }

        @Bean
        @Primary
        RecordingConsumptionPolicyClient recordingConsumptionPolicyClient() {
            return new RecordingConsumptionPolicyClient();
        }

        @Bean
        @Primary
        RecordingWorkflowApprovalService recordingWorkflowApprovalService() {
            return new RecordingWorkflowApprovalService();
        }
    }

    static final class RecordingObjectExposureReadDao implements ObjectExposureReadDao {

        private ObjectExposureRecord object;

        void reset() {
            object = null;
        }

        void setObject(ObjectExposureRecord object) {
            this.object = object;
        }

        @Override
        public List<ObjectExposureRecord> findObjects(String clientId, String schemaCode, String lifecycleStatusCode) {
            return List.of();
        }

        @Override
        public Optional<ObjectExposureRecord> findObject(String clientId, UUID objectId) {
            return Optional.ofNullable(object);
        }

        @Override
        public Optional<ObjectExposureRecord> findObject(String schemaCode, String objectCode) {
            return Optional.ofNullable(object);
        }

        @Override
        public List<AttributeExposureRecord> findAttributes(String clientId, UUID objectId) {
            return List.of();
        }

        @Override
        public List<AttributeAccessGrantRecord> findAttributeAccessGrants(String clientId, String schemaCode, String objectCode, String attributeCode) {
            return List.of();
        }

        @Override
        public void insertAccessAudit(ObjectExposureAccessAuditWriteRequest request) {
        }
    }

    static final class RecordingConsumptionPolicyClient implements ConsumptionPolicyClient {

        private final List<ConsumptionPolicyRequestDto> recordedRequests = new ArrayList<>();
        private ConsumptionPolicyDecisionDto decision = new ConsumptionPolicyDecisionDto(true, null, null);

        void allow() {
            recordedRequests.clear();
            decision = new ConsumptionPolicyDecisionDto(true, null, null);
        }

        void deny(String code, String message) {
            recordedRequests.clear();
            decision = new ConsumptionPolicyDecisionDto(false, code, message);
        }

        List<ConsumptionPolicyRequestDto> recordedRequests() {
            return recordedRequests;
        }

        @Override
        public ConsumptionPolicyDecisionDto validatePromotion(ConsumptionPolicyRequestDto request) {
            recordedRequests.add(request);
            return decision;
        }
    }

    static final class RecordingWorkflowApprovalService implements WorkflowApprovalService {

        private final List<WorkflowApprovalRequestDto> recordedRequests = new ArrayList<>();

        void reset() {
            recordedRequests.clear();
        }

        List<WorkflowApprovalRequestDto> recordedRequests() {
            return recordedRequests;
        }

        @Override
        public WorkflowTaskResponseDto approveTask(Long id, WorkflowApprovalRequestDto request) {
            recordedRequests.add(request);
            OffsetDateTime timestamp = OffsetDateTime.parse("2026-06-20T10:15:30Z");
            return new WorkflowTaskResponseDto(
                    id,
                    "CONSUMPTION_PROMOTE",
                    "CONSUMPTION_EXPOSURE",
                    String.valueOf(id),
                    "APPROVED",
                    request.approved_by(),
                    timestamp,
                    null,
                    null,
                    request.approval_note_txt(),
                    request.client_id(),
                    request.approved_by(),
                    timestamp,
                    request.approval_note_txt()
            );
        }

        @Override
        public WorkflowTaskResponseDto rejectTask(Long id, Map<String, String> body) {
            throw new UnsupportedOperationException("Not used");
        }
    }

    static final class RecordingNamedParameterJdbcTemplate extends NamedParameterJdbcTemplate {

        private List<List<Map<String, Object>>> responses = List.of();
        private final List<String> recordedSqls = new ArrayList<>();
        private final List<Map<String, Object>> recordedParameters = new ArrayList<>();

        RecordingNamedParameterJdbcTemplate() {
            super(noOpDataSource());
        }

        void reset() {
            responses = List.of();
            recordedSqls.clear();
            recordedParameters.clear();
        }

        void setResponses(List<List<Map<String, Object>>> responses) {
            this.responses = new ArrayList<>(responses);
        }

        List<String> recordedSqls() {
            return recordedSqls;
        }

        List<Map<String, Object>> recordedParameters() {
            return recordedParameters;
        }

        @Override
        public <T> List<T> query(String sql, SqlParameterSource paramSource, RowMapper<T> rowMapper) {
            record(sql, paramSource);
            List<Map<String, Object>> rows = responses.isEmpty() ? List.of() : responses.remove(0);
            return rows.stream().map(row -> mapRow(rowMapper, row)).toList();
        }

        @Override
        public int update(String sql, SqlParameterSource paramSource) {
            record(sql, paramSource);
            return 1;
        }

        private void record(String sql, SqlParameterSource paramSource) {
            recordedSqls.add(sql);
            if (paramSource instanceof MapSqlParameterSource source) {
                recordedParameters.add(new HashMap<>(source.getValues()));
            } else {
                recordedParameters.add(new HashMap<>());
            }
        }

        private <T> T mapRow(RowMapper<T> rowMapper, Map<String, Object> row) {
            try {
                return rowMapper.mapRow(resultSet(row), 0);
            } catch (SQLException exception) {
                throw new RuntimeException(exception);
            }
        }

        private ResultSet resultSet(Map<String, Object> row) {
            return (ResultSet) Proxy.newProxyInstance(
                    ResultSet.class.getClassLoader(),
                    new Class[]{ResultSet.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "getString" -> (String) row.get(args[0]);
                        case "getBoolean" -> {
                            Object value = row.get(args[0]);
                            yield value != null && (Boolean) value;
                        }
                        case "getObject" -> {
                            if (args.length == 1) {
                                yield row.get(args[0]);
                            }
                            Object value = row.get(args[0]);
                            yield value == null ? null : ((Class<?>) args[1]).cast(value);
                        }
                        case "close" -> null;
                        case "wasNull" -> false;
                        default -> defaultValue(method.getReturnType());
                    }
            );
        }

        private Object defaultValue(Class<?> returnType) {
            if (!returnType.isPrimitive()) {
                return null;
            }
            if (returnType == boolean.class) {
                return false;
            }
            if (returnType == byte.class) {
                return (byte) 0;
            }
            if (returnType == short.class) {
                return (short) 0;
            }
            if (returnType == int.class) {
                return 0;
            }
            if (returnType == long.class) {
                return 0L;
            }
            if (returnType == float.class) {
                return 0F;
            }
            if (returnType == double.class) {
                return 0D;
            }
            if (returnType == char.class) {
                return '\0';
            }
            return null;
        }

        private static DataSource noOpDataSource() {
            return new AbstractDataSource() {
                @Override
                public Connection getConnection() {
                    throw new UnsupportedOperationException("Not used in tests");
                }

                @Override
                public Connection getConnection(String username, String password) {
                    throw new UnsupportedOperationException("Not used in tests");
                }
            };
        }
    }
}
