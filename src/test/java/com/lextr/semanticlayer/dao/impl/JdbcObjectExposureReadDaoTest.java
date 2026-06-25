package com.lextr.semanticlayer.dao.impl;

import com.lextr.semanticlayer.exception.SemanticLayerException;
import com.lextr.semanticlayer.model.AttributeExposureRecord;
import com.lextr.semanticlayer.model.ObjectExposureRecord;
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
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JdbcObjectExposureReadDaoTest {

    @Test
    void bindsObjectListParametersAndMapsSnakeCaseColumns() {
        UUID objectId = UUID.fromString("00000000-0000-0000-0000-000000000101");
        UUID connectionId = UUID.fromString("00000000-0000-0000-0000-000000000201");
        RecordingNamedParameterJdbcTemplate jdbcTemplate = new RecordingNamedParameterJdbcTemplate(List.of(objectRow(objectId, connectionId)));
        JdbcObjectExposureReadDao dao = new JdbcObjectExposureReadDao(providerOf(jdbcTemplate), new SQLQueryLoaderUtil(new DefaultResourceLoader()));

        List<ObjectExposureRecord> results = dao.findObjects("client-a", "meta", "DRAFT");

        assertTrue(jdbcTemplate.recordedSql.contains("client_id = :client_id"));
        assertEquals("client-a", jdbcTemplate.recordedParameters.get("client_id"));
        assertEquals("meta", jdbcTemplate.recordedParameters.get("schema_cd"));
        assertEquals("DRAFT", jdbcTemplate.recordedParameters.get("lifecycle_status_cd"));
        assertEquals(1, results.size());
        assertEquals(objectId, results.get(0).object_id());
        assertEquals("GL Balance", results.get(0).effective_object_nm());
        assertEquals("DRAFT", results.get(0).lifecycle_status_cd());
    }

    @Test
    void findsSingleObjectByIdWithinClientScope() {
        UUID objectId = UUID.fromString("00000000-0000-0000-0000-000000000101");
        UUID connectionId = UUID.fromString("00000000-0000-0000-0000-000000000201");
        RecordingNamedParameterJdbcTemplate jdbcTemplate = new RecordingNamedParameterJdbcTemplate(List.of(objectRow(objectId, connectionId)));
        JdbcObjectExposureReadDao dao = new JdbcObjectExposureReadDao(providerOf(jdbcTemplate), new SQLQueryLoaderUtil(new DefaultResourceLoader()));

        ObjectExposureRecord result = dao.findObject("client-a", objectId).orElseThrow();

        assertTrue(jdbcTemplate.recordedSql.contains("object_id = :object_id"));
        assertEquals("client-a", jdbcTemplate.recordedParameters.get("client_id"));
        assertEquals(objectId, jdbcTemplate.recordedParameters.get("object_id"));
        assertEquals("GL_BALANCE", result.object_cd());
        assertEquals("GL Balance", result.effective_object_nm());
    }

    @Test
    void findsSingleObjectBySchemaAndCode() {
        UUID objectId = UUID.fromString("00000000-0000-0000-0000-000000000101");
        UUID connectionId = UUID.fromString("00000000-0000-0000-0000-000000000201");
        RecordingNamedParameterJdbcTemplate jdbcTemplate = new RecordingNamedParameterJdbcTemplate(List.of(objectRow(objectId, connectionId)));
        JdbcObjectExposureReadDao dao = new JdbcObjectExposureReadDao(providerOf(jdbcTemplate), new SQLQueryLoaderUtil(new DefaultResourceLoader()));

        ObjectExposureRecord result = dao.findObject("meta", "GL_BALANCE").orElseThrow();

        assertTrue(jdbcTemplate.recordedSql.contains("schema_cd = :schema_cd"));
        assertEquals("meta", jdbcTemplate.recordedParameters.get("schema_cd"));
        assertEquals("GL_BALANCE", jdbcTemplate.recordedParameters.get("object_cd"));
        assertEquals(objectId, result.object_id());
        assertEquals("GL Balance", result.effective_object_nm());
    }

    @Test
    void bindsAttributeParametersAndMapsEffectiveAttributeColumns() {
        UUID objectId = UUID.fromString("00000000-0000-0000-0000-000000000101");
        UUID attributeId = UUID.fromString("00000000-0000-0000-0000-000000000102");
        RecordingNamedParameterJdbcTemplate jdbcTemplate = new RecordingNamedParameterJdbcTemplate(List.of(attributeRow(attributeId, objectId)));
        JdbcObjectExposureReadDao dao = new JdbcObjectExposureReadDao(providerOf(jdbcTemplate), new SQLQueryLoaderUtil(new DefaultResourceLoader()));

        List<AttributeExposureRecord> results = dao.findAttributes("client-a", objectId);

        assertTrue(jdbcTemplate.recordedSql.contains("FROM meta.attribute_catalog"));
        assertTrue(jdbcTemplate.recordedSql.contains("a.pk_flg"));
        assertTrue(jdbcTemplate.recordedSql.contains("a.fk_flg"));
        assertTrue(jdbcTemplate.recordedSql.contains("a.nullable_flg"));
        assertEquals("client-a", jdbcTemplate.recordedParameters.get("client_id"));
        assertEquals(objectId, jdbcTemplate.recordedParameters.get("object_id"));
        assertEquals(1, results.size());
        assertEquals(attributeId, results.get(0).attribute_id());
        assertEquals("Amount", results.get(0).effective_attribute_nm());
        assertEquals("MDRM12345678", results.get(0).taxonomy_cd());
    }

    @Test
    void failsWhenNamedParameterJdbcTemplateMissing() {
        JdbcObjectExposureReadDao dao = new JdbcObjectExposureReadDao(providerOf(null), new SQLQueryLoaderUtil(new DefaultResourceLoader()));

        assertThrows(SemanticLayerException.class, () -> dao.findObjects("client-a", null, null));
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

    private static Map<String, Object> objectRow(UUID objectId, UUID connectionId) {
        Map<String, Object> row = new HashMap<>();
        row.put("object_id", objectId);
        row.put("client_id", "client-a");
        row.put("object_cd", "GL_BALANCE");
        row.put("object_nm", "GL Balance");
        row.put("effective_object_nm", "GL Balance");
        row.put("object_type_cd", "TABLE");
        row.put("schema_cd", "meta");
        row.put("connection_id", connectionId);
        row.put("lifecycle_status_cd", "DRAFT");
        row.put("created_ts", OffsetDateTime.parse("2026-06-16T10:15:30+05:30"));
        row.put("created_by", "producer");
        row.put("updated_ts", OffsetDateTime.parse("2026-06-17T10:15:30+05:30"));
        row.put("updated_by", "producer");
        return row;
    }

    private static Map<String, Object> attributeRow(UUID attributeId, UUID objectId) {
        Map<String, Object> row = new HashMap<>();
        row.put("attribute_id", attributeId);
        row.put("object_id", objectId);
        row.put("client_id", "client-a");
        row.put("attribute_cd", "AMOUNT");
        row.put("attribute_nm", "Amount");
        row.put("effective_attribute_nm", "Amount");
        row.put("data_type_cd", "DECIMAL");
        row.put("taxonomy_cd", "MDRM12345678");
        row.put("taxonomy_source_cd", "MDRM");
        row.put("taxonomy_jurisdiction_cd", "US");
        row.put("created_ts", OffsetDateTime.parse("2026-06-16T10:15:30+05:30"));
        row.put("created_by", "producer");
        row.put("updated_ts", OffsetDateTime.parse("2026-06-17T10:15:30+05:30"));
        row.put("updated_by", "producer");
        return row;
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
