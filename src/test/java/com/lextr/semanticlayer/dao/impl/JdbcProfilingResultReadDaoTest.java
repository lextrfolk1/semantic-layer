package com.lextr.semanticlayer.dao.impl;

import com.lextr.semanticlayer.model.ProfilingResultRecord;
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
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JdbcProfilingResultReadDaoTest {

    @Test
    void loadsAndBindsProfilingQueryFromProperties() {
        RecordingNamedParameterJdbcTemplate jdbcTemplate = new RecordingNamedParameterJdbcTemplate();
        jdbcTemplate.setRows(List.of(profilingRow()));
        JdbcProfilingResultReadDao dao = new JdbcProfilingResultReadDao(jdbcTemplateProvider(jdbcTemplate), new SQLQueryLoaderUtil(new DefaultResourceLoader()));

        List<ProfilingResultRecord> results = dao.findMetrics("client-a", "meta", "GL_BALANCE", "COMPLETED");

        assertEquals(1, results.size());
        assertEquals("ledger_id", results.get(0).logical_attribute_cd());
        assertTrue(jdbcTemplate.recordedSql.contains("FROM meta.profiling_result"));
        assertEquals("client-a", jdbcTemplate.recordedParameters.get("client_id"));
        assertEquals("meta", jdbcTemplate.recordedParameters.get("schema_cd"));
        assertEquals("GL_BALANCE", jdbcTemplate.recordedParameters.get("object_cd"));
        assertEquals("COMPLETED", jdbcTemplate.recordedParameters.get("profiling_status_cd"));
    }

    private static ObjectProvider<NamedParameterJdbcTemplate> jdbcTemplateProvider(NamedParameterJdbcTemplate jdbcTemplate) {
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
        };
    }

    private static Map<String, Object> profilingRow() {
        Map<String, Object> row = new HashMap<>();
        row.put("id", 101L);
        row.put("client_id", "client-a");
        row.put("schema_cd", "meta");
        row.put("object_cd", "GL_BALANCE");
        row.put("logical_attribute_cd", "ledger_id");
        row.put("attribute_role_cd", "SOURCE");
        row.put("null_pct_nbr", 0);
        row.put("distinct_pct_nbr", 100);
        row.put("profiling_status_cd", "COMPLETED");
        row.put("last_profiled_ts", OffsetDateTime.parse("2026-06-18T10:15:30Z"));
        row.put("created_ts", OffsetDateTime.parse("2026-06-18T10:15:30Z"));
        row.put("created_by", "profiler");
        row.put("updated_ts", OffsetDateTime.parse("2026-06-18T11:15:30Z"));
        row.put("updated_by", "profiler");
        return row;
    }

    static final class RecordingNamedParameterJdbcTemplate extends NamedParameterJdbcTemplate {

        private List<Map<String, Object>> rows = List.of();
        private String recordedSql = "";
        private Map<String, Object> recordedParameters = Map.of();

        RecordingNamedParameterJdbcTemplate() {
            super(noOpDataSource());
        }

        void setRows(List<Map<String, Object>> rows) {
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
            if (returnType == boolean.class) return false;
            if (returnType == byte.class) return (byte) 0;
            if (returnType == short.class) return (short) 0;
            if (returnType == int.class) return 0;
            if (returnType == long.class) return 0L;
            if (returnType == float.class) return 0F;
            if (returnType == double.class) return 0D;
            if (returnType == char.class) return '\0';
            return null;
        }
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
