package com.lextr.semanticlayer.dao.impl;

import com.lextr.semanticlayer.exception.SemanticLayerException;
import com.lextr.semanticlayer.model.LogicalPhysicalResolutionRecord;
import com.lextr.semanticlayer.util.SQLQueryLoaderUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.jdbc.datasource.AbstractDataSource;

import javax.sql.DataSource;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JdbcLogicalPhysicalResolutionDaoTest {

    @Test
    void bindsAttributeResolutionParametersAndMapsEffectiveOverrides() {
        RecordingNamedParameterJdbcTemplate jdbcTemplate = new RecordingNamedParameterJdbcTemplate(List.of(List.of(attributeRow())));
        JdbcLogicalPhysicalResolutionDao dao = new JdbcLogicalPhysicalResolutionDao(providerOf(jdbcTemplate), new SQLQueryLoaderUtil(new DefaultResourceLoader()));

        List<LogicalPhysicalResolutionRecord> results = dao.findByAttributes(
                "client-a",
                "meta",
                "ledger",
                List.of("ledger_id", "account_nm")
        );

        assertTrue(jdbcTemplate.recordedSqls().get(0).contains("FROM meta.object_catalog o"));
        assertTrue(jdbcTemplate.recordedSqls().get(0).contains("IN (:logical_attribute_cds)"));
        assertEquals("client-a", jdbcTemplate.recordedParameters().get("client_id"));
        assertEquals("meta", jdbcTemplate.recordedParameters().get("schema_cd"));
        assertEquals("ledger", jdbcTemplate.recordedParameters().get("object_cd"));
        assertEquals(List.of("ledger_id", "account_nm"), jdbcTemplate.recordedParameters().get("logical_attribute_cds"));
        assertEquals(1, results.size());
        assertEquals("ledger_id", results.get(0).logical_attribute_cd());
        assertEquals("Ledger Identifier Override", results.get(0).effective_logical_attribute_nm());
        assertEquals("ledger_id", results.get(0).physical_attribute_nm());
        assertEquals("ledger_source", results.get(0).source_object_nm());
        assertEquals("POSTGRES", results.get(0).engine_cd());
        assertEquals("NUMBER", results.get(0).data_type_cd());
    }

    @Test
    void returnsEmptyListWithoutQueryWhenNoLogicalAttributesProvided() {
        RecordingNamedParameterJdbcTemplate jdbcTemplate = new RecordingNamedParameterJdbcTemplate(List.of());
        JdbcLogicalPhysicalResolutionDao dao = new JdbcLogicalPhysicalResolutionDao(providerOf(jdbcTemplate), new SQLQueryLoaderUtil(new DefaultResourceLoader()));

        List<LogicalPhysicalResolutionRecord> results = dao.findByAttributes("client-a", "meta", "ledger", List.of());

        assertTrue(results.isEmpty());
        assertTrue(jdbcTemplate.recordedSqls().isEmpty());
    }

    @Test
    void bindsOutboundResolutionParametersAndMapsGrainRows() {
        RecordingNamedParameterJdbcTemplate jdbcTemplate = new RecordingNamedParameterJdbcTemplate(List.of(List.of(outboundRow())));
        JdbcLogicalPhysicalResolutionDao dao = new JdbcLogicalPhysicalResolutionDao(providerOf(jdbcTemplate), new SQLQueryLoaderUtil(new DefaultResourceLoader()));

        List<LogicalPhysicalResolutionRecord> results = dao.findByOutboundGrain("client-a", 77L);

        assertTrue(jdbcTemplate.recordedSqls().get(0).contains("FROM meta.consumption_outbound co"));
        assertTrue(jdbcTemplate.recordedSqls().get(0).contains("cog.logical_attribute_cd"));
        assertEquals("client-a", jdbcTemplate.recordedParameters().get("client_id"));
        assertEquals(77L, jdbcTemplate.recordedParameters().get("outbound_id"));
        assertEquals(1, results.size());
        assertEquals(77L, results.get(0).outbound_id());
        assertEquals("OB-77", results.get(0).outbound_cd());
        assertEquals(1, results.get(0).grain_level_nbr());
        assertEquals("ledger_id", results.get(0).logical_attribute_cd());
        assertEquals("Ledger Identifier Override", results.get(0).effective_logical_attribute_nm());
        assertEquals("ledger_id", results.get(0).physical_attribute_nm());
        assertEquals("ledger_source", results.get(0).source_object_nm());
        assertEquals("POSTGRES", results.get(0).engine_cd());
        assertEquals("NUMBER", results.get(0).data_type_cd());
    }

    @Test
    void failsWhenNamedParameterJdbcTemplateMissing() {
        JdbcLogicalPhysicalResolutionDao dao = new JdbcLogicalPhysicalResolutionDao(providerOf(null), new SQLQueryLoaderUtil(new DefaultResourceLoader()));

        assertThrows(SemanticLayerException.class, () -> dao.findByAttributes("client-a", "meta", "ledger", List.of("ledger_id")));
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

    private static Map<String, Object> attributeRow() {
        Map<String, Object> row = new HashMap<>();
        row.put("outbound_id", null);
        row.put("outbound_cd", null);
        row.put("grain_level_nbr", null);
        row.put("client_id", "client-a");
        row.put("schema_cd", "meta");
        row.put("object_cd", "ledger");
        row.put("logical_attribute_cd", "ledger_id");
        row.put("effective_logical_attribute_nm", "Ledger Identifier Override");
        row.put("physical_attribute_nm", "ledger_id");
        row.put("source_object_nm", "ledger_source");
        row.put("engine_cd", "POSTGRES");
        row.put("data_type_cd", "NUMBER");
        return row;
    }

    private static Map<String, Object> outboundRow() {
        Map<String, Object> row = new HashMap<>();
        row.put("outbound_id", 77L);
        row.put("outbound_cd", "OB-77");
        row.put("grain_level_nbr", 1);
        row.put("client_id", "client-a");
        row.put("schema_cd", "meta");
        row.put("object_cd", "ledger");
        row.put("logical_attribute_cd", "ledger_id");
        row.put("effective_logical_attribute_nm", "Ledger Identifier Override");
        row.put("physical_attribute_nm", "ledger_id");
        row.put("source_object_nm", "ledger_source");
        row.put("engine_cd", "POSTGRES");
        row.put("data_type_cd", "NUMBER");
        return row;
    }

    static final class RecordingNamedParameterJdbcTemplate extends NamedParameterJdbcTemplate {

        private final List<List<Map<String, Object>>> responses;
        private final List<String> recordedSqls = new ArrayList<>();
        private final List<Map<String, Object>> recordedParameters = new ArrayList<>();

        RecordingNamedParameterJdbcTemplate(List<List<Map<String, Object>>> responses) {
            super(new AbstractDataSource() {
                @Override
                public Connection getConnection() {
                    throw new UnsupportedOperationException("Connection access not expected");
                }

                @Override
                public Connection getConnection(String username, String password) {
                    throw new UnsupportedOperationException("Connection access not expected");
                }
            });
            this.responses = new ArrayList<>(responses);
        }

        @Override
        public <T> List<T> query(String sql, SqlParameterSource paramSource, RowMapper<T> rowMapper) {
            recordedSqls.add(sql);
            recordedParameters.add(parameterMap(paramSource));
            List<Map<String, Object>> rows = responses.isEmpty() ? List.of() : responses.remove(0);
            List<T> mappedRows = new ArrayList<>();
            for (int i = 0; i < rows.size(); i++) {
                mappedRows.add(mapRow(rowMapper, rows.get(i), i));
            }
            return mappedRows;
        }

        List<String> recordedSqls() {
            return recordedSqls;
        }

        Map<String, Object> recordedParameters() {
            return recordedParameters.isEmpty() ? Map.of() : recordedParameters.get(recordedParameters.size() - 1);
        }

        private Map<String, Object> parameterMap(SqlParameterSource paramSource) {
            if (paramSource instanceof org.springframework.jdbc.core.namedparam.MapSqlParameterSource mapSqlParameterSource) {
                return new HashMap<>(mapSqlParameterSource.getValues());
            }
            return Map.of();
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
                    JdbcLogicalPhysicalResolutionDaoTest.class.getClassLoader(),
                    new Class[]{ResultSet.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "getString" -> valueAsString(row.get(args[0]));
                        case "getObject" -> row.get(args[0]);
                        case "wasNull" -> false;
                        default -> throw new UnsupportedOperationException(method.getName());
                    }
            );
        }

        private String valueAsString(Object value) {
            return value == null ? null : String.valueOf(value);
        }
    }
}
