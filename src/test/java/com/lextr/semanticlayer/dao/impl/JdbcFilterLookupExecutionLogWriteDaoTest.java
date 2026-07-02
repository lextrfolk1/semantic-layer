package com.lextr.semanticlayer.dao.impl;

import com.lextr.semanticlayer.exception.SemanticLayerException;
import com.lextr.semanticlayer.model.FilterLookupExecutionLogRecord;
import com.lextr.semanticlayer.model.FilterLookupExecutionLogWriteRequest;
import com.lextr.semanticlayer.util.SQLQueryLoaderUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.jdbc.datasource.AbstractDataSource;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JdbcFilterLookupExecutionLogWriteDaoTest {

    @Test
    void bindsExecutionLogParametersAndMapsSnakeCaseColumns() {
        RecordingNamedParameterJdbcTemplate jdbcTemplate =
                new RecordingNamedParameterJdbcTemplate(List.of(executionLogRow()));
        JdbcFilterLookupExecutionLogWriteDao dao = new JdbcFilterLookupExecutionLogWriteDao(
                providerOf(jdbcTemplate),
                new SQLQueryLoaderUtil(new DefaultResourceLoader())
        );

        FilterLookupExecutionLogRecord result = dao.insertExecutionLog(executionLogRequest());

        assertTrue(jdbcTemplate.recordedSql.contains("INSERT INTO meta.filter_lookup_exec_log"));
        assertEquals("LEDGER_SCOPE", jdbcTemplate.recordedParameters.get("lookup_cd"));
        assertEquals("preview-user", jdbcTemplate.recordedParameters.get("executed_by"));
        assertEquals(Integer.valueOf(18), jdbcTemplate.recordedParameters.get("phase1_duration_ms"));
        assertEquals(Integer.valueOf(42), jdbcTemplate.recordedParameters.get("phase1_row_count"));
        assertEquals("IN_LIST", jdbcTemplate.recordedParameters.get("execution_strategy_used_cd"));
        assertEquals("SUCCESS", jdbcTemplate.recordedParameters.get("result_status_cd"));
        assertEquals(Long.valueOf(801L), result.id());
        assertEquals("LEDGER_SCOPE", result.lookup_cd());
        assertEquals("preview-user", result.executed_by());
        assertEquals(42, result.phase1_row_count());
        assertEquals("SUCCESS", result.result_status_cd());
    }

    @Test
    void failsWhenNamedParameterJdbcTemplateMissing() {
        JdbcFilterLookupExecutionLogWriteDao dao = new JdbcFilterLookupExecutionLogWriteDao(
                providerOf(null),
                new SQLQueryLoaderUtil(new DefaultResourceLoader())
        );

        assertThrows(SemanticLayerException.class, () -> dao.insertExecutionLog(executionLogRequest()));
    }

    @Test
    void usesNamedParameterJdbcTemplateAndDoesNotUseJpa() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/lextr/semanticlayer/dao/impl/JdbcFilterLookupExecutionLogWriteDao.java"
        ));

        assertTrue(source.contains("NamedParameterJdbcTemplate"));
        assertFalse(source.contains("EntityManager"));
        assertFalse(source.contains("JpaRepository"));
        assertFalse(source.contains("jakarta.persistence"));
        assertFalse(source.contains("javax.persistence"));
    }

    private static FilterLookupExecutionLogWriteRequest executionLogRequest() {
        return new FilterLookupExecutionLogWriteRequest(
                "LEDGER_SCOPE",
                "preview-user",
                OffsetDateTime.parse("2026-06-18T10:15:30Z"),
                18,
                42,
                false,
                "IN_LIST",
                null,
                "SUCCESS",
                null,
                null
        );
    }

    private static Map<String, Object> executionLogRow() {
        Map<String, Object> row = new HashMap<>();
        row.put("id", 801L);
        row.put("lookup_cd", "LEDGER_SCOPE");
        row.put("executed_by", "preview-user");
        row.put("executed_ts", OffsetDateTime.parse("2026-06-18T10:15:30Z"));
        row.put("phase1_duration_ms", 18);
        row.put("phase1_row_count", 42);
        row.put("phase1_cache_hit_flg", false);
        row.put("execution_strategy_used_cd", "IN_LIST");
        row.put("phase2_duration_ms", null);
        row.put("result_status_cd", "SUCCESS");
        row.put("error_txt", null);
        row.put("blocked_by_policy_cd", null);
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
            this.recordedSql = sql;
            if (paramSource instanceof MapSqlParameterSource source) {
                this.recordedParameters = source.getValues();
            }
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
