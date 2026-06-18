package com.lextr.semanticlayer.dao.impl;

import com.lextr.semanticlayer.exception.SemanticLayerException;
import com.lextr.semanticlayer.model.FilterLookupWorkflowTaskRecord;
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
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JdbcWorkflowApprovalDaoTest {

    @Test
    void findsTaskByIdAndMapsReturnedColumns() {
        RecordingNamedParameterJdbcTemplate jdbcTemplate = new RecordingNamedParameterJdbcTemplate(List.of(taskRow()));
        JdbcWorkflowApprovalDao dao = new JdbcWorkflowApprovalDao(
                providerOf(jdbcTemplate),
                new SQLQueryLoaderUtil(new DefaultResourceLoader())
        );

        FilterLookupWorkflowTaskRecord result = dao.findTaskById("client-a", 301L);

        assertTrue(jdbcTemplate.recordedSql.contains("SELECT id"));
        assertTrue(jdbcTemplate.recordedSql.contains("FROM wkfl.workflow_task"));
        assertEquals("client-a", jdbcTemplate.recordedParameters.get("client_id"));
        assertEquals(301L, jdbcTemplate.recordedParameters.get("id"));

        assertNotNull(result);
        assertEquals(301L, result.id());
        assertEquals("FILTER_LOOKUP_REGISTRATION", result.task_type_cd());
        assertEquals("PENDING", result.task_status_cd());
    }

    @Test
    void approvesTaskAndMapsReturnedColumns() {
        OffsetDateTime now = OffsetDateTime.parse("2026-06-18T10:15:30Z");
        Map<String, Object> approvedTaskRow = taskRow();
        approvedTaskRow.put("task_status_cd", "APPROVED");
        approvedTaskRow.put("approved_by", "approver");
        approvedTaskRow.put("approved_ts", now);
        approvedTaskRow.put("approval_note_txt", "approved");

        RecordingNamedParameterJdbcTemplate jdbcTemplate = new RecordingNamedParameterJdbcTemplate(List.of(approvedTaskRow));
        JdbcWorkflowApprovalDao dao = new JdbcWorkflowApprovalDao(
                providerOf(jdbcTemplate),
                new SQLQueryLoaderUtil(new DefaultResourceLoader())
        );

        FilterLookupWorkflowTaskRecord result = dao.approveTask("client-a", 301L, "approver", now, "approved");

        assertTrue(jdbcTemplate.recordedSql.contains("UPDATE wkfl.workflow_task"));
        assertEquals("client-a", jdbcTemplate.recordedParameters.get("client_id"));
        assertEquals(301L, jdbcTemplate.recordedParameters.get("id"));
        assertEquals("APPROVED", jdbcTemplate.recordedParameters.get("task_status_cd"));
        assertEquals("approver", jdbcTemplate.recordedParameters.get("approved_by"));
        assertEquals(now, jdbcTemplate.recordedParameters.get("approved_ts"));
        assertEquals("approved", jdbcTemplate.recordedParameters.get("approval_note_txt"));

        assertEquals(301L, result.id());
        assertEquals("APPROVED", result.task_status_cd());
        assertEquals("approver", result.approved_by());
    }

    @Test
    void updatesSemanticFilterLookupStatus() {
        RecordingNamedParameterJdbcTemplate jdbcTemplate = new RecordingNamedParameterJdbcTemplate(List.of());
        JdbcWorkflowApprovalDao dao = new JdbcWorkflowApprovalDao(
                providerOf(jdbcTemplate),
                new SQLQueryLoaderUtil(new DefaultResourceLoader())
        );
        OffsetDateTime now = OffsetDateTime.now();

        dao.approveLookup("client-a", "LEDGER_SCOPE", "ACTIVE", now, "approver");

        assertTrue(jdbcTemplate.recordedSql.contains("UPDATE meta.semantic_filter_lookup"));
        assertEquals("client-a", jdbcTemplate.recordedParameters.get("client_id"));
        assertEquals("LEDGER_SCOPE", jdbcTemplate.recordedParameters.get("lookup_cd"));
        assertEquals("ACTIVE", jdbcTemplate.recordedParameters.get("governance_status_cd"));
        assertEquals(now, jdbcTemplate.recordedParameters.get("updated_ts"));
        assertEquals("approver", jdbcTemplate.recordedParameters.get("updated_by"));
    }

    @Test
    void updatesAttributeLogicalNameOverrideStatus() {
        RecordingNamedParameterJdbcTemplate jdbcTemplate = new RecordingNamedParameterJdbcTemplate(List.of());
        JdbcWorkflowApprovalDao dao = new JdbcWorkflowApprovalDao(
                providerOf(jdbcTemplate),
                new SQLQueryLoaderUtil(new DefaultResourceLoader())
        );
        OffsetDateTime now = OffsetDateTime.now();

        dao.approveAttributeOverride("client-a", 402L, "ACTIVE", now, "approver");

        assertTrue(jdbcTemplate.recordedSql.contains("UPDATE meta.attribute_logical_name_override"));
        assertEquals("client-a", jdbcTemplate.recordedParameters.get("client_id"));
        assertEquals(402L, jdbcTemplate.recordedParameters.get("id"));
        assertEquals("ACTIVE", jdbcTemplate.recordedParameters.get("lifecycle_status_cd"));
    }

    @Test
    void updatesFilterLookupValueStatus() {
        RecordingNamedParameterJdbcTemplate jdbcTemplate = new RecordingNamedParameterJdbcTemplate(List.of());
        JdbcWorkflowApprovalDao dao = new JdbcWorkflowApprovalDao(
                providerOf(jdbcTemplate),
                new SQLQueryLoaderUtil(new DefaultResourceLoader())
        );
        OffsetDateTime now = OffsetDateTime.now();

        dao.approveFilterLookupValue("LEDGER_SCOPE", "USD", "ACTIVE", true, now);

        assertTrue(jdbcTemplate.recordedSql.contains("UPDATE meta.filter_lookup_value"));
        assertEquals("LEDGER_SCOPE", jdbcTemplate.recordedParameters.get("lookup_cd"));
        assertEquals("USD", jdbcTemplate.recordedParameters.get("value_cd"));
        assertEquals("ACTIVE", jdbcTemplate.recordedParameters.get("lifecycle_status_cd"));
        assertTrue((Boolean) jdbcTemplate.recordedParameters.get("validated_flg"));
    }

    @Test
    void transactionRollsBackOnFailure() {
        TransactionHarness harness = new TransactionHarness();
        TransactionalNamedParameterJdbcTemplate jdbcTemplate = new TransactionalNamedParameterJdbcTemplate(harness);
        JdbcWorkflowApprovalDao dao = new JdbcWorkflowApprovalDao(
                providerOf(jdbcTemplate),
                new SQLQueryLoaderUtil(new DefaultResourceLoader())
        );
        RecordingTransactionOperations transactionOperations = new RecordingTransactionOperations(harness);

        assertThrows(IllegalStateException.class, () -> transactionOperations.execute(status -> {
            dao.approveLookup("client-a", "LEDGER_SCOPE", "ACTIVE", OffsetDateTime.now(), "approver");
            throw new IllegalStateException("Simulated DB error");
        }));

        assertTrue(harness.rolledBack);
        assertFalse(harness.committed);
        assertTrue(jdbcTemplate.recordedSqls.get(0).contains("UPDATE meta.semantic_filter_lookup"));
    }

    @Test
    void failsWhenNamedParameterJdbcTemplateMissing() {
        JdbcWorkflowApprovalDao dao = new JdbcWorkflowApprovalDao(
                providerOf(null),
                new SQLQueryLoaderUtil(new DefaultResourceLoader())
        );

        assertThrows(SemanticLayerException.class, () -> dao.findTaskById("client-a", 301L));
    }

    @Test
    void usesNamedParameterJdbcTemplateAndDoesNotUseJpa() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/lextr/semanticlayer/dao/impl/JdbcWorkflowApprovalDao.java"
        ));

        assertTrue(source.contains("NamedParameterJdbcTemplate"));
        assertFalse(source.contains("EntityManager"));
        assertFalse(source.contains("JpaRepository"));
        assertFalse(source.contains("jakarta.persistence"));
        assertFalse(source.contains("javax.persistence"));
    }

    private static Map<String, Object> taskRow() {
        Map<String, Object> row = new HashMap<>();
        row.put("id", 301L);
        row.put("task_type_cd", "FILTER_LOOKUP_REGISTRATION");
        row.put("entity_type_cd", "FILTER_LOOKUP");
        row.put("entity_ref", "LEDGER_SCOPE");
        row.put("task_status_cd", "PENDING");
        row.put("submitted_by", "producer");
        row.put("submitted_ts", OffsetDateTime.parse("2026-06-18T10:15:30Z"));
        row.put("assigned_to", null);
        row.put("due_dt", LocalDate.parse("2026-09-16"));
        row.put("description_txt", "Review filter lookup LEDGER_SCOPE");
        row.put("client_id", "client-a");
        row.put("approved_by", null);
        row.put("approved_ts", null);
        row.put("approval_note_txt", null);
        return row;
    }

    private static ObjectProvider<NamedParameterJdbcTemplate> providerOf(NamedParameterJdbcTemplate jdbcTemplate) {
        return new ObjectProvider<>() {
            @Override
            public NamedParameterJdbcTemplate getObject(Object... args) { return jdbcTemplate; }
            @Override
            public NamedParameterJdbcTemplate getIfAvailable() { return jdbcTemplate; }
            @Override
            public NamedParameterJdbcTemplate getIfUnique() { return jdbcTemplate; }
            @Override
            public NamedParameterJdbcTemplate getObject() { return jdbcTemplate; }
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
                    case "getLong" -> {
                        Object value = row.get(args[0]);
                        yield value == null ? 0L : ((Number) value).longValue();
                    }
                    case "getTimestamp" -> {
                        Object value = row.get(args[0]);
                        if (value instanceof OffsetDateTime odt) {
                            yield java.sql.Timestamp.from(odt.toInstant());
                        }
                        yield value;
                    }
                    case "getDate" -> {
                        Object value = row.get(args[0]);
                        if (value instanceof LocalDate ld) {
                            yield java.sql.Date.valueOf(ld);
                        }
                        yield value;
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
        if (!returnType.isPrimitive()) { return null; }
        if (returnType == boolean.class) { return false; }
        if (returnType == long.class) { return 0L; }
        if (returnType == int.class) { return 0; }
        return null;
    }

    private static DataSource noOpDataSource() {
        return new AbstractDataSource() {
            @Override
            public Connection getConnection() { throw new UnsupportedOperationException(); }
            @Override
            public Connection getConnection(String u, String p) { throw new UnsupportedOperationException(); }
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

        @Override
        public int update(String sql, SqlParameterSource paramSource) {
            recordedSql = sql;
            if (paramSource instanceof MapSqlParameterSource source) {
                recordedParameters = source.getValues();
            }
            return 1;
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
        public int update(String sql, SqlParameterSource paramSource) {
            recordedSqls.add(sql);
            return 1;
        }
    }

    private static final class TransactionHarness {
        private boolean committed;
        private boolean rolledBack;

        private void begin() {
            committed = false;
            rolledBack = false;
        }
        private void commit() { committed = true; }
        private void rollback() { rolledBack = true; }
    }

    private static final class RecordingTransactionOperations implements TransactionOperations {
        private final TransactionHarness harness;

        private RecordingTransactionOperations(TransactionHarness harness) {
            this.harness = harness;
        }

        @Override
        public <T> T execute(TransactionCallback<T> action) {
            harness.begin();
            try {
                T result = action.doInTransaction(new NoOpTransactionStatus());
                harness.commit();
                return result;
            } catch (RuntimeException exception) {
                harness.rollback();
                throw exception;
            }
        }
    }

    private static final class NoOpTransactionStatus implements TransactionStatus {
        @Override
        public boolean isNewTransaction() { return false; }
        @Override
        public boolean hasSavepoint() { return false; }
        @Override
        public void setRollbackOnly() {}
        @Override
        public boolean isRollbackOnly() { return false; }
        @Override
        public void flush() {}
        @Override
        public boolean isCompleted() { return false; }
        @Override
        public Object createSavepoint() { return null; }
        @Override
        public void rollbackToSavepoint(Object savepoint) {}
        @Override
        public void releaseSavepoint(Object savepoint) {}
    }
}
