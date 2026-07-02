package com.lextr.semanticlayer.dao.impl;

import com.lextr.semanticlayer.model.ExternalRuleResultRecord;
import com.lextr.semanticlayer.model.ExternalRuleResultWriteRequest;
import com.lextr.semanticlayer.model.ObjectExposureAccessAuditWriteRequest;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayDeque;
import java.util.Deque;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JdbcRuleResultDaoTest {

    @Test
    void bindsInsertParametersAndMapsReturnedColumns() {
        RecordingNamedParameterJdbcTemplate jdbcTemplate = new RecordingNamedParameterJdbcTemplate(List.of(resultRow()));
        JdbcRuleResultDao dao = new JdbcRuleResultDao(providerOf(jdbcTemplate), new SQLQueryLoaderUtil(new DefaultResourceLoader()));

        ExternalRuleResultRecord result = dao.insertResult(request());

        assertTrue(jdbcTemplate.recordedSql.contains("INSERT INTO meta.external_rule_result"));
        assertEquals("client-a", jdbcTemplate.recordedParameters.get("client_id"));
        assertEquals(101L, jdbcTemplate.recordedParameters.get("outbound_id"));
        assertEquals("RULE-100", jdbcTemplate.recordedParameters.get("rule_ref_cd"));
        assertEquals("VALUE", jdbcTemplate.recordedParameters.get("output_kind_cd"));
        assertEquals("{\"verdict\":\"PASS\"}", jdbcTemplate.recordedParameters.get("output_payload_jsonb"));
        assertEquals(Long.valueOf(401L), result.id());
        assertEquals("RULE-100", result.rule_ref_cd());
        assertEquals("VALUE", result.output_kind_cd());
    }

    @Test
    void bindsAuditParameters() {
        RecordingNamedParameterJdbcTemplate jdbcTemplate = new RecordingNamedParameterJdbcTemplate(List.of());
        JdbcRuleResultDao dao = new JdbcRuleResultDao(providerOf(jdbcTemplate), new SQLQueryLoaderUtil(new DefaultResourceLoader()));

        dao.insertMetadataChangeHistory(new ObjectExposureAccessAuditWriteRequest(
                "EXTERNAL_RULE_RESULT",
                "101:RULE-100",
                "INGESTED",
                "ENGINE",
                OffsetDateTime.parse("2026-06-18T10:15:30Z"),
                "Ingested VALUE rule result"
        ));

        assertTrue(jdbcTemplate.recordedUpdateSql.contains("INSERT INTO meta.metadata_change_history"));
        assertEquals("EXTERNAL_RULE_RESULT", jdbcTemplate.recordedUpdateParameters.get("entity_type_cd"));
        assertEquals("101:RULE-100", jdbcTemplate.recordedUpdateParameters.get("entity_ref"));
        assertEquals("INGESTED", jdbcTemplate.recordedUpdateParameters.get("change_type_cd"));
        assertEquals("ENGINE", jdbcTemplate.recordedUpdateParameters.get("changed_by"));
        assertEquals("Ingested VALUE rule result", jdbcTemplate.recordedUpdateParameters.get("change_reason_txt"));
    }

    private static ExternalRuleResultWriteRequest request() {
        return new ExternalRuleResultWriteRequest(
                "client-a",
                101L,
                "RULE-100",
                "VALUE",
                "{\"verdict\":\"PASS\"}",
                OffsetDateTime.parse("2026-06-18T10:15:30Z"),
                OffsetDateTime.parse("2026-06-18T10:15:30Z"),
                "ENGINE",
                OffsetDateTime.parse("2026-06-18T10:15:30Z"),
                "ENGINE"
        );
    }

    private static Map<String, Object> resultRow() {
        Map<String, Object> row = new HashMap<>();
        row.put("id", 401L);
        row.put("client_id", "client-a");
        row.put("outbound_id", 101L);
        row.put("rule_ref_cd", "RULE-100");
        row.put("output_kind_cd", "VALUE");
        row.put("output_payload_jsonb", "{\"verdict\":\"PASS\"}");
        row.put("observed_ts", OffsetDateTime.parse("2026-06-18T10:15:30Z"));
        row.put("created_ts", OffsetDateTime.parse("2026-06-18T10:15:30Z"));
        row.put("created_by", "ENGINE");
        row.put("updated_ts", OffsetDateTime.parse("2026-06-18T10:15:30Z"));
        row.put("updated_by", "ENGINE");
        return row;
    }

    private static ObjectProvider<NamedParameterJdbcTemplate> providerOf(NamedParameterJdbcTemplate jdbcTemplate) {
        return new ObjectProvider<>() {
            @Override
            public NamedParameterJdbcTemplate getObject(Object... args) {
                return jdbcTemplate;
            }

            @Override
            public NamedParameterJdbcTemplate getObject() {
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
        };
    }

    private static final class RecordingNamedParameterJdbcTemplate extends NamedParameterJdbcTemplate {

        private final Deque<List<Map<String, Object>>> rowsQueue = new ArrayDeque<>();
        private String recordedSql;
        private Map<String, Object> recordedParameters = Map.of();
        private String recordedUpdateSql;
        private Map<String, Object> recordedUpdateParameters = Map.of();

        private RecordingNamedParameterJdbcTemplate(List<Map<String, Object>> rows) {
            super(noOpDataSource());
            rowsQueue.add(rows);
        }

        @Override
        public <T> List<T> query(String sql, SqlParameterSource paramSource, RowMapper<T> rowMapper) {
            recordedSql = sql;
            recordedParameters = paramSource instanceof MapSqlParameterSource source ? source.getValues() : Map.of();
            List<Map<String, Object>> rows = rowsQueue.isEmpty() ? List.of() : rowsQueue.removeFirst();
            List<T> mappedRows = new ArrayList<>();
            for (int i = 0; i < rows.size(); i++) {
                mappedRows.add(mapRow(rowMapper, rows.get(i), i));
            }
            return mappedRows;
        }

        @Override
        public int update(String sql, SqlParameterSource paramSource) {
            recordedUpdateSql = sql;
            recordedUpdateParameters = paramSource instanceof MapSqlParameterSource source ? source.getValues() : Map.of();
            return 1;
        }

        private <T> T mapRow(RowMapper<T> rowMapper, Map<String, Object> row, int rowNum) {
            try {
                return rowMapper.mapRow(resultSet(row), rowNum);
            } catch (SQLException exception) {
                throw new RuntimeException(exception);
            }
        }

        private ResultSet resultSet(Map<String, Object> row) {
            return (ResultSet) Proxy.newProxyInstance(
                    getClass().getClassLoader(),
                    new Class<?>[]{ResultSet.class},
                    (proxy, method, args) -> {
                        String name = method.getName();
                        if ("getObject".equals(name) && args != null && args.length == 2 && args[1] instanceof Class<?> targetType) {
                            Object value = row.get(args[0]);
                            if (value == null) {
                                return null;
                            }
                            if (targetType.isInstance(value)) {
                                return value;
                            }
                            if (targetType == Long.class && value instanceof Number number) {
                                return number.longValue();
                            }
                            if (targetType == Integer.class && value instanceof Number number) {
                                return number.intValue();
                            }
                            if (targetType == OffsetDateTime.class && value instanceof String text) {
                                return OffsetDateTime.parse(text);
                            }
                            return value;
                        }
                        if ("getString".equals(name) && args != null && args.length == 1) {
                            Object value = row.get(args[0]);
                            return value == null ? null : value.toString();
                        }
                        if ("next".equals(name)) {
                            return false;
                        }
                        if ("wasNull".equals(name)) {
                            return false;
                        }
                        if ("close".equals(name)) {
                            return null;
                        }
                        Class<?> returnType = method.getReturnType();
                        if (returnType.equals(boolean.class)) {
                            return false;
                        }
                        if (returnType.equals(int.class)) {
                            return 0;
                        }
                        if (returnType.equals(long.class)) {
                            return 0L;
                        }
                        return null;
                    }
            );
        }

        private static DataSource noOpDataSource() {
            return new AbstractDataSource() {
                @Override
                public Connection getConnection() {
                    return (Connection) Proxy.newProxyInstance(
                            getClass().getClassLoader(),
                            new Class<?>[]{Connection.class},
                            (proxy, method, args) -> {
                                if ("close".equals(method.getName())) {
                                    return null;
                                }
                                Class<?> returnType = method.getReturnType();
                                if (returnType.equals(boolean.class)) {
                                    return false;
                                }
                                if (returnType.equals(int.class)) {
                                    return 0;
                                }
                                if (returnType.equals(long.class)) {
                                    return 0L;
                                }
                                return null;
                            }
                    );
                }

                @Override
                public Connection getConnection(String username, String password) {
                    return getConnection();
                }
            };
        }
    }
}
