package com.lextr.semanticlayer.dao.impl;

import com.lextr.semanticlayer.exception.SemanticLayerException;
import com.lextr.semanticlayer.model.GovernanceHistoryEventRecord;
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
import java.nio.file.Files;
import java.nio.file.Path;
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

class JdbcGovernanceHistoryReadDaoTest {

    @Test
    void bindsGovernanceHistoryParametersAndMapsReturnedColumns() {
        RecordingNamedParameterJdbcTemplate jdbcTemplate =
                new RecordingNamedParameterJdbcTemplate(List.of(historyRow("REGISTERED")));
        JdbcGovernanceHistoryReadDao dao = new JdbcGovernanceHistoryReadDao(
                providerOf(jdbcTemplate),
                new SQLQueryLoaderUtil(new DefaultResourceLoader())
        );

        List<GovernanceHistoryEventRecord> results =
                dao.findEvents("client-a", "OBJECT", "meta.gl_balance", "REGISTERED");

        assertEquals(1, results.size());
        GovernanceHistoryEventRecord result = results.get(0);
        assertTrue(jdbcTemplate.recordedSql.contains("FROM meta.metadata_change_history mch"));
        assertEquals("client-a", jdbcTemplate.recordedParameters.get("client_id"));
        assertEquals("OBJECT", jdbcTemplate.recordedParameters.get("entity_type_cd"));
        assertEquals("meta.gl_balance", jdbcTemplate.recordedParameters.get("entity_ref"));
        assertEquals("REGISTERED", jdbcTemplate.recordedParameters.get("change_type_cd"));
        assertEquals(501L, result.event_id());
        assertEquals("client-a", result.client_id());
        assertEquals("OBJECT", result.entity_type_cd());
        assertEquals("meta.gl_balance", result.entity_ref());
        assertEquals("REGISTERED", result.change_type_cd());
        assertEquals("Object registered", result.change_summary_txt());
        assertEquals("producer", result.actor_id());
        assertEquals(OffsetDateTime.parse("2026-06-18T00:00:00Z"), result.event_ts());
        assertEquals("{\"status\":\"DRAFT\"}", result.old_value_json());
        assertEquals("{\"status\":\"ACTIVE\"}", result.new_value_json());
    }

    @Test
    void failsWhenNamedParameterJdbcTemplateMissing() {
        JdbcGovernanceHistoryReadDao dao = new JdbcGovernanceHistoryReadDao(
                providerOf(null),
                new SQLQueryLoaderUtil(new DefaultResourceLoader())
        );

        assertThrows(SemanticLayerException.class,
                () -> dao.findEvents("client-a", "OBJECT", "meta.gl_balance", null));
    }

    @Test
    void usesNamedParameterJdbcTemplateAndDoesNotUseJpa() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/lextr/semanticlayer/dao/impl/JdbcGovernanceHistoryReadDao.java"
        ));

        assertTrue(source.contains("NamedParameterJdbcTemplate"));
        assertTrue(source.contains("sqlQueryLoaderUtil.getQuery(FIND_BY_ENTITY)"));
        assertFalse(source.contains("EntityManager"));
        assertFalse(source.contains("JpaRepository"));
        assertFalse(source.contains("jakarta.persistence"));
        assertFalse(source.contains("javax.persistence"));
    }

    private static Map<String, Object> historyRow(String changeTypeCode) {
        Map<String, Object> row = new HashMap<>();
        row.put("event_id", 501L);
        row.put("client_id", "client-a");
        row.put("entity_type_cd", "OBJECT");
        row.put("entity_ref", "meta.gl_balance");
        row.put("change_type_cd", changeTypeCode);
        row.put("change_summary_txt", "Object registered");
        row.put("actor_id", "producer");
        row.put("event_ts", OffsetDateTime.parse("2026-06-18T00:00:00Z"));
        row.put("old_value_json", "{\"status\":\"DRAFT\"}");
        row.put("new_value_json", "{\"status\":\"ACTIVE\"}");
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
}
