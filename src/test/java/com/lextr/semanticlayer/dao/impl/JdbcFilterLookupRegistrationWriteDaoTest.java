package com.lextr.semanticlayer.dao.impl;

import com.lextr.semanticlayer.exception.SemanticLayerException;
import com.lextr.semanticlayer.model.FilterLookupBindingRecord;
import com.lextr.semanticlayer.model.FilterLookupBindingWriteRequest;
import com.lextr.semanticlayer.model.FilterLookupCertificationWriteRequest;
import com.lextr.semanticlayer.model.FilterLookupMetadataChangeHistoryRecord;
import com.lextr.semanticlayer.model.FilterLookupMetadataChangeHistoryWriteRequest;
import com.lextr.semanticlayer.model.FilterLookupWorkflowTaskRecord;
import com.lextr.semanticlayer.model.FilterLookupWorkflowTaskWriteRequest;
import com.lextr.semanticlayer.model.SemanticFilterLookupRecord;
import com.lextr.semanticlayer.model.SemanticFilterLookupWriteRequest;
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
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
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

class JdbcFilterLookupRegistrationWriteDaoTest {

    @Test
    void bindsLookupInsertParametersAndMapsReturnedColumns() {
        RecordingNamedParameterJdbcTemplate jdbcTemplate = new RecordingNamedParameterJdbcTemplate(List.of(lookupRow()));
        JdbcFilterLookupRegistrationWriteDao dao = new JdbcFilterLookupRegistrationWriteDao(
                providerOf(jdbcTemplate),
                new SQLQueryLoaderUtil(new DefaultResourceLoader())
        );

        SemanticFilterLookupRecord result = dao.insertLookup(lookupRequest());

        assertTrue(jdbcTemplate.recordedSql.contains("INSERT INTO meta.semantic_filter_lookup"));
        assertEquals("LEDGER_SCOPE", jdbcTemplate.recordedParameters.get("lookup_cd"));
        assertEquals(Integer.valueOf(120), jdbcTemplate.recordedParameters.get("review_period_days_override"));
        assertEquals("REVIEW", jdbcTemplate.recordedParameters.get("governance_status_cd"));
        assertEquals(101L, result.id());
        assertEquals("LEDGER_SCOPE", result.lookup_cd());
        assertEquals("REVIEW", result.governance_status_cd());
        assertEquals(LocalDate.parse("2026-09-16"), result.next_review_due_dt());
    }

    @Test
    void bindsWorkflowTaskInsertParametersAndMapsReturnedColumns() {
        RecordingNamedParameterJdbcTemplate jdbcTemplate = new RecordingNamedParameterJdbcTemplate(List.of(workflowTaskRow()));
        JdbcFilterLookupRegistrationWriteDao dao = new JdbcFilterLookupRegistrationWriteDao(
                providerOf(jdbcTemplate),
                new SQLQueryLoaderUtil(new DefaultResourceLoader())
        );

        FilterLookupWorkflowTaskRecord result = dao.insertWorkflowTask(workflowTaskRequest());

        assertTrue(jdbcTemplate.recordedSql.contains("INSERT INTO wkfl.workflow_task"));
        assertEquals("FILTER_LOOKUP_REGISTRATION", jdbcTemplate.recordedParameters.get("task_type_cd"));
        assertEquals("LEDGER_SCOPE", jdbcTemplate.recordedParameters.get("entity_ref"));
        assertEquals(301L, result.id());
        assertEquals("PENDING", result.task_status_cd());
        assertEquals(LocalDate.parse("2026-09-16"), result.due_dt());
    }

    @Test
    void bindsMetadataChangeInsertParametersAndMapsReturnedColumns() {
        RecordingNamedParameterJdbcTemplate jdbcTemplate = new RecordingNamedParameterJdbcTemplate(List.of(metadataChangeRow()));
        JdbcFilterLookupRegistrationWriteDao dao = new JdbcFilterLookupRegistrationWriteDao(
                providerOf(jdbcTemplate),
                new SQLQueryLoaderUtil(new DefaultResourceLoader())
        );

        FilterLookupMetadataChangeHistoryRecord result = dao.insertMetadataChangeHistory(metadataChangeRequest());

        assertTrue(jdbcTemplate.recordedSql.contains("INSERT INTO meta.metadata_change_history"));
        assertEquals("FILTER_LOOKUP", jdbcTemplate.recordedParameters.get("entity_type_cd"));
        assertEquals("LEDGER_SCOPE", jdbcTemplate.recordedParameters.get("entity_ref"));
        assertEquals("REGISTERED", jdbcTemplate.recordedParameters.get("change_type_cd"));
        assertEquals(401L, result.id());
        assertEquals("Registered filter lookup LEDGER_SCOPE", result.change_reason_txt());
    }

    @Test
    void bindsCertificationUpdateParametersAndMapsReturnedColumns() {
        RecordingNamedParameterJdbcTemplate jdbcTemplate = new RecordingNamedParameterJdbcTemplate(List.of(certifiedLookupRow()));
        JdbcFilterLookupRegistrationWriteDao dao = new JdbcFilterLookupRegistrationWriteDao(
                providerOf(jdbcTemplate),
                new SQLQueryLoaderUtil(new DefaultResourceLoader())
        );

        SemanticFilterLookupRecord result = dao.certifyLookup(certificationRequest());

        assertTrue(jdbcTemplate.recordedSql.contains("UPDATE meta.semantic_filter_lookup"));
        assertTrue(jdbcTemplate.recordedSql.contains("WHERE client_id = :client_id AND lookup_cd = :lookup_cd"));
        assertEquals("client-a", jdbcTemplate.recordedParameters.get("client_id"));
        assertEquals("LEDGER_SCOPE", jdbcTemplate.recordedParameters.get("lookup_cd"));
        assertEquals("HEALTHY", jdbcTemplate.recordedParameters.get("health_status_cd"));
        assertEquals("certifier", jdbcTemplate.recordedParameters.get("last_certified_by"));
        assertEquals(LocalDate.parse("2026-09-16"), jdbcTemplate.recordedParameters.get("next_review_due_dt"));
        assertEquals("HEALTHY", result.health_status_cd());
        assertEquals("certifier", result.last_certified_by());
        assertEquals(LocalDate.parse("2026-09-16"), result.next_review_due_dt());
        assertEquals(OffsetDateTime.parse("2026-06-18T10:15:30Z"), result.last_certified_ts());
    }

    @Test
    void bindsBindingInsertParametersAndMapsReturnedColumns() {
        RecordingNamedParameterJdbcTemplate jdbcTemplate = new RecordingNamedParameterJdbcTemplate(List.of(bindingRow()));
        JdbcFilterLookupRegistrationWriteDao dao = new JdbcFilterLookupRegistrationWriteDao(
                providerOf(jdbcTemplate),
                new SQLQueryLoaderUtil(new DefaultResourceLoader())
        );

        FilterLookupBindingRecord result = dao.insertBinding(bindingRequest());

        assertTrue(jdbcTemplate.recordedSql.contains("INSERT INTO meta.filter_lookup_binding"));
        assertEquals("LEDGER_SCOPE", jdbcTemplate.recordedParameters.get("lookup_cd"));
        assertEquals("meta.gl_balance", jdbcTemplate.recordedParameters.get("bound_obj"));
        assertEquals("ledger_id", jdbcTemplate.recordedParameters.get("bound_attr_cd"));
        assertEquals("PIPELINE", jdbcTemplate.recordedParameters.get("binding_context_cd"));
        assertEquals("daily-pipeline", jdbcTemplate.recordedParameters.get("binding_ref"));
        assertEquals("binder", jdbcTemplate.recordedParameters.get("bound_by"));
        assertTrue((Boolean) jdbcTemplate.recordedParameters.get("is_active_flg"));

        assertEquals(501L, result.id());
        assertEquals("LEDGER_SCOPE", result.lookup_cd());
        assertEquals("meta.gl_balance", result.bound_obj());
        assertEquals("ledger_id", result.bound_attr_cd());
        assertEquals("PIPELINE", result.binding_context_cd());
        assertEquals("daily-pipeline", result.binding_ref());
        assertEquals("binder", result.bound_by());
        assertTrue(result.is_active_flg());
    }

    @Test
    void transactionRollsBackFilterLookupWritesOnFailure() {
        TransactionHarness harness = new TransactionHarness();
        TransactionalNamedParameterJdbcTemplate jdbcTemplate = new TransactionalNamedParameterJdbcTemplate(harness);
        JdbcFilterLookupRegistrationWriteDao dao = new JdbcFilterLookupRegistrationWriteDao(
                providerOf(jdbcTemplate),
                new SQLQueryLoaderUtil(new DefaultResourceLoader())
        );
        RecordingTransactionOperations transactionOperations = new RecordingTransactionOperations(harness);

        assertThrows(IllegalStateException.class, () -> transactionOperations.execute(status -> {
            dao.insertLookup(lookupRequest());
            dao.insertWorkflowTask(workflowTaskRequest());
            throw new IllegalStateException("workflow failed");
        }));

        assertTrue(harness.rolledBack);
        assertFalse(harness.committed);
        assertTrue(jdbcTemplate.committedLookups.isEmpty());
        assertTrue(jdbcTemplate.committedWorkflowTasks.isEmpty());
        assertEquals(2, jdbcTemplate.recordedSqls.size());
    }

    @Test
    void transactionRollsBackCertificationUpdateOnFailure() {
        TransactionHarness harness = new TransactionHarness();
        TransactionalNamedParameterJdbcTemplate jdbcTemplate = new TransactionalNamedParameterJdbcTemplate(harness);
        JdbcFilterLookupRegistrationWriteDao dao = new JdbcFilterLookupRegistrationWriteDao(
                providerOf(jdbcTemplate),
                new SQLQueryLoaderUtil(new DefaultResourceLoader())
        );
        RecordingTransactionOperations transactionOperations = new RecordingTransactionOperations(harness);

        assertThrows(IllegalStateException.class, () -> transactionOperations.execute(status -> {
            dao.certifyLookup(certificationRequest());
            dao.insertMetadataChangeHistory(metadataChangeRequest());
            throw new IllegalStateException("audit failed");
        }));

        assertTrue(harness.rolledBack);
        assertFalse(harness.committed);
        assertTrue(jdbcTemplate.committedLookups.isEmpty());
        assertEquals(2, jdbcTemplate.recordedSqls.size());
        assertTrue(jdbcTemplate.recordedSqls.get(0).contains("UPDATE meta.semantic_filter_lookup"));
        assertTrue(jdbcTemplate.recordedSqls.get(1).contains("INSERT INTO meta.metadata_change_history"));
    }

    @Test
    void transactionRollsBackBindingInsertOnFailure() {
        TransactionHarness harness = new TransactionHarness();
        TransactionalNamedParameterJdbcTemplate jdbcTemplate = new TransactionalNamedParameterJdbcTemplate(harness);
        JdbcFilterLookupRegistrationWriteDao dao = new JdbcFilterLookupRegistrationWriteDao(
                providerOf(jdbcTemplate),
                new SQLQueryLoaderUtil(new DefaultResourceLoader())
        );
        RecordingTransactionOperations transactionOperations = new RecordingTransactionOperations(harness);

        assertThrows(IllegalStateException.class, () -> transactionOperations.execute(status -> {
            dao.insertBinding(bindingRequest());
            dao.insertMetadataChangeHistory(metadataChangeRequest());
            throw new IllegalStateException("binding transaction failed");
        }));

        assertTrue(harness.rolledBack);
        assertFalse(harness.committed);
        assertTrue(jdbcTemplate.committedBindings.isEmpty());
        assertEquals(2, jdbcTemplate.recordedSqls.size());
        assertTrue(jdbcTemplate.recordedSqls.get(0).contains("INSERT INTO meta.filter_lookup_binding"));
        assertTrue(jdbcTemplate.recordedSqls.get(1).contains("INSERT INTO meta.metadata_change_history"));
    }

    @Test
    void failsWhenNamedParameterJdbcTemplateMissing() {
        JdbcFilterLookupRegistrationWriteDao dao = new JdbcFilterLookupRegistrationWriteDao(
                providerOf(null),
                new SQLQueryLoaderUtil(new DefaultResourceLoader())
        );

        assertThrows(SemanticLayerException.class, () -> dao.insertLookup(lookupRequest()));
    }

    @Test
    void usesNamedParameterJdbcTemplateAndDoesNotUseJpa() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/lextr/semanticlayer/dao/impl/JdbcFilterLookupRegistrationWriteDao.java"
        ));

        assertTrue(source.contains("NamedParameterJdbcTemplate"));
        assertFalse(source.contains("EntityManager"));
        assertFalse(source.contains("JpaRepository"));
        assertFalse(source.contains("jakarta.persistence"));
        assertFalse(source.contains("javax.persistence"));
    }

    private static SemanticFilterLookupWriteRequest lookupRequest() {
        return new SemanticFilterLookupWriteRequest(
                "LEDGER_SCOPE",
                "MANUAL_LIST",
                "HAND_TYPED",
                "meta.gl_balance",
                "ledger_status = 'ACTIVE'",
                "ledger_id",
                "meta.ledger",
                "ledger_id",
                "ledger_id",
                "IN_LIST",
                500,
                10000,
                60,
                120,
                true,
                true,
                false,
                false,
                "Ledger scope values",
                "client-a",
                "REVIEW",
                "PENDING",
                LocalDate.parse("2026-09-16"),
                "ACTIVE",
                OffsetDateTime.parse("2026-06-18T10:15:30Z"),
                "producer",
                OffsetDateTime.parse("2026-06-18T10:15:30Z"),
                "producer"
        );
    }

    private static FilterLookupWorkflowTaskWriteRequest workflowTaskRequest() {
        return new FilterLookupWorkflowTaskWriteRequest(
                "FILTER_LOOKUP_REGISTRATION",
                "FILTER_LOOKUP",
                "LEDGER_SCOPE",
                "PENDING",
                "producer",
                OffsetDateTime.parse("2026-06-18T10:15:30Z"),
                null,
                LocalDate.parse("2026-09-16"),
                "Review filter lookup LEDGER_SCOPE",
                "client-a",
                null,
                null,
                null
        );
    }

    private static FilterLookupMetadataChangeHistoryWriteRequest metadataChangeRequest() {
        return new FilterLookupMetadataChangeHistoryWriteRequest(
                "FILTER_LOOKUP",
                "LEDGER_SCOPE",
                "REGISTERED",
                "producer",
                OffsetDateTime.parse("2026-06-18T10:15:30Z"),
                null,
                null,
                "Registered filter lookup LEDGER_SCOPE"
        );
    }

    private static FilterLookupCertificationWriteRequest certificationRequest() {
        return new FilterLookupCertificationWriteRequest(
                "client-a",
                "LEDGER_SCOPE",
                "HEALTHY",
                OffsetDateTime.parse("2026-06-18T10:15:30Z"),
                "certifier",
                LocalDate.parse("2026-09-16"),
                OffsetDateTime.parse("2026-06-18T10:15:30Z"),
                "certifier"
        );
    }

    private static FilterLookupBindingWriteRequest bindingRequest() {
        return new FilterLookupBindingWriteRequest(
                "LEDGER_SCOPE",
                "meta.gl_balance",
                "ledger_id",
                "PIPELINE",
                "daily-pipeline",
                "binder",
                OffsetDateTime.parse("2026-06-18T10:15:30Z"),
                true
        );
    }

    private static Map<String, Object> bindingRow() {
        Map<String, Object> row = new HashMap<>();
        row.put("id", 501L);
        row.put("lookup_cd", "LEDGER_SCOPE");
        row.put("bound_obj", "meta.gl_balance");
        row.put("bound_attr_cd", "ledger_id");
        row.put("binding_context_cd", "PIPELINE");
        row.put("binding_ref", "daily-pipeline");
        row.put("bound_by", "binder");
        row.put("bound_ts", OffsetDateTime.parse("2026-06-18T10:15:30Z"));
        row.put("is_active_flg", true);
        return row;
    }

    private static Map<String, Object> lookupRow() {
        Map<String, Object> row = new HashMap<>();
        row.put("id", 101L);
        row.put("lookup_cd", "LEDGER_SCOPE");
        row.put("construction_type_cd", "MANUAL_LIST");
        row.put("manual_subtype_cd", "HAND_TYPED");
        row.put("filter_obj", "meta.gl_balance");
        row.put("filter_condition_txt", "ledger_status = 'ACTIVE'");
        row.put("filter_attr_cd", "ledger_id");
        row.put("validation_obj", "meta.ledger");
        row.put("validation_attr_cd", "ledger_id");
        row.put("suggested_target_attr_cd", "ledger_id");
        row.put("execution_strategy_cd", "IN_LIST");
        row.put("max_input_set_size", 500);
        row.put("max_output_rows", 10000);
        row.put("cache_ttl_min", 60);
        row.put("review_period_days_override", 120);
        row.put("rules_eligible_flg", true);
        row.put("qs_eligible_flg", true);
        row.put("ai_eligible_flg", false);
        row.put("replicate_to_ch_flg", false);
        row.put("description_txt", "Ledger scope values");
        row.put("client_id", "client-a");
        row.put("governance_status_cd", "REVIEW");
        row.put("health_status_cd", "PENDING");
        row.put("last_certified_ts", null);
        row.put("last_certified_by", null);
        row.put("next_review_due_dt", LocalDate.parse("2026-09-16"));
        row.put("lifecycle_status_cd", "ACTIVE");
        row.put("created_ts", OffsetDateTime.parse("2026-06-18T10:15:30Z"));
        row.put("created_by", "producer");
        row.put("updated_ts", OffsetDateTime.parse("2026-06-18T10:15:30Z"));
        row.put("updated_by", "producer");
        return row;
    }

    private static Map<String, Object> workflowTaskRow() {
        Map<String, Object> row = new HashMap<>();
        row.put("id", 301L);
        row.put("task_type_cd", "FILTER_LOOKUP_REGISTRATION");
        row.put("entity_type_cd", "FILTER_LOOKUP");
        row.put("entity_ref", "LEDGER_SCOPE");
        row.put("task_status_cd", "PENDING");
        row.put("submitted_by", "producer");
        row.put("submitted_ts", OffsetDateTime.parse("2026-06-18T10:15:30Z"));
        row.put("assigned_to", null);
        row.put("due_dt", LocalDate.parse("2026-09-16"));
        row.put("description_txt", "Review filter lookup LEDGER_SCOPE");
        row.put("client_id", "client-a");
        row.put("approved_by", null);
        row.put("approved_ts", null);
        row.put("approval_note_txt", null);
        return row;
    }

    private static Map<String, Object> metadataChangeRow() {
        Map<String, Object> row = new HashMap<>();
        row.put("id", 401L);
        row.put("entity_type_cd", "FILTER_LOOKUP");
        row.put("entity_ref", "LEDGER_SCOPE");
        row.put("change_type_cd", "REGISTERED");
        row.put("changed_by", "producer");
        row.put("changed_ts", OffsetDateTime.parse("2026-06-18T10:15:30Z"));
        row.put("old_value_json", null);
        row.put("new_value_json", null);
        row.put("change_reason_txt", "Registered filter lookup LEDGER_SCOPE");
        return row;
    }

    private static Map<String, Object> certifiedLookupRow() {
        Map<String, Object> row = lookupRow();
        row.put("health_status_cd", "HEALTHY");
        row.put("last_certified_ts", OffsetDateTime.parse("2026-06-18T10:15:30Z"));
        row.put("last_certified_by", "certifier");
        row.put("next_review_due_dt", LocalDate.parse("2026-09-16"));
        row.put("updated_ts", OffsetDateTime.parse("2026-06-18T10:15:30Z"));
        row.put("updated_by", "certifier");
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
            recordedSql = sql;
            if (paramSource instanceof MapSqlParameterSource source) {
                recordedParameters = source.getValues();
            }
            return rows.stream().map(row -> mapRow(rowMapper, row)).toList();
        }
    }

    private static final class TransactionalNamedParameterJdbcTemplate extends NamedParameterJdbcTemplate {

        private final TransactionHarness harness;
        private final Map<String, Map<String, Object>> committedLookups = new LinkedHashMap<>();
        private final Map<Long, Map<String, Object>> committedWorkflowTasks = new LinkedHashMap<>();
        private final Map<Long, Map<String, Object>> committedBindings = new LinkedHashMap<>();
        private final List<String> recordedSqls = new ArrayList<>();

        private TransactionalNamedParameterJdbcTemplate(TransactionHarness harness) {
            super(noOpDataSource());
            this.harness = harness;
        }

        @Override
        public <T> List<T> query(String sql, SqlParameterSource paramSource, RowMapper<T> rowMapper) {
            recordedSqls.add(sql);
            Map<String, Object> parameters = paramSource instanceof MapSqlParameterSource source ? source.getValues() : Map.of();
            TransactionState state = harness.transactionState(committedLookups, committedWorkflowTasks, committedBindings);
            Map<String, Object> row;
            if (sql.startsWith("INSERT INTO meta.semantic_filter_lookup")) {
                row = insertedLookupRow(parameters);
                state.lookupRows.put((String) parameters.get("lookup_cd"), row);
            } else if (sql.startsWith("UPDATE meta.semantic_filter_lookup")) {
                row = certifiedLookupRow(parameters);
                state.lookupRows.put((String) parameters.get("lookup_cd"), row);
            } else if (sql.startsWith("INSERT INTO wkfl.workflow_task")) {
                row = insertedWorkflowTaskRow(parameters);
                state.workflowRows.put(301L, row);
            } else if (sql.startsWith("INSERT INTO meta.filter_lookup_binding")) {
                row = insertedBindingRow(parameters);
                state.bindingRows.put(501L, row);
            } else if (sql.startsWith("INSERT INTO meta.metadata_change_history")) {
                row = metadataChangeRow();
            } else {
                throw new IllegalArgumentException("Unexpected SQL: " + sql);
            }
            return List.of(mapRow(rowMapper, row));
        }

        private Map<String, Object> insertedLookupRow(Map<String, Object> parameters) {
            Map<String, Object> row = new HashMap<>();
            row.put("id", 101L);
            row.put("lookup_cd", parameters.get("lookup_cd"));
            row.put("construction_type_cd", parameters.get("construction_type_cd"));
            row.put("manual_subtype_cd", parameters.get("manual_subtype_cd"));
            row.put("filter_obj", parameters.get("filter_obj"));
            row.put("filter_condition_txt", parameters.get("filter_condition_txt"));
            row.put("filter_attr_cd", parameters.get("filter_attr_cd"));
            row.put("validation_obj", parameters.get("validation_obj"));
            row.put("validation_attr_cd", parameters.get("validation_attr_cd"));
            row.put("suggested_target_attr_cd", parameters.get("suggested_target_attr_cd"));
            row.put("execution_strategy_cd", parameters.get("execution_strategy_cd"));
            row.put("max_input_set_size", parameters.get("max_input_set_size"));
            row.put("max_output_rows", parameters.get("max_output_rows"));
            row.put("cache_ttl_min", parameters.get("cache_ttl_min"));
            row.put("review_period_days_override", parameters.get("review_period_days_override"));
            row.put("rules_eligible_flg", parameters.get("rules_eligible_flg"));
            row.put("qs_eligible_flg", parameters.get("qs_eligible_flg"));
            row.put("ai_eligible_flg", parameters.get("ai_eligible_flg"));
            row.put("replicate_to_ch_flg", parameters.get("replicate_to_ch_flg"));
            row.put("description_txt", parameters.get("description_txt"));
            row.put("client_id", parameters.get("client_id"));
            row.put("governance_status_cd", parameters.get("governance_status_cd"));
            row.put("health_status_cd", parameters.get("health_status_cd"));
            row.put("last_certified_ts", null);
            row.put("last_certified_by", null);
            row.put("next_review_due_dt", parameters.get("next_review_due_dt"));
            row.put("lifecycle_status_cd", parameters.get("lifecycle_status_cd"));
            row.put("created_ts", parameters.get("created_ts"));
            row.put("created_by", parameters.get("created_by"));
            row.put("updated_ts", parameters.get("updated_ts"));
            row.put("updated_by", parameters.get("updated_by"));
            return row;
        }

        private Map<String, Object> certifiedLookupRow(Map<String, Object> parameters) {
            Map<String, Object> row = new HashMap<>();
            row.put("id", 101L);
            row.put("lookup_cd", parameters.get("lookup_cd"));
            row.put("construction_type_cd", "MANUAL_LIST");
            row.put("manual_subtype_cd", "HAND_TYPED");
            row.put("filter_obj", "meta.gl_balance");
            row.put("filter_condition_txt", "ledger_status = 'ACTIVE'");
            row.put("filter_attr_cd", "ledger_id");
            row.put("validation_obj", "meta.ledger");
            row.put("validation_attr_cd", "ledger_id");
            row.put("suggested_target_attr_cd", "ledger_id");
            row.put("execution_strategy_cd", "IN_LIST");
            row.put("max_input_set_size", 500);
            row.put("max_output_rows", 10000);
            row.put("cache_ttl_min", 60);
            row.put("review_period_days_override", 120);
            row.put("rules_eligible_flg", true);
            row.put("qs_eligible_flg", true);
            row.put("ai_eligible_flg", false);
            row.put("replicate_to_ch_flg", false);
            row.put("description_txt", "Ledger scope values");
            row.put("client_id", parameters.get("client_id"));
            row.put("governance_status_cd", "REVIEW");
            row.put("health_status_cd", parameters.get("health_status_cd"));
            row.put("last_certified_ts", parameters.get("last_certified_ts"));
            row.put("last_certified_by", parameters.get("last_certified_by"));
            row.put("next_review_due_dt", parameters.get("next_review_due_dt"));
            row.put("lifecycle_status_cd", "ACTIVE");
            row.put("created_ts", OffsetDateTime.parse("2026-06-18T10:15:30Z"));
            row.put("created_by", "producer");
            row.put("updated_ts", parameters.get("updated_ts"));
            row.put("updated_by", parameters.get("updated_by"));
            return row;
        }

        private Map<String, Object> insertedWorkflowTaskRow(Map<String, Object> parameters) {
            Map<String, Object> row = new HashMap<>();
            row.put("id", 301L);
            row.put("task_type_cd", parameters.get("task_type_cd"));
            row.put("entity_type_cd", parameters.get("entity_type_cd"));
            row.put("entity_ref", parameters.get("entity_ref"));
            row.put("task_status_cd", parameters.get("task_status_cd"));
            row.put("submitted_by", parameters.get("submitted_by"));
            row.put("submitted_ts", parameters.get("submitted_ts"));
            row.put("assigned_to", parameters.get("assigned_to"));
            row.put("due_dt", parameters.get("due_dt"));
            row.put("description_txt", parameters.get("description_txt"));
            row.put("client_id", parameters.get("client_id"));
            row.put("approved_by", parameters.get("approved_by"));
            row.put("approved_ts", parameters.get("approved_ts"));
            row.put("approval_note_txt", parameters.get("approval_note_txt"));
            return row;
        }

        private Map<String, Object> insertedBindingRow(Map<String, Object> parameters) {
            Map<String, Object> row = new HashMap<>();
            row.put("id", 501L);
            row.put("lookup_cd", parameters.get("lookup_cd"));
            row.put("bound_obj", parameters.get("bound_obj"));
            row.put("bound_attr_cd", parameters.get("bound_attr_cd"));
            row.put("binding_context_cd", parameters.get("binding_context_cd"));
            row.put("binding_ref", parameters.get("binding_ref"));
            row.put("bound_by", parameters.get("bound_by"));
            row.put("bound_ts", parameters.get("bound_ts"));
            row.put("is_active_flg", parameters.get("is_active_flg"));
            return row;
        }
    }

    private static final class TransactionHarness {

        private TransactionState state;
        private boolean committed;
        private boolean rolledBack;

        private TransactionState transactionState(Map<String, Map<String, Object>> committedLookups,
                                                   Map<Long, Map<String, Object>> committedWorkflowTasks,
                                                   Map<Long, Map<String, Object>> committedBindings) {
            if (state == null) {
                state = new TransactionState(
                        new LinkedHashMap<>(committedLookups),
                        new LinkedHashMap<>(committedWorkflowTasks),
                        new LinkedHashMap<>(committedBindings)
                );
            }
            return state;
        }
    }

    private record TransactionState(Map<String, Map<String, Object>> lookupRows,
                                    Map<Long, Map<String, Object>> workflowRows,
                                    Map<Long, Map<String, Object>> bindingRows) {
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
                harness.state = null;
                harness.committed = true;
                return result;
            } catch (RuntimeException exception) {
                harness.state = null;
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
