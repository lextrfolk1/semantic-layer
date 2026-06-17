package com.lextr.semanticlayer.dao.impl;

import com.lextr.semanticlayer.exception.SemanticLayerException;
import com.lextr.semanticlayer.model.AttributeCatalogRecord;
import com.lextr.semanticlayer.model.AttributeCatalogWriteRequest;
import com.lextr.semanticlayer.model.MetadataChangeHistoryRecord;
import com.lextr.semanticlayer.model.MetadataChangeHistoryWriteRequest;
import com.lextr.semanticlayer.model.ObjectCatalogRecord;
import com.lextr.semanticlayer.model.ObjectCatalogWriteRequest;
import com.lextr.semanticlayer.model.WorkflowTaskRecord;
import com.lextr.semanticlayer.model.WorkflowTaskWriteRequest;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JdbcObjectRegistrationWriteDaoTest {

    @Test
    void bindsObjectInsertParametersAndMapsReturnedColumns() {
        UUID objectId = UUID.fromString("00000000-0000-0000-0000-000000000101");
        UUID connectionId = UUID.fromString("00000000-0000-0000-0000-000000000201");
        RecordingNamedParameterJdbcTemplate jdbcTemplate = new RecordingNamedParameterJdbcTemplate(List.of(objectRow(objectId, connectionId)));
        JdbcObjectRegistrationWriteDao dao = new JdbcObjectRegistrationWriteDao(providerOf(jdbcTemplate), new SQLQueryLoaderUtil(new DefaultResourceLoader()));
        ObjectCatalogWriteRequest request = new ObjectCatalogWriteRequest(
                objectId,
                "client-a",
                "GL_BALANCE",
                "GL Balance",
                "TABLE",
                "meta",
                connectionId,
                OffsetDateTime.parse("2026-06-17T10:15:30+05:30"),
                "producer",
                OffsetDateTime.parse("2026-06-17T10:15:30+05:30"),
                "producer"
        );

        ObjectCatalogRecord result = dao.insertDraftObject(request);

        assertTrue(jdbcTemplate.recordedSql.contains("INSERT INTO meta.object_catalog"));
        assertTrue(jdbcTemplate.recordedSql.contains("RETURNING object_id, client_id, object_cd"));
        assertTrue(jdbcTemplate.recordedSql.contains("'DRAFT'"));
        assertEquals("client-a", jdbcTemplate.recordedParameters.get("client_id"));
        assertEquals("GL_BALANCE", jdbcTemplate.recordedParameters.get("object_cd"));
        assertEquals(objectId, result.object_id());
        assertEquals("DRAFT", result.lifecycle_status_cd());
        assertEquals("GL Balance", result.object_nm());
    }

    @Test
    void bindsAttributeInsertParametersAndMapsTaxonomyColumns() {
        UUID attributeId = UUID.fromString("00000000-0000-0000-0000-000000000102");
        UUID objectId = UUID.fromString("00000000-0000-0000-0000-000000000101");
        RecordingNamedParameterJdbcTemplate jdbcTemplate = new RecordingNamedParameterJdbcTemplate(List.of(attributeRow(attributeId, objectId)));
        JdbcObjectRegistrationWriteDao dao = new JdbcObjectRegistrationWriteDao(providerOf(jdbcTemplate), new SQLQueryLoaderUtil(new DefaultResourceLoader()));
        AttributeCatalogWriteRequest request = new AttributeCatalogWriteRequest(
                attributeId,
                objectId,
                "client-a",
                "AMOUNT",
                "Amount",
                "DECIMAL",
                "MDRM12345678",
                "MDRM",
                "US",
                OffsetDateTime.parse("2026-06-17T10:15:30+05:30"),
                "producer",
                OffsetDateTime.parse("2026-06-17T10:15:30+05:30"),
                "producer"
        );

        AttributeCatalogRecord result = dao.insertAttribute(request);

        assertTrue(jdbcTemplate.recordedSql.contains("INSERT INTO meta.attribute_catalog"));
        assertTrue(jdbcTemplate.recordedSql.contains("taxonomy_jurisdiction_cd"));
        assertEquals("MDRM12345678", jdbcTemplate.recordedParameters.get("taxonomy_cd"));
        assertEquals("MDRM", jdbcTemplate.recordedParameters.get("taxonomy_source_cd"));
        assertEquals("US", result.taxonomy_jurisdiction_cd());
        assertEquals("AMOUNT", result.attribute_cd());
    }

    @Test
    void bindsWorkflowTaskParametersAndMapsReturnedColumns() {
        UUID workflowTaskId = UUID.fromString("00000000-0000-0000-0000-000000000301");
        UUID entityId = UUID.fromString("00000000-0000-0000-0000-000000000101");
        RecordingNamedParameterJdbcTemplate jdbcTemplate = new RecordingNamedParameterJdbcTemplate(List.of(workflowTaskRow(workflowTaskId, entityId)));
        JdbcObjectRegistrationWriteDao dao = new JdbcObjectRegistrationWriteDao(providerOf(jdbcTemplate), new SQLQueryLoaderUtil(new DefaultResourceLoader()));
        WorkflowTaskWriteRequest request = new WorkflowTaskWriteRequest(
                workflowTaskId,
                "client-a",
                "OBJECT_REGISTRATION",
                "OBJECT",
                entityId,
                "PENDING_APPROVAL",
                OffsetDateTime.parse("2026-06-17T10:15:30+05:30"),
                "producer",
                OffsetDateTime.parse("2026-06-17T10:15:30+05:30"),
                "producer"
        );

        WorkflowTaskRecord result = dao.insertWorkflowTask(request);

        assertTrue(jdbcTemplate.recordedSql.contains("INSERT INTO wkfl.workflow_task"));
        assertEquals("OBJECT_REGISTRATION", jdbcTemplate.recordedParameters.get("workflow_type_cd"));
        assertEquals("PENDING_APPROVAL", result.task_status_cd());
        assertEquals(entityId, result.entity_id());
    }

    @Test
    void bindsMetadataChangeHistoryParametersAndMapsReturnedColumns() {
        UUID changeHistoryId = UUID.fromString("00000000-0000-0000-0000-000000000401");
        UUID entityId = UUID.fromString("00000000-0000-0000-0000-000000000101");
        RecordingNamedParameterJdbcTemplate jdbcTemplate = new RecordingNamedParameterJdbcTemplate(List.of(metadataChangeRow(changeHistoryId, entityId)));
        JdbcObjectRegistrationWriteDao dao = new JdbcObjectRegistrationWriteDao(providerOf(jdbcTemplate), new SQLQueryLoaderUtil(new DefaultResourceLoader()));
        MetadataChangeHistoryWriteRequest request = new MetadataChangeHistoryWriteRequest(
                changeHistoryId,
                "client-a",
                "OBJECT",
                entityId,
                "REGISTERED",
                "Registered draft object",
                OffsetDateTime.parse("2026-06-17T10:15:30+05:30"),
                "producer"
        );

        MetadataChangeHistoryRecord result = dao.insertMetadataChangeHistory(request);

        assertTrue(jdbcTemplate.recordedSql.contains("INSERT INTO meta.metadata_change_history"));
        assertEquals("REGISTERED", jdbcTemplate.recordedParameters.get("change_type_cd"));
        assertEquals("Registered draft object", result.change_summary_txt());
        assertEquals(changeHistoryId, result.change_history_id());
    }

    @Test
    void failsWhenNamedParameterJdbcTemplateMissing() {
        JdbcObjectRegistrationWriteDao dao = new JdbcObjectRegistrationWriteDao(providerOf(null), new SQLQueryLoaderUtil(new DefaultResourceLoader()));
        ObjectCatalogWriteRequest request = new ObjectCatalogWriteRequest(
                UUID.fromString("00000000-0000-0000-0000-000000000101"),
                "client-a",
                "GL_BALANCE",
                "GL Balance",
                "TABLE",
                "meta",
                UUID.fromString("00000000-0000-0000-0000-000000000201"),
                OffsetDateTime.parse("2026-06-17T10:15:30+05:30"),
                "producer",
                OffsetDateTime.parse("2026-06-17T10:15:30+05:30"),
                "producer"
        );

        assertThrows(SemanticLayerException.class, () -> dao.insertDraftObject(request));
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
        row.put("object_type_cd", "TABLE");
        row.put("schema_cd", "meta");
        row.put("connection_id", connectionId);
        row.put("lifecycle_status_cd", "DRAFT");
        row.put("created_ts", OffsetDateTime.parse("2026-06-17T10:15:30+05:30"));
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
        row.put("data_type_cd", "DECIMAL");
        row.put("taxonomy_cd", "MDRM12345678");
        row.put("taxonomy_source_cd", "MDRM");
        row.put("taxonomy_jurisdiction_cd", "US");
        row.put("created_ts", OffsetDateTime.parse("2026-06-17T10:15:30+05:30"));
        row.put("created_by", "producer");
        row.put("updated_ts", OffsetDateTime.parse("2026-06-17T10:15:30+05:30"));
        row.put("updated_by", "producer");
        return row;
    }

    private static Map<String, Object> workflowTaskRow(UUID workflowTaskId, UUID entityId) {
        Map<String, Object> row = new HashMap<>();
        row.put("workflow_task_id", workflowTaskId);
        row.put("client_id", "client-a");
        row.put("workflow_type_cd", "OBJECT_REGISTRATION");
        row.put("entity_type_cd", "OBJECT");
        row.put("entity_id", entityId);
        row.put("task_status_cd", "PENDING_APPROVAL");
        row.put("created_ts", OffsetDateTime.parse("2026-06-17T10:15:30+05:30"));
        row.put("created_by", "producer");
        row.put("updated_ts", OffsetDateTime.parse("2026-06-17T10:15:30+05:30"));
        row.put("updated_by", "producer");
        return row;
    }

    private static Map<String, Object> metadataChangeRow(UUID changeHistoryId, UUID entityId) {
        Map<String, Object> row = new HashMap<>();
        row.put("change_history_id", changeHistoryId);
        row.put("client_id", "client-a");
        row.put("entity_type_cd", "OBJECT");
        row.put("entity_id", entityId);
        row.put("change_type_cd", "REGISTERED");
        row.put("change_summary_txt", "Registered draft object");
        row.put("created_ts", OffsetDateTime.parse("2026-06-17T10:15:30+05:30"));
        row.put("created_by", "producer");
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
