package com.lextr.semanticlayer.dao.impl;

import com.lextr.semanticlayer.model.ConsumptionLayerRecord;
import com.lextr.semanticlayer.model.ConsumptionOutboundRecord;
import com.lextr.semanticlayer.model.ConsumptionPromotionRecord;
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

class JdbcConsumptionDaoTest {

    @Test
    void loadsAndBindsConsumptionQueriesFromProperties() {
        RecordingNamedParameterJdbcTemplate jdbcTemplate = new RecordingNamedParameterJdbcTemplate();
        jdbcTemplate.enqueueRows(List.of(layerRow()));
        jdbcTemplate.enqueueRows(List.of(exposureRow()));
        jdbcTemplate.enqueueRows(List.of(promotionRow()));
        JdbcConsumptionDao dao = new JdbcConsumptionDao(jdbcTemplateProvider(jdbcTemplate), new SQLQueryLoaderUtil(new DefaultResourceLoader()), new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules());

        List<ConsumptionLayerRecord> layers = dao.findLayers("client-a", "ACTIVE");
        List<ConsumptionOutboundRecord> exposures = dao.findExposures("client-a", UUID.fromString("00000000-0000-0000-0000-000000000101"), "TECHNICAL");
        ConsumptionPromotionRecord promotion = dao.findLatestPromotion("client-a", 101L).orElseThrow();

        assertEquals(1, layers.size());
        assertEquals("CL-01", layers.get(0).layer_cd());
        assertEquals(1, exposures.size());
        assertEquals("OB-01", exposures.get(0).outbound_cd());
        assertEquals("QA", promotion.target_sdlc_status_cd());
        assertTrue(jdbcTemplate.recordedSqls.stream().anyMatch(sql -> sql.contains("FROM meta.consumption_layer")));
        assertTrue(jdbcTemplate.recordedSqls.stream().anyMatch(sql -> sql.contains("FROM meta.consumption_outbound")));
        assertTrue(jdbcTemplate.recordedSqls.stream().anyMatch(sql -> sql.contains("FROM meta.consumption_promotion")));
        assertEquals("client-a", jdbcTemplate.recordedParameters.get(0).get("client_id"));
        assertEquals("ACTIVE", jdbcTemplate.recordedParameters.get(0).get("lifecycle_status_cd"));
        assertEquals("TECHNICAL", jdbcTemplate.recordedParameters.get(1).get("structure_type_cd"));
        assertEquals(101L, jdbcTemplate.recordedParameters.get(2).get("outbound_id"));
    }

    @Test
    void bindsPromotionWriteQueriesAndReturnsMappedRow() {
        RecordingNamedParameterJdbcTemplate jdbcTemplate = new RecordingNamedParameterJdbcTemplate();
        jdbcTemplate.enqueueRows(List.of(promotionRow()));
        JdbcConsumptionDao dao = new JdbcConsumptionDao(jdbcTemplateProvider(jdbcTemplate), new SQLQueryLoaderUtil(new DefaultResourceLoader()), new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules());

        ConsumptionPromotionRecord created = dao.insertPromotionRequest(
                "client-a",
                101L,
                "DEV",
                "QA",
                "PENDING",
                "PENDING",
                501L,
                "PENDING_APPROVAL",
                2,
                OffsetDateTime.parse("2026-06-20T10:15:30Z"),
                "approver",
                OffsetDateTime.parse("2026-06-20T10:15:30Z"),
                "approver"
        );

        assertEquals("QA", created.target_sdlc_status_cd());
        assertTrue(jdbcTemplate.recordedSqls.stream().anyMatch(sql -> sql.contains("INSERT INTO meta.consumption_promotion")));
        assertEquals(101L, jdbcTemplate.recordedParameters.get(0).get("outbound_id"));
        assertEquals("QA", jdbcTemplate.recordedParameters.get(0).get("target_sdlc_status_cd"));
        assertEquals("PENDING_APPROVAL", jdbcTemplate.recordedParameters.get(0).get("promotion_status_cd"));
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

    private static Map<String, Object> layerRow() {
        Map<String, Object> row = new HashMap<>();
        row.put("id", 11L);
        row.put("client_id", "client-a");
        row.put("layer_cd", "CL-01");
        row.put("layer_nm", "Finance Layer");
        row.put("layer_desc_txt", "Finance outbound descriptor");
        row.put("layer_type_cd", "DATA_ASSET");
        row.put("lifecycle_status_cd", "ACTIVE");
        row.put("created_ts", OffsetDateTime.parse("2026-06-20T10:15:30Z"));
        row.put("created_by", "owner");
        row.put("updated_ts", OffsetDateTime.parse("2026-06-20T10:15:30Z"));
        row.put("updated_by", "owner");
        return row;
    }

    private static Map<String, Object> exposureRow() {
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
        row.put("created_ts", OffsetDateTime.parse("2026-06-20T10:15:30Z"));
        row.put("created_by", "owner");
        row.put("updated_ts", OffsetDateTime.parse("2026-06-20T10:15:30Z"));
        row.put("updated_by", "owner");
        return row;
    }

    private static Map<String, Object> promotionRow() {
        Map<String, Object> row = new HashMap<>();
        row.put("id", 301L);
        row.put("client_id", "client-a");
        row.put("outbound_id", 101L);
        row.put("source_sdlc_status_cd", "DEV");
        row.put("target_sdlc_status_cd", "QA");
        row.put("validation_status_cd", "PENDING");
        row.put("opa_decision_cd", "PENDING");
        row.put("workflow_task_id", 501L);
        row.put("promotion_status_cd", "PENDING_APPROVAL");
        row.put("version_nbr", 2);
        row.put("applied_ts", OffsetDateTime.parse("2026-06-20T10:15:30Z"));
        row.put("applied_by", "approver");
        row.put("created_ts", OffsetDateTime.parse("2026-06-20T10:15:30Z"));
        row.put("created_by", "approver");
        row.put("updated_ts", OffsetDateTime.parse("2026-06-20T10:15:30Z"));
        row.put("updated_by", "approver");
        return row;
    }

    static final class RecordingNamedParameterJdbcTemplate extends NamedParameterJdbcTemplate {

        private final java.util.Deque<List<Map<String, Object>>> rowsQueue = new java.util.ArrayDeque<>();
        private final List<String> recordedSqls = new java.util.ArrayList<>();
        private final List<Map<String, Object>> recordedParameters = new java.util.ArrayList<>();

        RecordingNamedParameterJdbcTemplate() {
            super(noOpDataSource());
        }

        void enqueueRows(List<Map<String, Object>> rows) {
            this.rowsQueue.addLast(rows);
        }

        @Override
        public <T> List<T> query(String sql, SqlParameterSource paramSource, RowMapper<T> rowMapper) {
            recordedSqls.add(sql);
            if (paramSource instanceof MapSqlParameterSource source) {
                recordedParameters.add(source.getValues());
            } else {
                recordedParameters.add(Map.of());
            }
            List<Map<String, Object>> rows = rowsQueue.isEmpty() ? List.of() : rowsQueue.removeFirst();
            return rows.stream().map(row -> mapRow(rowMapper, row)).toList();
        }

        @Override
        public int update(String sql, SqlParameterSource paramSource) {
            recordedSqls.add(sql);
            if (paramSource instanceof MapSqlParameterSource source) {
                recordedParameters.add(source.getValues());
            } else {
                recordedParameters.add(Map.of());
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
