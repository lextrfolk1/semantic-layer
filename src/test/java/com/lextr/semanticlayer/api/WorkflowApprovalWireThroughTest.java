package com.lextr.semanticlayer.api;

import com.lextr.semanticlayer.dto.WorkflowApprovalRequestDto;
import com.lextr.semanticlayer.dto.WorkflowTaskResponseDto;
import com.lextr.semanticlayer.dto.WorkflowPolicyRequestDto;
import com.lextr.semanticlayer.dto.WorkflowPolicyDecisionDto;
import com.lextr.semanticlayer.service.WorkflowPolicyClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
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
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.web.context.WebApplicationContext;

import javax.sql.DataSource;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringJUnitWebConfig(classes = {
        com.lextr.semanticlayer.SemanticLayerApplication.class,
        WorkflowApprovalWireThroughTest.WorkflowApprovalWireThroughTestConfiguration.class
})
@TestExecutionListeners(
        listeners = {
                DependencyInjectionTestExecutionListener.class,
                DirtiesContextBeforeModesTestExecutionListener.class,
                DirtiesContextTestExecutionListener.class
        },
        mergeMode = TestExecutionListeners.MergeMode.REPLACE_DEFAULTS
)
public class WorkflowApprovalWireThroughTest {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private RecordingNamedParameterJdbcTemplate jdbcTemplate;

    @Autowired
    private RecordingWorkflowPolicyClient workflowPolicyClient;

    @BeforeEach
    void resetDependencies() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        jdbcTemplate.reset();
        workflowPolicyClient.allow();
    }

    @Test
    void approvesFilterLookupRegistrationTaskEndToEnd() throws Exception {
        OffsetDateTime submittedTs = OffsetDateTime.parse("2026-06-18T10:15:30Z");
        
        // Setup mock DB responses
        // 1. findTaskById
        Map<String, Object> taskRow = pendingTaskRow(301L, "FILTER_LOOKUP_REGISTRATION", "FILTER_LOOKUP", "LEDGER_SCOPE", submittedTs);
        
        // 2. approveTask
        Map<String, Object> approvedTaskRow = new HashMap<>(taskRow);
        approvedTaskRow.put("task_status_cd", "APPROVED");
        approvedTaskRow.put("approved_by", "approver");
        approvedTaskRow.put("approved_ts", OffsetDateTime.parse("2026-06-18T12:00:00Z"));
        approvedTaskRow.put("approval_note_txt", "approved");

        // 3. insertMetadataChangeHistory
        Map<String, Object> auditRow = new HashMap<>();
        auditRow.put("id", 401L);
        auditRow.put("entity_type_cd", "FILTER_LOOKUP");
        auditRow.put("entity_ref", "LEDGER_SCOPE");
        auditRow.put("change_type_cd", "APPROVED");
        auditRow.put("changed_by", "approver");
        auditRow.put("changed_ts", OffsetDateTime.parse("2026-06-18T12:00:00Z"));
        auditRow.put("old_value_json", null);
        auditRow.put("new_value_json", null);
        auditRow.put("change_reason_txt", "Approved workflow task 301");

        jdbcTemplate.setResponses(List.of(
                List.of(taskRow),
                List.of(approvedTaskRow),
                List.of(Map.of("id", 999L, "lookup_cd", "LEDGER_SCOPE", "governance_status_cd", "APPROVED")),
                List.of(auditRow)
        ));

        mockMvc.perform(post("/api/workflow-tasks/301/approve")
                        .contentType("application/json")
                        .content("""
                                {
                                  "client_id": "client-a",
                                  "approved_by": "approver",
                                  "approval_note_txt": "approved"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(301))
                .andExpect(jsonPath("$.task_type_cd").value("FILTER_LOOKUP_REGISTRATION"))
                .andExpect(jsonPath("$.task_status_cd").value("APPROVED"))
                .andExpect(jsonPath("$.approved_by").value("approver"));

        // Verify JDBC operations
        assertEquals(4, jdbcTemplate.recordedSqls().size());
        assertTrue(jdbcTemplate.recordedSqls().get(0).contains("FROM wkfl.workflow_task"));
        assertTrue(jdbcTemplate.recordedSqls().get(1).contains("UPDATE wkfl.workflow_task"));
        assertTrue(jdbcTemplate.recordedSqls().get(2).contains("UPDATE meta.semantic_filter_lookup"));
        assertTrue(jdbcTemplate.recordedSqls().get(3).contains("INSERT INTO meta.metadata_change_history"));

        // Verify approveLookup ran as a returning query
        assertEquals(0, jdbcTemplate.recordedUpdates().size());
        assertEquals("client-a", jdbcTemplate.recordedParameters().get(2).get("client_id"));
        
        // Verify policy requests
        assertEquals(1, workflowPolicyClient.recordedRequests().size());
        assertEquals("approver", workflowPolicyClient.recordedRequests().get(0).approved_by());
    }

    @Test
    void approvesAttributeOverrideTaskEndToEnd() throws Exception {
        OffsetDateTime submittedTs = OffsetDateTime.parse("2026-06-18T10:15:30Z");
        
        // Setup mock DB responses
        // 1. findTaskById
        Map<String, Object> taskRow = pendingTaskRow(302L, "ATTRIBUTE_LOGICAL_NAME_OVERRIDE", "ATTRIBUTE", "402", submittedTs);
        
        // 2. approveTask
        Map<String, Object> approvedTaskRow = new HashMap<>(taskRow);
        approvedTaskRow.put("task_status_cd", "APPROVED");
        approvedTaskRow.put("approved_by", "approver");
        approvedTaskRow.put("approved_ts", OffsetDateTime.parse("2026-06-18T12:00:00Z"));
        approvedTaskRow.put("approval_note_txt", "override approved");

        // 3. insertMetadataChangeHistory
        Map<String, Object> auditRow = new HashMap<>();
        auditRow.put("id", 402L);
        auditRow.put("entity_type_cd", "ATTRIBUTE");
        auditRow.put("entity_ref", "402");
        auditRow.put("change_type_cd", "APPROVED");
        auditRow.put("changed_by", "approver");
        auditRow.put("changed_ts", OffsetDateTime.parse("2026-06-18T12:00:00Z"));
        auditRow.put("old_value_json", null);
        auditRow.put("new_value_json", null);
        auditRow.put("change_reason_txt", "Approved workflow task 302");

        jdbcTemplate.setResponses(List.of(
                List.of(taskRow),
                List.of(approvedTaskRow),
                List.of(auditRow)
        ));

        mockMvc.perform(post("/api/workflow-tasks/302/approve")
                        .contentType("application/json")
                        .content("""
                                {
                                  "client_id": "client-a",
                                  "approved_by": "approver",
                                  "approval_note_txt": "override approved"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(302))
                .andExpect(jsonPath("$.task_type_cd").value("ATTRIBUTE_LOGICAL_NAME_OVERRIDE"))
                .andExpect(jsonPath("$.task_status_cd").value("APPROVED"));

        // Verify update SQL (approveAttributeOverride) was executed
        assertEquals(1, jdbcTemplate.recordedUpdates().size());
        assertTrue(jdbcTemplate.recordedUpdates().get(0).contains("UPDATE meta.attribute_logical_name_override"));
    }

    @Test
    void approvesFilterLookupValueTaskEndToEnd() throws Exception {
        OffsetDateTime submittedTs = OffsetDateTime.parse("2026-06-18T10:15:30Z");
        
        // Setup mock DB responses
        // 1. findTaskById
        Map<String, Object> taskRow = pendingTaskRow(303L, "FILTER_LOOKUP_VALUE", "FILTER_LOOKUP", "LEDGER_SCOPE:USD", submittedTs);
        
        // 2. approveTask
        Map<String, Object> approvedTaskRow = new HashMap<>(taskRow);
        approvedTaskRow.put("task_status_cd", "APPROVED");
        approvedTaskRow.put("approved_by", "approver");
        approvedTaskRow.put("approved_ts", OffsetDateTime.parse("2026-06-18T12:00:00Z"));
        approvedTaskRow.put("approval_note_txt", "value approved");

        // 3. insertMetadataChangeHistory
        Map<String, Object> auditRow = new HashMap<>();
        auditRow.put("id", 403L);
        auditRow.put("entity_type_cd", "FILTER_LOOKUP");
        auditRow.put("entity_ref", "LEDGER_SCOPE:USD");
        auditRow.put("change_type_cd", "APPROVED");
        auditRow.put("changed_by", "approver");
        auditRow.put("changed_ts", OffsetDateTime.parse("2026-06-18T12:00:00Z"));
        auditRow.put("old_value_json", null);
        auditRow.put("new_value_json", null);
        auditRow.put("change_reason_txt", "Approved workflow task 303");

        jdbcTemplate.setResponses(List.of(
                List.of(taskRow),
                List.of(approvedTaskRow),
                List.of(auditRow)
        ));

        mockMvc.perform(post("/api/workflow-tasks/303/approve")
                        .contentType("application/json")
                        .content("""
                                {
                                  "client_id": "client-a",
                                  "approved_by": "approver",
                                  "approval_note_txt": "value approved"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(303))
                .andExpect(jsonPath("$.task_status_cd").value("APPROVED"));

        // Verify update SQL (approveFilterLookupValue) was executed
        assertEquals(1, jdbcTemplate.recordedUpdates().size());
        assertTrue(jdbcTemplate.recordedUpdates().get(0).contains("UPDATE meta.filter_lookup_value"));
    }

    @Test
    void rejectsFilterLookupRegistrationTaskEndToEnd() throws Exception {
        OffsetDateTime submittedTs = OffsetDateTime.parse("2026-06-18T10:15:30Z");
        Map<String, Object> taskRow = pendingTaskRow(304L, "FILTER_LOOKUP_REGISTRATION", "FILTER_LOOKUP", "LEDGER_SCOPE", submittedTs);

        Map<String, Object> rejectedTaskRow = new HashMap<>(taskRow);
        rejectedTaskRow.put("task_status_cd", "REJECTED");
        rejectedTaskRow.put("approved_by", "rejecter");
        rejectedTaskRow.put("approved_ts", OffsetDateTime.parse("2026-06-18T12:00:00Z"));
        rejectedTaskRow.put("approval_note_txt", "not approved");

        Map<String, Object> auditRow = new HashMap<>();
        auditRow.put("id", 404L);
        auditRow.put("entity_type_cd", "FILTER_LOOKUP");
        auditRow.put("entity_ref", "LEDGER_SCOPE");
        auditRow.put("change_type_cd", "REJECTED");
        auditRow.put("changed_by", "rejecter");
        auditRow.put("changed_ts", OffsetDateTime.parse("2026-06-18T12:00:00Z"));
        auditRow.put("old_value_json", null);
        auditRow.put("new_value_json", null);
        auditRow.put("change_reason_txt", "Rejected workflow task 304");

        jdbcTemplate.setResponses(List.of(
                List.of(taskRow),
                List.of(rejectedTaskRow),
                List.of(auditRow)
        ));

        mockMvc.perform(post("/api/workflow-tasks/304/reject")
                        .contentType("application/json")
                        .content("""
                                {
                                  "rejected_by": "rejecter",
                                  "rejection_note_txt": "not approved"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(304))
                .andExpect(jsonPath("$.task_type_cd").value("FILTER_LOOKUP_REGISTRATION"))
                .andExpect(jsonPath("$.task_status_cd").value("REJECTED"))
                .andExpect(jsonPath("$.approved_by").value("rejecter"))
                .andExpect(jsonPath("$.approval_note_txt").value("not approved"));

        assertEquals(3, jdbcTemplate.recordedSqls().size());
        assertTrue(jdbcTemplate.recordedSqls().get(0).contains("FROM wkfl.workflow_task"));
        assertTrue(jdbcTemplate.recordedSqls().get(1).contains("UPDATE wkfl.workflow_task"));
        assertTrue(jdbcTemplate.recordedSqls().get(2).contains("INSERT INTO meta.metadata_change_history"));

        assertEquals(1, jdbcTemplate.recordedUpdates().size());
        assertTrue(jdbcTemplate.recordedUpdates().get(0).contains("UPDATE meta.semantic_filter_lookup"));
        assertEquals("client-a", jdbcTemplate.recordedUpdateParameters().get(0).get("client_id"));
        assertEquals("rejecter", jdbcTemplate.recordedUpdateParameters().get(0).get("updated_by"));
        assertEquals("SUSPENDED", jdbcTemplate.recordedUpdateParameters().get(0).get("governance_status_cd"));
    }

    @Test
    void returnsUnprocessableEntityWhenPolicyViolated() throws Exception {
        OffsetDateTime submittedTs = OffsetDateTime.parse("2026-06-18T10:15:30Z");
        Map<String, Object> taskRow = pendingTaskRow(301L, "FILTER_LOOKUP_REGISTRATION", "FILTER_LOOKUP", "LEDGER_SCOPE", submittedTs);

        jdbcTemplate.setResponses(List.of(List.of(taskRow)));
        workflowPolicyClient.deny("POL-SV-002", "Self-approval is forbidden");

        mockMvc.perform(post("/api/workflow-tasks/301/approve")
                        .contentType("application/json")
                        .content("""
                                {
                                  "client_id": "client-a",
                                  "approved_by": "producer",
                                  "approval_note_txt": "approve"
                                }
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("POL-SV-002"))
                .andExpect(jsonPath("$.message").value("Self-approval is forbidden"));
    }

    private static Map<String, Object> pendingTaskRow(Long id, String typeCd, String entityType, String entityRef, OffsetDateTime submittedTs) {
        Map<String, Object> row = new HashMap<>();
        row.put("id", id);
        row.put("task_type_cd", typeCd);
        row.put("entity_type_cd", entityType);
        row.put("entity_ref", entityRef);
        row.put("task_status_cd", "PENDING");
        row.put("submitted_by", "producer");
        row.put("submitted_ts", submittedTs);
        row.put("assigned_to", null);
        row.put("due_dt", LocalDate.parse("2026-09-16"));
        row.put("description_txt", "Review task");
        row.put("client_id", "client-a");
        row.put("approved_by", null);
        row.put("approved_ts", null);
        row.put("approval_note_txt", null);
        return row;
    }

    @TestConfiguration
    static class WorkflowApprovalWireThroughTestConfiguration {

        @Bean
        RecordingNamedParameterJdbcTemplate recordingNamedParameterJdbcTemplate() {
            return new RecordingNamedParameterJdbcTemplate();
        }

        @Bean
        RecordingWorkflowPolicyClient recordingWorkflowPolicyClient() {
            return new RecordingWorkflowPolicyClient();
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

    static final class RecordingWorkflowPolicyClient implements WorkflowPolicyClient {
        private final List<WorkflowPolicyRequestDto> recordedRequests = new ArrayList<>();
        private WorkflowPolicyDecisionDto decision = new WorkflowPolicyDecisionDto(true, null, null);

        void allow() {
            recordedRequests.clear();
            decision = new WorkflowPolicyDecisionDto(true, null, null);
        }

        void deny(String code, String message) {
            recordedRequests.clear();
            decision = new WorkflowPolicyDecisionDto(false, code, message);
        }

        List<WorkflowPolicyRequestDto> recordedRequests() {
            return recordedRequests;
        }

        @Override
        public WorkflowPolicyDecisionDto validateApproval(WorkflowPolicyRequestDto request) {
            recordedRequests.add(request);
            return decision;
        }
    }

    static final class RecordingNamedParameterJdbcTemplate extends NamedParameterJdbcTemplate {
        private List<List<Map<String, Object>>> responses = List.of();
        private final List<String> recordedSqls = new ArrayList<>();
        private final List<Map<String, Object>> recordedParameters = new ArrayList<>();
        private final List<String> recordedUpdates = new ArrayList<>();
        private final List<Map<String, Object>> recordedUpdateParameters = new ArrayList<>();

        RecordingNamedParameterJdbcTemplate() {
            super(noOpDataSource());
        }

        void reset() {
            responses = List.of();
            recordedSqls.clear();
            recordedParameters.clear();
            recordedUpdates.clear();
            recordedUpdateParameters.clear();
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

        List<String> recordedUpdates() {
            return recordedUpdates;
        }

        List<Map<String, Object>> recordedUpdateParameters() {
            return recordedUpdateParameters;
        }

        @Override
        public <T> List<T> query(String sql, SqlParameterSource paramSource, RowMapper<T> rowMapper) {
            recordedSqls.add(sql);
            if (paramSource instanceof MapSqlParameterSource source) {
                recordedParameters.add(new HashMap<>(source.getValues()));
            }

            List<Map<String, Object>> rows = responses.isEmpty() ? List.of() : responses.remove(0);
            return rows.stream().map(row -> mapRow(rowMapper, row)).toList();
        }

        @Override
        public int update(String sql, SqlParameterSource paramSource) {
            recordedUpdates.add(sql);
            if (paramSource instanceof MapSqlParameterSource source) {
                recordedUpdateParameters.add(new HashMap<>(source.getValues()));
            }
            return 1;
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
                        case "getLong" -> {
                            Object val = row.get(args[0]);
                            yield val == null ? 0L : ((Number) val).longValue();
                        }
                        case "getBoolean" -> {
                            Object val = row.get(args[0]);
                            yield val != null && (Boolean) val;
                        }
                        case "getTimestamp" -> {
                            Object val = row.get(args[0]);
                            if (val instanceof OffsetDateTime odt) {
                                yield Timestamp.from(odt.toInstant());
                            }
                            yield val;
                        }
                        case "getDate" -> {
                            Object val = row.get(args[0]);
                            if (val instanceof LocalDate ld) {
                                yield java.sql.Date.valueOf(ld);
                            }
                            yield val;
                        }
                        case "getObject" -> {
                            if (args.length == 1) {
                                yield row.get(args[0]);
                            }
                            Object val = row.get(args[0]);
                            yield val == null ? null : ((Class<?>) args[1]).cast(val);
                        }
                        case "close" -> null;
                        case "wasNull" -> false;
                        default -> defaultValue(method.getReturnType());
                    }
            );
        }

        private Object defaultValue(Class<?> returnType) {
            if (!returnType.isPrimitive()) return null;
            if (returnType == boolean.class) return false;
            if (returnType == long.class) return 0L;
            if (returnType == int.class) return 0;
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
