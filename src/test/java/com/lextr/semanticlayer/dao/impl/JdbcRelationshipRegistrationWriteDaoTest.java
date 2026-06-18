package com.lextr.semanticlayer.dao.impl;

import com.lextr.semanticlayer.exception.SemanticLayerException;
import com.lextr.semanticlayer.model.SemanticRelationshipCatalogRecord;
import com.lextr.semanticlayer.model.SemanticRelationshipCatalogWriteRequest;
import com.lextr.semanticlayer.model.SemanticRelationshipProjectionSyncWriteRequest;
import com.lextr.semanticlayer.util.SQLQueryLoaderUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.jdbc.datasource.AbstractDataSource;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JdbcRelationshipRegistrationWriteDaoTest {

    @Test
    void bindsRelationshipInsertParametersAndMapsReturnedColumns() {
        RecordingNamedParameterJdbcTemplate jdbcTemplate = new RecordingNamedParameterJdbcTemplate(List.of(relationshipRow()));
        JdbcRelationshipRegistrationWriteDao dao = new JdbcRelationshipRegistrationWriteDao(providerOf(jdbcTemplate), new SQLQueryLoaderUtil(new DefaultResourceLoader()));
        SemanticRelationshipCatalogWriteRequest request = new SemanticRelationshipCatalogWriteRequest(
                "GL_TO_LEDGER",
                "meta",
                "gl_balance",
                "ledger_id",
                "meta",
                "ledger",
                "ledger_id",
                "FOREIGN_KEY",
                "MANY_TO_ONE",
                "INNER",
                true,
                false,
                false,
                "GL balances map to ledger master rows",
                "Join on ledger identifier",
                "ACTIVE",
                OffsetDateTime.parse("2026-06-17T10:15:30+05:30"),
                "producer",
                OffsetDateTime.parse("2026-06-17T10:15:30+05:30"),
                "producer"
        );

        SemanticRelationshipCatalogRecord result = dao.insertRelationship(request);

        assertTrue(jdbcTemplate.recordedSql.contains("INSERT INTO meta.semantic_relationship_catalog"));
        assertTrue(jdbcTemplate.recordedSql.contains("RETURNING id, relationship_cd"));
        assertEquals("GL_TO_LEDGER", jdbcTemplate.recordedParameters.get("relationship_cd"));
        assertEquals("meta", jdbcTemplate.recordedParameters.get("parent_schema_cd"));
        assertEquals("ledger", jdbcTemplate.recordedParameters.get("child_object_cd"));
        assertEquals("MANY_TO_ONE", jdbcTemplate.recordedParameters.get("cardinality_cd"));
        assertEquals(Boolean.TRUE, jdbcTemplate.recordedParameters.get("is_enforced_flg"));
        assertEquals(101L, result.id());
        assertEquals("GL_TO_LEDGER", result.relationship_cd());
        assertEquals("FOREIGN_KEY", result.relationship_type_cd());
        assertEquals("ACTIVE", result.lifecycle_status_cd());
    }

    @Test
    void failsWhenNamedParameterJdbcTemplateMissing() {
        JdbcRelationshipRegistrationWriteDao dao = new JdbcRelationshipRegistrationWriteDao(providerOf(null), new SQLQueryLoaderUtil(new DefaultResourceLoader()));
        SemanticRelationshipCatalogWriteRequest request = new SemanticRelationshipCatalogWriteRequest(
                "GL_TO_LEDGER",
                "meta",
                "gl_balance",
                "ledger_id",
                "meta",
                "ledger",
                "ledger_id",
                "FOREIGN_KEY",
                "MANY_TO_ONE",
                "INNER",
                true,
                false,
                false,
                "GL balances map to ledger master rows",
                "Join on ledger identifier",
                "ACTIVE",
                OffsetDateTime.parse("2026-06-17T10:15:30+05:30"),
                "producer",
                OffsetDateTime.parse("2026-06-17T10:15:30+05:30"),
                "producer"
        );

        assertThrows(SemanticLayerException.class, () -> dao.insertRelationship(request));
    }

    @Test
    void bindsProjectionSyncUpdateParametersAndMapsReturnedColumns() {
        RecordingNamedParameterJdbcTemplate jdbcTemplate = new RecordingNamedParameterJdbcTemplate(List.of(projectedRelationshipRow()));
        JdbcRelationshipRegistrationWriteDao dao = new JdbcRelationshipRegistrationWriteDao(providerOf(jdbcTemplate), new SQLQueryLoaderUtil(new DefaultResourceLoader()));
        SemanticRelationshipProjectionSyncWriteRequest request = new SemanticRelationshipProjectionSyncWriteRequest(
                "GL_TO_LEDGER",
                OffsetDateTime.parse("2026-06-17T11:30:45+05:30"),
                OffsetDateTime.parse("2026-06-17T11:30:45+05:30"),
                "neo4j-projector"
        );

        SemanticRelationshipCatalogRecord result = dao.updateNeo4jProjectionSync(request);

        assertTrue(jdbcTemplate.recordedSql.contains("UPDATE meta.semantic_relationship_catalog"));
        assertTrue(jdbcTemplate.recordedSql.contains("neo4j_synced_ts = :neo4j_synced_ts"));
        assertEquals("GL_TO_LEDGER", jdbcTemplate.recordedParameters.get("relationship_cd"));
        assertEquals("neo4j-projector", jdbcTemplate.recordedParameters.get("updated_by"));
        assertEquals(OffsetDateTime.parse("2026-06-17T11:30:45+05:30"), result.neo4j_synced_ts());
        assertEquals("neo4j-projector", result.updated_by());
    }

    @Test
    void transactionRollsBackInsertAndProjectionUpdateOnFailure() {
        TransactionHarness harness = new TransactionHarness();
        TransactionalNamedParameterJdbcTemplate jdbcTemplate = new TransactionalNamedParameterJdbcTemplate(harness);
        JdbcRelationshipRegistrationWriteDao dao = new JdbcRelationshipRegistrationWriteDao(providerOf(jdbcTemplate), new SQLQueryLoaderUtil(new DefaultResourceLoader()));
        RecordingTransactionOperations transactionOperations = new RecordingTransactionOperations(harness);

        assertThrows(IllegalStateException.class, () -> transactionOperations.execute(status -> {
            dao.insertRelationship(insertRequest());
            dao.updateNeo4jProjectionSync(new SemanticRelationshipProjectionSyncWriteRequest(
                    "GL_TO_LEDGER",
                    OffsetDateTime.parse("2026-06-17T11:30:45+05:30"),
                    OffsetDateTime.parse("2026-06-17T11:30:45+05:30"),
                    "neo4j-projector"
            ));
            throw new IllegalStateException("projection failed");
        }));

        assertTrue(harness.rolledBack);
        assertFalse(harness.committed);
        assertTrue(jdbcTemplate.committedRows.isEmpty());
        assertEquals(2, jdbcTemplate.recordedSqls.size());
    }

    @Test
    void usesNamedParameterJdbcTemplateAndDoesNotUseJpa() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/lextr/semanticlayer/dao/impl/JdbcRelationshipRegistrationWriteDao.java"
        ));

        assertTrue(source.contains("NamedParameterJdbcTemplate"));
        assertTrue(!source.contains("EntityManager"));
        assertTrue(!source.contains("JpaRepository"));
        assertTrue(!source.contains("jakarta.persistence"));
        assertTrue(!source.contains("javax.persistence"));
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

    private static Map<String, Object> relationshipRow() {
        Map<String, Object> row = new HashMap<>();
        row.put("id", 101L);
        row.put("relationship_cd", "GL_TO_LEDGER");
        row.put("parent_schema_cd", "meta");
        row.put("parent_object_cd", "gl_balance");
        row.put("parent_attribute_cd", "ledger_id");
        row.put("child_schema_cd", "meta");
        row.put("child_object_cd", "ledger");
        row.put("child_attribute_cd", "ledger_id");
        row.put("relationship_type_cd", "FOREIGN_KEY");
        row.put("cardinality_cd", "MANY_TO_ONE");
        row.put("join_type_cd", "INNER");
        row.put("is_enforced_flg", true);
        row.put("is_nullable_flg", false);
        row.put("is_cross_engine_flg", false);
        row.put("relationship_desc", "GL balances map to ledger master rows");
        row.put("ai_join_guidance_txt", "Join on ledger identifier");
        row.put("neo4j_synced_ts", null);
        row.put("lifecycle_status_cd", "ACTIVE");
        row.put("created_ts", OffsetDateTime.parse("2026-06-17T10:15:30+05:30"));
        row.put("created_by", "producer");
        row.put("updated_ts", OffsetDateTime.parse("2026-06-17T10:15:30+05:30"));
        row.put("updated_by", "producer");
        return row;
    }

    private static SemanticRelationshipCatalogWriteRequest insertRequest() {
        return new SemanticRelationshipCatalogWriteRequest(
                "GL_TO_LEDGER",
                "meta",
                "gl_balance",
                "ledger_id",
                "meta",
                "ledger",
                "ledger_id",
                "FOREIGN_KEY",
                "MANY_TO_ONE",
                "INNER",
                true,
                false,
                false,
                "GL balances map to ledger master rows",
                "Join on ledger identifier",
                "ACTIVE",
                OffsetDateTime.parse("2026-06-17T10:15:30+05:30"),
                "producer",
                OffsetDateTime.parse("2026-06-17T10:15:30+05:30"),
                "producer"
        );
    }

    private static Map<String, Object> projectedRelationshipRow() {
        Map<String, Object> row = new HashMap<>(relationshipRow());
        row.put("neo4j_synced_ts", OffsetDateTime.parse("2026-06-17T11:30:45+05:30"));
        row.put("updated_ts", OffsetDateTime.parse("2026-06-17T11:30:45+05:30"));
        row.put("updated_by", "neo4j-projector");
        return row;
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
            this.recordedSql = sql;
            if (paramSource instanceof MapSqlParameterSource source) {
                this.recordedParameters = source.getValues();
            }
            return rows.stream().map(row -> mapRow(rowMapper, row)).toList();
        }
    }

    private static final class TransactionalNamedParameterJdbcTemplate extends NamedParameterJdbcTemplate {

        private final TransactionHarness harness;
        private final Map<String, Map<String, Object>> committedRows = new LinkedHashMap<>();
        private final List<String> recordedSqls = new ArrayList<>();

        private TransactionalNamedParameterJdbcTemplate(TransactionHarness harness) {
            super(noOpDataSource());
            this.harness = harness;
        }

        @Override
        public <T> List<T> query(String sql, SqlParameterSource paramSource, RowMapper<T> rowMapper) {
            recordedSqls.add(sql);
            Map<String, Object> parameters = paramSource instanceof MapSqlParameterSource source ? source.getValues() : Map.of();
            Map<String, Map<String, Object>> rows = harness.transactionRows(committedRows);
            Map<String, Object> row;
            if (sql.startsWith("INSERT INTO meta.semantic_relationship_catalog")) {
                row = insertedRow(parameters);
                rows.put((String) parameters.get("relationship_cd"), row);
            } else if (sql.startsWith("UPDATE meta.semantic_relationship_catalog")) {
                row = rows.get(parameters.get("relationship_cd"));
                if (row == null) {
                    return List.of();
                }
                row = new HashMap<>(row);
                row.put("neo4j_synced_ts", parameters.get("neo4j_synced_ts"));
                row.put("updated_ts", parameters.get("updated_ts"));
                row.put("updated_by", parameters.get("updated_by"));
                rows.put((String) parameters.get("relationship_cd"), row);
            } else {
                throw new IllegalArgumentException("Unexpected SQL: " + sql);
            }
            return List.of(mapRow(rowMapper, row));
        }

        private Map<String, Object> insertedRow(Map<String, Object> parameters) {
            Map<String, Object> row = new HashMap<>();
            row.put("id", 101L);
            row.put("relationship_cd", parameters.get("relationship_cd"));
            row.put("parent_schema_cd", parameters.get("parent_schema_cd"));
            row.put("parent_object_cd", parameters.get("parent_object_cd"));
            row.put("parent_attribute_cd", parameters.get("parent_attribute_cd"));
            row.put("child_schema_cd", parameters.get("child_schema_cd"));
            row.put("child_object_cd", parameters.get("child_object_cd"));
            row.put("child_attribute_cd", parameters.get("child_attribute_cd"));
            row.put("relationship_type_cd", parameters.get("relationship_type_cd"));
            row.put("cardinality_cd", parameters.get("cardinality_cd"));
            row.put("join_type_cd", parameters.get("join_type_cd"));
            row.put("is_enforced_flg", parameters.get("is_enforced_flg"));
            row.put("is_nullable_flg", parameters.get("is_nullable_flg"));
            row.put("is_cross_engine_flg", parameters.get("is_cross_engine_flg"));
            row.put("relationship_desc", parameters.get("relationship_desc"));
            row.put("ai_join_guidance_txt", parameters.get("ai_join_guidance_txt"));
            row.put("neo4j_synced_ts", null);
            row.put("lifecycle_status_cd", parameters.get("lifecycle_status_cd"));
            row.put("created_ts", parameters.get("created_ts"));
            row.put("created_by", parameters.get("created_by"));
            row.put("updated_ts", parameters.get("updated_ts"));
            row.put("updated_by", parameters.get("updated_by"));
            return row;
        }

        private <T> T mapRow(RowMapper<T> rowMapper, Map<String, Object> row) {
            try {
                return rowMapper.mapRow(resultSet(row), 0);
            } catch (SQLException exception) {
                throw new RuntimeException(exception);
            }
        }
    }

    private static final class TransactionHarness {

        private Map<String, Map<String, Object>> workingRows;
        private boolean committed;
        private boolean rolledBack;

        private Map<String, Map<String, Object>> transactionRows(Map<String, Map<String, Object>> committedRows) {
            if (workingRows == null) {
                workingRows = new LinkedHashMap<>(committedRows);
            }
            return workingRows;
        }
    }

    private static final class RecordingTransactionOperations implements TransactionOperations {

        private final TransactionHarness harness;

        private RecordingTransactionOperations(TransactionHarness harness) {
            this.harness = harness;
        }

        @Override
        public <T> T execute(TransactionCallback<T> action) {
            try {
                T result = action.doInTransaction(new NoOpTransactionStatus());
                harness.workingRows = null;
                harness.committed = true;
                return result;
            } catch (RuntimeException exception) {
                harness.workingRows = null;
                harness.rolledBack = true;
                throw exception;
            }
        }
    }

    private static final class NoOpTransactionStatus implements TransactionStatus {

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
