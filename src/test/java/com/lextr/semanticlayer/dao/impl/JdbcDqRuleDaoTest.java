package com.lextr.semanticlayer.dao.impl;

import com.lextr.semanticlayer.exception.SemanticLayerException;
import com.lextr.semanticlayer.model.DqRuleAttributeRecord;
import com.lextr.semanticlayer.model.DqRuleCatalogRecord;
import com.lextr.semanticlayer.model.DqRuleMetadataChangeHistoryRecord;
import com.lextr.semanticlayer.model.DqRuleMetadataChangeHistoryWriteRequest;
import com.lextr.semanticlayer.model.DqRuleRequestWorkflowTaskRecord;
import com.lextr.semanticlayer.model.DqRuleRequestWorkflowTaskWriteRequest;
import com.lextr.semanticlayer.model.DqRuleResultRecord;
import com.lextr.semanticlayer.model.DqRuleResultWriteRequest;
import com.lextr.semanticlayer.util.SQLQueryLoaderUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.jdbc.datasource.AbstractDataSource;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

import javax.sql.DataSource;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JdbcDqRuleDaoTest {

    @Test
    void bindsRuleLookupParametersAndMapsReturnedColumns() {
        RecordingNamedParameterJdbcTemplate jdbcTemplate = new RecordingNamedParameterJdbcTemplate(List.of(ruleRow()));
        JdbcDqRuleDao dao = new JdbcDqRuleDao(providerOf(jdbcTemplate), new SQLQueryLoaderUtil(new DefaultResourceLoader()));

        List<DqRuleCatalogRecord> result = dao.findRules("client-a", "COMPLETENESS", "ACTIVE");

        assertTrue(jdbcTemplate.recordedSql.contains("FROM meta.dq_rule_catalog"));
        assertEquals("client-a", jdbcTemplate.recordedParameters.get("client_id"));
        assertEquals("COMPLETENESS", jdbcTemplate.recordedParameters.get("rule_dimension_cd"));
        assertEquals("ACTIVE", jdbcTemplate.recordedParameters.get("lifecycle_status_cd"));
        assertEquals(1, result.size());
        assertEquals("LEDGER_COMPLETENESS", result.get(0).rule_cd());
        assertEquals("ledger_id", result.get(0).logical_attribute_cd());
    }

    @Test
    void bindsWorkflowTaskInsertParametersAndMapsReturnedColumns() {
        RecordingNamedParameterJdbcTemplate jdbcTemplate = new RecordingNamedParameterJdbcTemplate(List.of(workflowTaskRow()));
        JdbcDqRuleDao dao = new JdbcDqRuleDao(providerOf(jdbcTemplate), new SQLQueryLoaderUtil(new DefaultResourceLoader()));

        DqRuleRequestWorkflowTaskRecord result = dao.insertWorkflowTask(workflowTaskRequest());

        assertTrue(jdbcTemplate.recordedSql.contains("INSERT INTO wkfl.workflow_task"));
        assertEquals("DQ_RULE_REQUEST", jdbcTemplate.recordedParameters.get("workflow_type_cd"));
        assertEquals("REQUESTED", jdbcTemplate.recordedParameters.get("task_status_cd"));
        assertEquals("client-a", jdbcTemplate.recordedParameters.get("client_id"));
        assertEquals(301L, result.id());
        assertEquals("LEDGER_COMPLETENESS", result.rule_cd());
        assertEquals("REQUESTED", result.task_status_cd());
    }

    @Test
    void bindsRequestLookupParametersAndMapsReturnedColumns() {
        RecordingNamedParameterJdbcTemplate jdbcTemplate = new RecordingNamedParameterJdbcTemplate(List.of(requestRow()));
        JdbcDqRuleDao dao = new JdbcDqRuleDao(providerOf(jdbcTemplate), new SQLQueryLoaderUtil(new DefaultResourceLoader()));

        Optional<DqRuleRequestWorkflowTaskRecord> result = dao.findRequest(
                "client-a",
                UUID.fromString("11111111-1111-1111-1111-111111111111")
        );

        assertTrue(jdbcTemplate.recordedSql.contains("CAST(md5(id::text) AS uuid) = :workflow_task_id"));
        assertEquals("client-a", jdbcTemplate.recordedParameters.get("client_id"));
        assertEquals(UUID.fromString("11111111-1111-1111-1111-111111111111"), jdbcTemplate.recordedParameters.get("workflow_task_id"));
        assertTrue(result.isPresent());
        assertEquals("LEDGER_COMPLETENESS", result.get().rule_cd());
        assertEquals("REQUESTED", result.get().task_status_cd());
    }

    @Test
    void bindsResultInsertParametersAndMapsReturnedColumns() {
        RecordingNamedParameterJdbcTemplate jdbcTemplate = new RecordingNamedParameterJdbcTemplate(List.of(resultRow()));
        JdbcDqRuleDao dao = new JdbcDqRuleDao(providerOf(jdbcTemplate), new SQLQueryLoaderUtil(new DefaultResourceLoader()));

        DqRuleResultRecord result = dao.insertResult(resultWriteRequest());

        assertTrue(jdbcTemplate.recordedSql.contains("INSERT INTO meta.dq_result"));
        assertEquals("LEDGER_COMPLETENESS", jdbcTemplate.recordedParameters.get("rule_cd"));
        assertEquals("ledger_id", jdbcTemplate.recordedParameters.get("logical_attribute_cd"));
        assertEquals("PASS", jdbcTemplate.recordedParameters.get("result_status_cd"));
        assertEquals(501L, result.id());
        assertEquals("PASS", result.result_status_cd());
    }

    @Test
    void bindsMetadataChangeHistoryParametersAndMapsReturnedColumns() {
        RecordingNamedParameterJdbcTemplate jdbcTemplate = new RecordingNamedParameterJdbcTemplate(List.of(metadataChangeRow()));
        JdbcDqRuleDao dao = new JdbcDqRuleDao(providerOf(jdbcTemplate), new SQLQueryLoaderUtil(new DefaultResourceLoader()));

        DqRuleMetadataChangeHistoryRecord result = dao.insertMetadataChangeHistory(metadataChangeRequest());

        assertTrue(jdbcTemplate.recordedSql.contains("INSERT INTO meta.metadata_change_history"));
        assertEquals("DQ_RULE_REQUEST", jdbcTemplate.recordedParameters.get("entity_type_cd"));
        assertEquals("LEDGER_COMPLETENESS", jdbcTemplate.recordedParameters.get("entity_ref"));
        assertEquals("REQUESTED", result.change_type_cd());
        assertEquals("Requested DQ rule LEDGER_COMPLETENESS; coverage=50%", result.change_summary_txt());
    }

    @Test
    void transactionRollsBackDqWritesOnFailure() {
        TransactionHarness harness = new TransactionHarness();
        TransactionalNamedParameterJdbcTemplate jdbcTemplate = new TransactionalNamedParameterJdbcTemplate(harness);
        JdbcDqRuleDao dao = new JdbcDqRuleDao(providerOf(jdbcTemplate), new SQLQueryLoaderUtil(new DefaultResourceLoader()));
        RecordingTransactionOperations transactionOperations = new RecordingTransactionOperations(harness);

        assertThrows(IllegalStateException.class, () -> transactionOperations.execute(status -> {
            dao.insertWorkflowTask(workflowTaskRequest());
            dao.insertResult(resultWriteRequest());
            throw new IllegalStateException("dq transaction failed");
        }));

        assertTrue(harness.rolledBack);
        assertFalse(harness.committed);
        assertEquals(2, jdbcTemplate.recordedSqls.size());
    }

    @Test
    void usesNamedParameterJdbcTemplateAndDoesNotUseJpa() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/lextr/semanticlayer/dao/impl/JdbcDqRuleDao.java"));

        assertTrue(source.contains("NamedParameterJdbcTemplate"));
        assertFalse(source.contains("EntityManager"));
        assertFalse(source.contains("JpaRepository"));
        assertFalse(source.contains("jakarta.persistence"));
        assertFalse(source.contains("javax.persistence"));
    }

    private static DqRuleRequestWorkflowTaskWriteRequest workflowTaskRequest() {
        return new DqRuleRequestWorkflowTaskWriteRequest(
                "client-a",
                "DQ_RULE_REQUEST",
                "DQ_RULE_REQUEST",
                "LEDGER_COMPLETENESS",
                "REQUESTED",
                "steward",
                OffsetDateTime.parse("2026-06-26T10:15:30Z"),
                "semantic-layer",
                LocalDate.parse("2026-07-03"),
                "Request DQ rule LEDGER_COMPLETENESS"
        );
    }

    private static DqRuleResultWriteRequest resultWriteRequest() {
        return new DqRuleResultWriteRequest(
                "LEDGER_COMPLETENESS",
                "ledger_id",
                "client-a",
                "123",
                "123",
                "PASS",
                "Expected value observed",
                OffsetDateTime.parse("2026-06-26T10:15:30Z"),
                OffsetDateTime.parse("2026-06-26T10:15:30Z"),
                "engine-principal",
                OffsetDateTime.parse("2026-06-26T10:15:30Z"),
                "engine-principal"
        );
    }

    private static DqRuleMetadataChangeHistoryWriteRequest metadataChangeRequest() {
        return new DqRuleMetadataChangeHistoryWriteRequest(
                UUID.fromString("22222222-2222-2222-2222-222222222222"),
                "client-a",
                "DQ_RULE_REQUEST",
                "LEDGER_COMPLETENESS",
                "REQUESTED",
                "Requested DQ rule LEDGER_COMPLETENESS; coverage=50%",
                OffsetDateTime.parse("2026-06-26T10:15:30Z"),
                "steward"
        );
    }

    private static Map<String, Object> ruleRow() {
        Map<String, Object> row = new HashMap<>();
        row.put("id", 101L);
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

    private static Map<String, Object> workflowTaskRow() {
        Map<String, Object> row = new HashMap<>();
        row.put("id", 301L);
        row.put("task_type_cd", "DQ_RULE_REQUEST");
        row.put("entity_type_cd", "DQ_RULE_REQUEST");
        row.put("rule_cd", "LEDGER_COMPLETENESS");
        row.put("task_status_cd", "REQUESTED");
        row.put("submitted_by", "steward");
        row.put("submitted_ts", OffsetDateTime.parse("2026-06-26T10:15:30Z"));
        row.put("assigned_to", "semantic-layer");
        row.put("due_dt", LocalDate.parse("2026-07-03"));
        row.put("description_txt", "Request DQ rule LEDGER_COMPLETENESS");
        row.put("client_id", "client-a");
        row.put("approved_by", null);
        row.put("approved_ts", null);
        row.put("approval_note_txt", null);
        return row;
    }

    private static Map<String, Object> requestRow() {
        Map<String, Object> row = workflowTaskRow();
        row.put("task_status_cd", "REQUESTED");
        return row;
    }

    private static Map<String, Object> resultRow() {
        Map<String, Object> row = new HashMap<>();
        row.put("id", 501L);
        row.put("rule_cd", "LEDGER_COMPLETENESS");
        row.put("logical_attribute_cd", "ledger_id");
        row.put("client_id", "client-a");
        row.put("observed_value_txt", "123");
        row.put("expected_value_txt", "123");
        row.put("result_status_cd", "PASS");
        row.put("result_reason_txt", "Expected value observed");
        row.put("observed_ts", OffsetDateTime.parse("2026-06-26T10:15:30Z"));
        row.put("created_ts", OffsetDateTime.parse("2026-06-26T10:15:30Z"));
        row.put("created_by", "engine-principal");
        row.put("updated_ts", OffsetDateTime.parse("2026-06-26T10:15:30Z"));
        row.put("updated_by", "engine-principal");
        return row;
    }

    private static Map<String, Object> metadataChangeRow() {
        Map<String, Object> row = new HashMap<>();
        row.put("change_history_id", UUID.fromString("33333333-3333-3333-3333-333333333333"));
        row.put("client_id", "client-a");
        row.put("entity_type_cd", "DQ_RULE_REQUEST");
        row.put("entity_ref", "LEDGER_COMPLETENESS");
        row.put("change_type_cd", "REQUESTED");
        row.put("change_summary_txt", "Requested DQ rule LEDGER_COMPLETENESS; coverage=50%");
        row.put("created_ts", OffsetDateTime.parse("2026-06-26T10:15:30Z"));
        row.put("created_by", "steward");
        return row;
    }

    private static ObjectProvider<NamedParameterJdbcTemplate> providerOf(NamedParameterJdbcTemplate jdbcTemplate) {
        return new ObjectProvider<>() {
            @Override
            public NamedParameterJdbcTemplate getObject(Object... args) {
                return jdbcTemplate;
            }

            @Override
            public NamedParameterJdbcTemplate getIfAvailable() {
                return jdbcTemplate;
            }

            @Override
            public NamedParameterJdbcTemplate getIfUnique() {
                return jdbcTemplate;
            }

            @Override
            public NamedParameterJdbcTemplate getObject() {
                return jdbcTemplate;
            }

            @Override
            public Iterator<NamedParameterJdbcTemplate> iterator() {
                return jdbcTemplate == null ? Collections.emptyIterator() : List.of(jdbcTemplate).iterator();
            }
        };
    }

    private static <T> T mapRow(RowMapper<T> rowMapper, Map<String, Object> row) {
        try {
            return rowMapper.mapRow(resultSet(row), 0);
        } catch (SQLException exception) {
            throw new RuntimeException(exception);
        }
    }

    private static ResultSet resultSet(Map<String, Object> row) {
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

    private static Object defaultValue(Class<?> returnType) {
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

    private static final class RecordingNamedParameterJdbcTemplate extends NamedParameterJdbcTemplate {

        private final List<Map<String, Object>> rows;
        private String recordedSql;
        private Map<String, Object> recordedParameters = Map.of();

        private RecordingNamedParameterJdbcTemplate(List<Map<String, Object>> rows) {
            super(noOpDataSource());
            this.rows = rows;
        }

        @Override
        public <T> List<T> query(String sql, SqlParameterSource paramSource, RowMapper<T> rowMapper) {
            recordedSql = sql;
            if (paramSource instanceof MapSqlParameterSource source) {
                recordedParameters = source.getValues();
            }
            return rows.stream().map(row -> mapRow(rowMapper, row)).toList();
        }
    }

    private static final class TransactionalNamedParameterJdbcTemplate extends NamedParameterJdbcTemplate {

        private final TransactionHarness harness;
        private final List<String> recordedSqls = new ArrayList<>();

        private TransactionalNamedParameterJdbcTemplate(TransactionHarness harness) {
            super(noOpDataSource());
            this.harness = harness;
        }

        @Override
        public <T> List<T> query(String sql, SqlParameterSource paramSource, RowMapper<T> rowMapper) {
            recordedSqls.add(sql);
            Map<String, Object> parameters = paramSource instanceof MapSqlParameterSource source ? source.getValues() : Map.of();
            Map<String, Object> row;
            if (sql.startsWith("INSERT INTO wkfl.workflow_task")) {
                row = workflowTaskRow(parameters);
            } else if (sql.startsWith("INSERT INTO meta.dq_result")) {
                row = resultRow(parameters);
            } else if (sql.startsWith("INSERT INTO meta.metadata_change_history")) {
                row = metadataChangeRow();
            } else {
                throw new IllegalArgumentException("Unexpected SQL: " + sql);
            }
            return List.of(mapRow(rowMapper, row));
        }

        private Map<String, Object> workflowTaskRow(Map<String, Object> parameters) {
            Map<String, Object> row = new HashMap<>();
            row.put("id", 301L);
            row.put("task_type_cd", parameters.get("task_type_cd"));
            row.put("entity_type_cd", parameters.get("entity_type_cd"));
            row.put("rule_cd", parameters.get("rule_cd"));
            row.put("task_status_cd", parameters.get("task_status_cd"));
            row.put("submitted_by", parameters.get("created_by"));
            row.put("submitted_ts", parameters.get("created_ts"));
            row.put("assigned_to", parameters.get("assigned_to"));
            row.put("due_dt", parameters.get("due_dt"));
            row.put("description_txt", parameters.get("description_txt"));
            row.put("client_id", parameters.get("client_id"));
            row.put("approved_by", null);
            row.put("approved_ts", null);
            row.put("approval_note_txt", null);
            return row;
        }

        private Map<String, Object> resultRow(Map<String, Object> parameters) {
            Map<String, Object> row = new HashMap<>();
            row.put("id", 501L);
            row.put("rule_cd", parameters.get("rule_cd"));
            row.put("logical_attribute_cd", parameters.get("logical_attribute_cd"));
            row.put("client_id", parameters.get("client_id"));
            row.put("observed_value_txt", parameters.get("observed_value_txt"));
            row.put("expected_value_txt", parameters.get("expected_value_txt"));
            row.put("result_status_cd", parameters.get("result_status_cd"));
            row.put("result_reason_txt", parameters.get("result_reason_txt"));
            row.put("observed_ts", parameters.get("observed_ts"));
            row.put("created_ts", parameters.get("created_ts"));
            row.put("created_by", parameters.get("created_by"));
            row.put("updated_ts", parameters.get("updated_ts"));
            row.put("updated_by", parameters.get("updated_by"));
            return row;
        }
    }

    private static final class TransactionHarness {
        private boolean committed;
        private boolean rolledBack;
    }

    private static final class RecordingTransactionOperations implements TransactionOperations {

        private final TransactionHarness harness;

        private RecordingTransactionOperations(TransactionHarness harness) {
            this.harness = harness;
        }

        @Override
        public <T> T execute(TransactionCallback<T> action) {
            try {
                T result = action.doInTransaction(new RecordingTransactionStatus());
                harness.committed = true;
                return result;
            } catch (Throwable exception) {
                harness.rolledBack = true;
                if (exception instanceof RuntimeException runtimeException) {
                    throw runtimeException;
                }
                throw new RuntimeException(exception);
            }
        }
    }

    private static final class RecordingTransactionStatus implements TransactionStatus {

        @Override
        public boolean isNewTransaction() {
            return false;
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
