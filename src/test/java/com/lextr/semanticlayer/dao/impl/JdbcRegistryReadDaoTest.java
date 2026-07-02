package com.lextr.semanticlayer.dao.impl;

import com.lextr.semanticlayer.exception.SemanticLayerException;
import com.lextr.semanticlayer.model.DataConnectionRecord;
import com.lextr.semanticlayer.model.SchemaCatalogRecord;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JdbcRegistryReadDaoTest {

    @Test
    void bindsSchemaParametersAndMapsSnakeCaseColumns() {
        RecordingNamedParameterJdbcTemplate jdbcTemplate = new RecordingNamedParameterJdbcTemplate(List.of(Map.of(
                "schema_cd", "meta",
                "schema_nm", "Metadata",
                "effective_schema_nm", "Metadata",
                "schema_purpose_txt", "Semantic system of record",
                "lifecycle_status_cd", "ACTIVE",
                "created_ts", OffsetDateTime.parse("2026-06-16T10:15:30+05:30"),
                "created_by", "flyway",
                "updated_ts", OffsetDateTime.parse("2026-06-17T10:15:30+05:30"),
                "updated_by", "platform"
        )));
        JdbcRegistryReadDao dao = new JdbcRegistryReadDao(providerOf(jdbcTemplate), new SQLQueryLoaderUtil(new DefaultResourceLoader()));

        List<SchemaCatalogRecord> results = dao.findSchemas("client-a", "ACTIVE");

        assertTrue(jdbcTemplate.recordedSql.contains("client_id = :client_id"));
        assertEquals("client-a", jdbcTemplate.recordedParameters.get("client_id"));
        assertEquals("ACTIVE", jdbcTemplate.recordedParameters.get("lifecycle_status_cd"));
        assertEquals(1, results.size());
        assertEquals("meta", results.get(0).schema_cd());
        assertEquals("Metadata", results.get(0).effective_schema_nm());
        assertEquals("ACTIVE", results.get(0).lifecycle_status_cd());
    }

    @Test
    void findsSingleSchemaByCodeWithinClientScope() {
        RecordingNamedParameterJdbcTemplate jdbcTemplate = new RecordingNamedParameterJdbcTemplate(List.of(Map.of(
                "schema_cd", "meta",
                "schema_nm", "Metadata",
                "effective_schema_nm", "Metadata Override",
                "schema_purpose_txt", "Semantic system of record",
                "lifecycle_status_cd", "ACTIVE",
                "created_ts", OffsetDateTime.parse("2026-06-16T10:15:30+05:30"),
                "created_by", "flyway",
                "updated_ts", OffsetDateTime.parse("2026-06-17T10:15:30+05:30"),
                "updated_by", "platform"
        )));
        JdbcRegistryReadDao dao = new JdbcRegistryReadDao(providerOf(jdbcTemplate), new SQLQueryLoaderUtil(new DefaultResourceLoader()));

        SchemaCatalogRecord result = dao.findSchema("client-a", "meta").orElseThrow();

        assertTrue(jdbcTemplate.recordedSql.contains("client_id = :client_id"));
        assertTrue(jdbcTemplate.recordedSql.contains("schema_cd = :schema_cd"));
        assertEquals("client-a", jdbcTemplate.recordedParameters.get("client_id"));
        assertEquals("meta", jdbcTemplate.recordedParameters.get("schema_cd"));
        assertEquals("Metadata", result.schema_nm());
        assertEquals("Metadata Override", result.effective_schema_nm());
    }

    @Test
    void bindsConnectionParametersAndWithholdsSecretsRef() {
        UUID connectionId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        RecordingNamedParameterJdbcTemplate jdbcTemplate = new RecordingNamedParameterJdbcTemplate(List.of(connectionRow(connectionId)));
        JdbcRegistryReadDao dao = new JdbcRegistryReadDao(providerOf(jdbcTemplate), new SQLQueryLoaderUtil(new DefaultResourceLoader()));

        List<DataConnectionRecord> results = dao.findConnections("client-a", "POSTGRES", true);

        assertTrue(jdbcTemplate.recordedSql.contains("client_id = :client_id"));
        assertTrue(jdbcTemplate.recordedSql.contains("effective_connection_nm"));
        assertFalse(jdbcTemplate.recordedSql.contains("secrets_ref"));
        assertEquals("client-a", jdbcTemplate.recordedParameters.get("client_id"));
        assertEquals("POSTGRES", jdbcTemplate.recordedParameters.get("engine_cd"));
        assertEquals(Boolean.TRUE, jdbcTemplate.recordedParameters.get("is_active_flg"));
        assertEquals(1, results.size());
        assertEquals(connectionId, results.get(0).connection_id());
        assertEquals("Lextr PostgreSQL Override", results.get(0).effective_connection_nm());
        assertEquals("meta", results.get(0).schema_nm_default());
    }

    @Test
    void findsSingleConnectionByIdWithinClientScope() {
        UUID connectionId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        RecordingNamedParameterJdbcTemplate jdbcTemplate = new RecordingNamedParameterJdbcTemplate(List.of(connectionRow(connectionId)));
        JdbcRegistryReadDao dao = new JdbcRegistryReadDao(providerOf(jdbcTemplate), new SQLQueryLoaderUtil(new DefaultResourceLoader()));

        DataConnectionRecord result = dao.findConnection("client-a", connectionId).orElseThrow();

        assertTrue(jdbcTemplate.recordedSql.contains("client_id = :client_id"));
        assertTrue(jdbcTemplate.recordedSql.contains("connection_id = :connection_id"));
        assertEquals("client-a", jdbcTemplate.recordedParameters.get("client_id"));
        assertEquals(connectionId, jdbcTemplate.recordedParameters.get("connection_id"));
        assertEquals("LEXTR_PG", result.connection_cd());
        assertEquals("Lextr PostgreSQL Override", result.effective_connection_nm());
    }

    @Test
    void failsWhenNamedParameterJdbcTemplateMissing() {
        JdbcRegistryReadDao dao = new JdbcRegistryReadDao(providerOf(null), new SQLQueryLoaderUtil(new DefaultResourceLoader()));

        assertThrows(SemanticLayerException.class, () -> dao.findSchemas("client-a", null));
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

    private static Map<String, Object> connectionRow(UUID connectionId) {
        Map<String, Object> row = new HashMap<>();
        row.put("connection_id", connectionId);
        row.put("connection_cd", "LEXTR_PG");
        row.put("connection_nm", "Lextr PostgreSQL");
        row.put("effective_connection_nm", "Lextr PostgreSQL Override");
        row.put("engine_cd", "POSTGRES");
        row.put("connection_type_cd", "PRIMARY");
        row.put("source_mode_cd", "METADATA_PLUS_EXECUTION");
        row.put("host_nm", "localhost");
        row.put("port_nbr", 5432);
        row.put("database_nm", "lextr");
        row.put("schema_nm_default", "meta");
        row.put("is_default_flg", true);
        row.put("is_active_flg", true);
        row.put("created_ts", OffsetDateTime.parse("2026-06-16T10:15:30+05:30"));
        row.put("created_by", "flyway");
        row.put("updated_ts", null);
        row.put("updated_by", null);
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
