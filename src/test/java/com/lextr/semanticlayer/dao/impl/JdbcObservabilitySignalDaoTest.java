package com.lextr.semanticlayer.dao.impl;

import com.lextr.semanticlayer.model.ObservabilitySignalCorrelationWriteRequest;
import com.lextr.semanticlayer.model.ObservabilitySignalRecord;
import com.lextr.semanticlayer.model.ObservabilitySignalWriteRequest;
import com.lextr.semanticlayer.util.SQLQueryLoaderUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

import javax.sql.DataSource;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JdbcObservabilitySignalDaoTest {

    @Test
    void loadsInsertQueryAndBindsSignalParameters() {
        RecordingNamedParameterJdbcTemplate jdbcTemplate = new RecordingNamedParameterJdbcTemplate();
        jdbcTemplate.setResponses(List.of(List.of(signalRow(501L, "OPEN", "WARN"))));
        JdbcObservabilitySignalDao dao = new JdbcObservabilitySignalDao(providerOf(jdbcTemplate), new SQLQueryLoaderUtil(new DefaultResourceLoader()));

        ObservabilitySignalRecord record = dao.insertSignal(new ObservabilitySignalWriteRequest(
                "client-a",
                "FRESHNESS",
                "WARN",
                "OPEN",
                "PIPELINE",
                "DATASET",
                "orders",
                "orders#2026-06-18",
                "Freshness lag detected",
                "Latest event lagged by 4h",
                OffsetDateTime.parse("2026-06-18T10:15:30Z"),
                true,
                "Re-run ETL",
                OffsetDateTime.parse("2026-06-18T10:16:30Z"),
                "tooling",
                OffsetDateTime.parse("2026-06-18T10:16:30Z"),
                "tooling"
        ));

        assertTrue(jdbcTemplate.recordedSqls().get(0).contains("INSERT INTO meta.observability_signal"));
        assertEquals("client-a", jdbcTemplate.recordedParameters().get(0).get("client_id"));
        assertEquals("FRESHNESS", jdbcTemplate.recordedParameters().get(0).get("signal_type_cd"));
        assertEquals("WARN", jdbcTemplate.recordedParameters().get(0).get("severity_cd"));
        assertEquals("OPEN", jdbcTemplate.recordedParameters().get(0).get("signal_status_cd"));
        assertEquals("orders", jdbcTemplate.recordedParameters().get(0).get("source_entity_ref_txt"));
        assertEquals(true, jdbcTemplate.recordedParameters().get(0).get("dq_rerun_requested_flg"));
        assertEquals(501L, record.id());
    }

    @Test
    void loadsFindQueryAndBindsFilterParameters() {
        RecordingNamedParameterJdbcTemplate jdbcTemplate = new RecordingNamedParameterJdbcTemplate();
        jdbcTemplate.setResponses(List.of(List.of(signalRow(501L, "OPEN", "WARN"))));
        JdbcObservabilitySignalDao dao = new JdbcObservabilitySignalDao(providerOf(jdbcTemplate), new SQLQueryLoaderUtil(new DefaultResourceLoader()));

        List<ObservabilitySignalRecord> records = dao.findSignals("client-a", "FRESHNESS", "WARN", "OPEN", "orders#2026-06-18");

        assertTrue(jdbcTemplate.recordedSqls().get(0).contains("FROM meta.observability_signal"));
        assertEquals("client-a", jdbcTemplate.recordedParameters().get(0).get("client_id"));
        assertEquals("FRESHNESS", jdbcTemplate.recordedParameters().get(0).get("signal_type_cd"));
        assertEquals("WARN", jdbcTemplate.recordedParameters().get(0).get("severity_cd"));
        assertEquals("OPEN", jdbcTemplate.recordedParameters().get(0).get("signal_status_cd"));
        assertEquals("orders#2026-06-18", jdbcTemplate.recordedParameters().get(0).get("correlation_key_txt"));
        assertEquals(1, records.size());
        assertEquals(501L, records.get(0).id());
    }

    @Test
    void loadsCorrelateQueryAndBindsUpdateParameters() {
        RecordingNamedParameterJdbcTemplate jdbcTemplate = new RecordingNamedParameterJdbcTemplate();
        jdbcTemplate.setResponses(List.of(List.of(signalRow(501L, "TRIAGE", "WARN"))));
        JdbcObservabilitySignalDao dao = new JdbcObservabilitySignalDao(providerOf(jdbcTemplate), new SQLQueryLoaderUtil(new DefaultResourceLoader()));

        var result = dao.correlateSignal(new ObservabilitySignalCorrelationWriteRequest(
                501L,
                "client-a",
                "TRIAGE",
                701L,
                true,
                "Create DQ rerun",
                OffsetDateTime.parse("2026-06-18T10:20:30Z"),
                OffsetDateTime.parse("2026-06-18T11:20:30Z"),
                OffsetDateTime.parse("2026-06-18T10:25:30Z"),
                "analyst"
        ));

        assertTrue(jdbcTemplate.recordedSqls().get(0).contains("UPDATE meta.observability_signal"));
        assertEquals(501L, jdbcTemplate.recordedParameters().get(0).get("id"));
        assertEquals("client-a", jdbcTemplate.recordedParameters().get(0).get("client_id"));
        assertEquals("TRIAGE", jdbcTemplate.recordedParameters().get(0).get("signal_status_cd"));
        assertEquals(701L, jdbcTemplate.recordedParameters().get(0).get("workflow_task_id"));
        assertEquals("analyst", jdbcTemplate.recordedParameters().get(0).get("updated_by"));
        assertEquals(501L, result.map(ObservabilitySignalRecord::id).orElseThrow());
    }

    @Test
    void transactionRollsBackSignalWritesOnFailure() {
        TransactionHarness harness = new TransactionHarness();
        RecordingNamedParameterJdbcTemplate jdbcTemplate = new RecordingNamedParameterJdbcTemplate();
        jdbcTemplate.setResponses(List.of(
                List.of(signalRow(501L, "OPEN", "WARN")),
                List.of(signalRow(501L, "TRIAGE", "WARN"))
        ));
        JdbcObservabilitySignalDao dao = new JdbcObservabilitySignalDao(providerOf(jdbcTemplate), new SQLQueryLoaderUtil(new DefaultResourceLoader()));
        RecordingTransactionOperations transactionOperations = new RecordingTransactionOperations(harness);

        assertThrows(IllegalStateException.class, () -> transactionOperations.execute(status -> {
            dao.insertSignal(new ObservabilitySignalWriteRequest(
                    "client-a",
                    "FRESHNESS",
                    "WARN",
                    "OPEN",
                    "PIPELINE",
                    "DATASET",
                    "orders",
                    "orders#2026-06-18",
                    "Freshness lag detected",
                    "Latest event lagged by 4h",
                    OffsetDateTime.parse("2026-06-18T10:15:30Z"),
                    true,
                    "Re-run ETL",
                    OffsetDateTime.parse("2026-06-18T10:16:30Z"),
                    "tooling",
                    OffsetDateTime.parse("2026-06-18T10:16:30Z"),
                    "tooling"
            ));
            dao.correlateSignal(new ObservabilitySignalCorrelationWriteRequest(
                    501L,
                    "client-a",
                    "TRIAGE",
                    701L,
                    true,
                    "Create DQ rerun",
                    OffsetDateTime.parse("2026-06-18T10:20:30Z"),
                    OffsetDateTime.parse("2026-06-18T11:20:30Z"),
                    OffsetDateTime.parse("2026-06-18T10:25:30Z"),
                    "analyst"
            ));
            throw new IllegalStateException("signal transaction failed");
        }));

        assertTrue(harness.rolledBack);
        assertFalse(harness.committed);
        assertEquals(2, jdbcTemplate.recordedSqls().size());
        assertTrue(jdbcTemplate.recordedSqls().get(0).contains("INSERT INTO meta.observability_signal"));
        assertTrue(jdbcTemplate.recordedSqls().get(1).contains("UPDATE meta.observability_signal"));
    }

    @Test
    void usesNamedParameterJdbcTemplateAndDoesNotUseJpa() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/lextr/semanticlayer/dao/impl/JdbcObservabilitySignalDao.java"));

        assertTrue(source.contains("NamedParameterJdbcTemplate"));
        assertFalse(source.contains("EntityManager"));
        assertFalse(source.contains("JpaRepository"));
        assertFalse(source.contains("jakarta.persistence"));
        assertFalse(source.contains("javax.persistence"));
    }

    private static Map<String, Object> signalRow(Long id, String signalStatusCode, String severityCode) {
        OffsetDateTime timestamp = OffsetDateTime.parse("2026-06-18T10:15:30Z");
        Map<String, Object> row = new HashMap<>();
        row.put("id", id);
        row.put("client_id", "client-a");
        row.put("signal_type_cd", "FRESHNESS");
        row.put("severity_cd", severityCode);
        row.put("signal_status_cd", signalStatusCode);
        row.put("source_system_cd", "PIPELINE");
        row.put("source_entity_type_cd", "DATASET");
        row.put("source_entity_ref_txt", "orders");
        row.put("correlation_key_txt", "orders#2026-06-18");
        row.put("finding_summary_txt", "Freshness lag detected");
        row.put("finding_detail_txt", "Latest event lagged by 4h");
        row.put("detected_ts", timestamp);
        row.put("acknowledged_ts", null);
        row.put("resolved_ts", null);
        row.put("workflow_task_id", 701L);
        row.put("dq_rerun_requested_flg", true);
        row.put("dq_rerun_reason_txt", "Create DQ rerun");
        row.put("created_ts", timestamp);
        row.put("created_by", "tooling");
        row.put("updated_ts", timestamp);
        row.put("updated_by", "tooling");
        return row;
    }

    private static <T> ObjectProvider<T> providerOf(T instance) {
        return new ObjectProvider<>() {
            @Override
            public T getObject(Object... args) {
                return instance;
            }

            @Override
            public T getIfAvailable() {
                return instance;
            }

            @Override
            public T getIfUnique() {
                return instance;
            }

            @Override
            public T getObject() {
                return instance;
            }

            @Override
            public Iterator<T> iterator() {
                return instance == null ? Collections.emptyIterator() : List.of(instance).iterator();
            }
        };
    }

    static final class RecordingNamedParameterJdbcTemplate extends NamedParameterJdbcTemplate {

        private List<List<Map<String, Object>>> responses = List.of();
        private final List<String> recordedSqls = new ArrayList<>();
        private final List<Map<String, Object>> recordedParameters = new ArrayList<>();

        RecordingNamedParameterJdbcTemplate() {
            super(noOpDataSource());
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
        public <T> List<T> query(String sql, org.springframework.jdbc.core.namedparam.SqlParameterSource paramSource, org.springframework.jdbc.core.RowMapper<T> rowMapper) {
            recordedSqls.add(sql);
            if (paramSource instanceof MapSqlParameterSource source) {
                recordedParameters.add(new HashMap<>(source.getValues()));
            }
            List<Map<String, Object>> rows = responses.isEmpty() ? List.of() : responses.remove(0);
            return rows.stream().map(row -> mapRow(rowMapper, row)).toList();
        }

        private <T> T mapRow(org.springframework.jdbc.core.RowMapper<T> rowMapper, Map<String, Object> row) {
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
            return new javax.sql.DataSource() {
                @Override
                public Connection getConnection() {
                    throw new UnsupportedOperationException("Not used in tests");
                }

                @Override
                public Connection getConnection(String username, String password) {
                    throw new UnsupportedOperationException("Not used in tests");
                }

                @Override
                public <T> T unwrap(Class<T> iface) {
                    throw new UnsupportedOperationException("Not used in tests");
                }

                @Override
                public boolean isWrapperFor(Class<?> iface) {
                    return false;
                }

                @Override
                public java.io.PrintWriter getLogWriter() {
                    throw new UnsupportedOperationException("Not used in tests");
                }

                @Override
                public void setLogWriter(java.io.PrintWriter out) {
                    throw new UnsupportedOperationException("Not used in tests");
                }

                @Override
                public void setLoginTimeout(int seconds) {
                    throw new UnsupportedOperationException("Not used in tests");
                }

                @Override
                public int getLoginTimeout() {
                    return 0;
                }

                @Override
                public java.util.logging.Logger getParentLogger() {
                    throw new UnsupportedOperationException("Not used in tests");
                }
            };
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
