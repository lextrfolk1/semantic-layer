package com.lextr.semanticlayer.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
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
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.context.WebApplicationContext;

import javax.sql.DataSource;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringJUnitWebConfig(classes = {
        com.lextr.semanticlayer.SemanticLayerApplication.class,
        DqRuleWireThroughTest.DqRuleWireThroughTestConfiguration.class
})
@TestExecutionListeners(
        listeners = {
                DependencyInjectionTestExecutionListener.class,
                DirtiesContextBeforeModesTestExecutionListener.class,
                DirtiesContextTestExecutionListener.class
        },
        mergeMode = TestExecutionListeners.MergeMode.REPLACE_DEFAULTS
)
class DqRuleWireThroughTest {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private RecordingNamedParameterJdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetTemplate() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        jdbcTemplate.reset();
    }

    @Test
    void honorsDqRequestAndObserveContractsEndToEnd() throws Exception {
        jdbcTemplate.setRowsForSqlFragment("FROM meta.dq_rule_catalog", List.of(ruleRow()));
        jdbcTemplate.setRowsForSqlFragment("FROM meta.dq_rule_attribute", List.of(
                attributeRow("ledger_id"),
                attributeRow("ledger_status_cd")
        ));
        jdbcTemplate.setRowsForSqlFragment("FROM meta.dq_result", List.of(resultRow()));
        jdbcTemplate.setRowsForSqlFragment("INSERT INTO wkfl.workflow_task", List.of(workflowTaskRow("PENDING", null, null, null)));
        jdbcTemplate.setRowsForSqlFragment("INSERT INTO meta.metadata_change_history", List.of(metadataHistoryRow()));
        jdbcTemplate.setRowsForSqlFragment("FROM wkfl.workflow_task", List.of(workflowTaskRow("APPROVED", "approver", OffsetDateTime.parse("2026-06-18T10:20:30Z"), "looks good")));

        mockMvc.perform(post("/api/dq-rules/requests")
                        .contentType("application/json")
                        .content("""
                                {
                                  "client_id": "client-a",
                                  "rule_names": ["LEDGER_COMPLETENESS"],
                                  "requested_by": "steward",
                                  "request_txt": "Please review ledger completeness"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$[0].task_type_cd").value("DQ_RULE_REQUEST"))
                .andExpect(jsonPath("$[0].entity_ref").value("LEDGER_COMPLETENESS"))
                .andExpect(jsonPath("$[0].client_id").value("client-a"))
                .andExpect(jsonPath("$[0].description_txt").value("Please review ledger completeness"));

        assertEquals("client-a", paramsFor("FROM meta.dq_rule_catalog").get("client_id"));
        assertEquals("LEDGER_COMPLETENESS", paramsFor("FROM meta.dq_rule_catalog").get("rule_cd"));
        assertEquals("client-a", paramsFor("INSERT INTO wkfl.workflow_task").get("client_id"));
        assertEquals("LEDGER_COMPLETENESS", paramsFor("INSERT INTO wkfl.workflow_task").get("rule_cd"));
        assertEquals("Please review ledger completeness", paramsFor("INSERT INTO wkfl.workflow_task").get("description_txt"));
        assertEquals("client-a", paramsFor("INSERT INTO meta.metadata_change_history").get("client_id"));
        assertEquals("LEDGER_COMPLETENESS", paramsFor("INSERT INTO meta.metadata_change_history").get("entity_ref"));

        mockMvc.perform(get("/api/dq-rules/requests/{workflow_task_id}", UUID.fromString("11111111-1111-1111-1111-111111111111"))
                        .queryParam("client_id", "client-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(301))
                .andExpect(jsonPath("$.task_status_cd").value("APPROVED"))
                .andExpect(jsonPath("$.client_id").value("client-a"));

        assertEquals("client-a", paramsFor("FROM wkfl.workflow_task").get("client_id"));
        assertEquals(UUID.fromString("11111111-1111-1111-1111-111111111111"), paramsFor("FROM wkfl.workflow_task").get("workflow_task_id"));

        assertTrue(jdbcTemplate.recordedSqls().stream().anyMatch(sql -> sql.contains("FROM meta.dq_rule_catalog")));
        assertTrue(jdbcTemplate.recordedSqls().stream().anyMatch(sql -> sql.contains("FROM meta.dq_rule_attribute")));
        assertTrue(jdbcTemplate.recordedSqls().stream().anyMatch(sql -> sql.contains("FROM meta.dq_result")));
        assertTrue(jdbcTemplate.recordedSqls().stream().anyMatch(sql -> sql.contains("INSERT INTO wkfl.workflow_task")));
        assertTrue(jdbcTemplate.recordedSqls().stream().anyMatch(sql -> sql.contains("INSERT INTO meta.metadata_change_history")));
    }

    @Test
    void rejectsRequestWhenTenantScopeMissing() throws Exception {
        mockMvc.perform(post("/api/dq-rules/requests")
                        .contentType("application/json")
                        .content("""
                                {
                                  "rule_names": ["LEDGER_COMPLETENESS"],
                                  "requested_by": "steward",
                                  "request_txt": "Please review ledger completeness"
                                }
                                """))
                .andExpect(status().isBadRequest());

        assertTrue(jdbcTemplate.recordedCalls().isEmpty());
    }

    private Map<String, Object> paramsFor(String sqlFragment) {
        return jdbcTemplate.recordedCalls().stream()
                .filter(call -> call.sql().contains(sqlFragment))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No recorded SQL contained: " + sqlFragment))
                .params();
    }

    private static Map<String, Object> ruleRow() {
        Map<String, Object> row = new HashMap<>();
        row.put("id", 1L);
        row.put("rule_cd", "LEDGER_COMPLETENESS");
        row.put("rule_nm", "Ledger Completeness");
        row.put("rule_dimension_cd", "COMPLETENESS");
        row.put("logical_attribute_cd", "ledger_id");
        row.put("rule_scope_cd", "RULESET");
        row.put("rule_expression_txt", "ledger_id is present");
        row.put("severity_cd", "HIGH");
        row.put("lifecycle_status_cd", "ACTIVE");
        row.put("client_id", "client-a");
        row.put("created_ts", OffsetDateTime.parse("2026-06-01T10:15:30Z"));
        row.put("created_by", "producer");
        row.put("updated_ts", OffsetDateTime.parse("2026-06-02T10:15:30Z"));
        row.put("updated_by", "reviewer");
        return row;
    }

    private static Map<String, Object> attributeRow(String attributeCode) {
        Map<String, Object> row = new HashMap<>();
        row.put("id", attributeCode.equals("ledger_id") ? 10L : 11L);
        row.put("rule_cd", "LEDGER_COMPLETENESS");
        row.put("attribute_cd", attributeCode);
        row.put("attribute_role_cd", "SOURCE");
        row.put("client_id", "client-a");
        row.put("created_ts", OffsetDateTime.parse("2026-06-01T10:15:30Z"));
        row.put("created_by", "producer");
        row.put("updated_ts", OffsetDateTime.parse("2026-06-02T10:15:30Z"));
        row.put("updated_by", "reviewer");
        return row;
    }

    private static Map<String, Object> resultRow() {
        Map<String, Object> row = new HashMap<>();
        row.put("id", 100L);
        row.put("rule_cd", "LEDGER_COMPLETENESS");
        row.put("logical_attribute_cd", "ledger_id");
        row.put("client_id", "client-a");
        row.put("observed_value_txt", "123");
        row.put("expected_value_txt", "123");
        row.put("result_status_cd", "PASS");
        row.put("result_reason_txt", null);
        row.put("observed_ts", OffsetDateTime.parse("2026-06-26T10:00:00Z"));
        row.put("created_ts", OffsetDateTime.parse("2026-06-26T10:00:00Z"));
        row.put("created_by", "sensor");
        row.put("updated_ts", OffsetDateTime.parse("2026-06-26T10:00:01Z"));
        row.put("updated_by", "sensor");
        return row;
    }

    private static Map<String, Object> workflowTaskRow(String status, String approvedBy, OffsetDateTime approvedTs, String approvalNote) {
        Map<String, Object> row = new HashMap<>();
        row.put("id", 301L);
        row.put("task_type_cd", "DQ_RULE_REQUEST");
        row.put("entity_type_cd", "DQ_RULE");
        row.put("rule_cd", "LEDGER_COMPLETENESS");
        row.put("task_status_cd", status);
        row.put("submitted_by", "steward");
        row.put("submitted_ts", OffsetDateTime.parse("2026-06-10T10:15:30Z"));
        row.put("assigned_to", "semantic-layer");
        row.put("due_dt", LocalDate.parse("2026-06-17"));
        row.put("description_txt", "Please review ledger completeness");
        row.put("client_id", "client-a");
        row.put("approved_by", approvedBy);
        row.put("approved_ts", approvedTs);
        row.put("approval_note_txt", approvalNote);
        return row;
    }

    private static Map<String, Object> metadataHistoryRow() {
        Map<String, Object> row = new HashMap<>();
        row.put("change_history_id", UUID.fromString("22222222-2222-2222-2222-222222222222"));
        row.put("client_id", "client-a");
        row.put("entity_type_cd", "DQ_RULE");
        row.put("entity_ref", "LEDGER_COMPLETENESS");
        row.put("change_type_cd", "REQUESTED");
        row.put("change_summary_txt", "Requested DQ rule LEDGER_COMPLETENESS; coverage=50%");
        row.put("created_ts", OffsetDateTime.parse("2026-06-10T10:15:30Z"));
        row.put("created_by", "steward");
        return row;
    }

    @TestConfiguration
    static class DqRuleWireThroughTestConfiguration {

        @Bean
        RecordingNamedParameterJdbcTemplate recordingNamedParameterJdbcTemplate() {
            return new RecordingNamedParameterJdbcTemplate();
        }
    }

    static final class RecordingNamedParameterJdbcTemplate extends NamedParameterJdbcTemplate {

        private final List<RecordedCall> recordedCalls = new ArrayList<>();
        private final Map<String, List<Map<String, Object>>> rowsBySqlFragment = new LinkedHashMap<>();

        RecordingNamedParameterJdbcTemplate() {
            super(noOpDataSource());
        }

        void reset() {
            recordedCalls.clear();
            rowsBySqlFragment.clear();
        }

        void setRowsForSqlFragment(String sqlFragment, List<Map<String, Object>> rows) {
            rowsBySqlFragment.put(sqlFragment, rows);
        }

        List<RecordedCall> recordedCalls() {
            return List.copyOf(recordedCalls);
        }

        List<String> recordedSqls() {
            return recordedCalls.stream().map(RecordedCall::sql).toList();
        }

        @Override
        public <T> List<T> query(String sql, SqlParameterSource paramSource, RowMapper<T> rowMapper) {
            Map<String, Object> parameters = paramSource instanceof MapSqlParameterSource source
                    ? new LinkedHashMap<>(source.getValues())
                    : Map.of();
            recordedCalls.add(new RecordedCall(sql, parameters));
            List<Map<String, Object>> rows = rowsBySqlFragment.entrySet().stream()
                    .filter(entry -> sql.contains(entry.getKey()))
                    .findFirst()
                    .map(Map.Entry::getValue)
                    .orElse(List.of());
            return rows.stream().map(row -> mapRow(rowMapper, row)).toList();
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
    }

    record RecordedCall(String sql, Map<String, Object> params) {
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
