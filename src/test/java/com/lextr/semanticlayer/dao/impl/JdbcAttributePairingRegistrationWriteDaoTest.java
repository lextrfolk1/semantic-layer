package com.lextr.semanticlayer.dao.impl;

import com.lextr.semanticlayer.exception.SemanticLayerException;
import com.lextr.semanticlayer.model.AttributePairingCatalogRecord;
import com.lextr.semanticlayer.model.AttributePairingCatalogWriteRequest;
import com.lextr.semanticlayer.model.FilterLookupMetadataChangeHistoryRecord;
import com.lextr.semanticlayer.model.FilterLookupMetadataChangeHistoryWriteRequest;
import com.lextr.semanticlayer.model.FilterLookupWorkflowTaskRecord;
import com.lextr.semanticlayer.model.FilterLookupWorkflowTaskWriteRequest;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JdbcAttributePairingRegistrationWriteDaoTest {

    @Test
    void bindsPairingInsertParametersAndMapsReturnedColumns() {
        RecordingNamedParameterJdbcTemplate jdbcTemplate = new RecordingNamedParameterJdbcTemplate(List.of(pairingRow()));
        JdbcAttributePairingRegistrationWriteDao dao = new JdbcAttributePairingRegistrationWriteDao(
                providerOf(jdbcTemplate),
                new SQLQueryLoaderUtil(new DefaultResourceLoader())
        );

        AttributePairingCatalogRecord result = dao.insertPairing(pairingRequest());

        assertTrue(jdbcTemplate.recordedSql.contains("INSERT INTO meta.attribute_pairing_catalog"));
        assertEquals("CUSTOMER_NAME_TO_ID", jdbcTemplate.recordedParameters.get("pairing_cd"));
        assertEquals("customer_nm", jdbcTemplate.recordedParameters.get("display_attribute_cd"));
        assertEquals("customer_id", jdbcTemplate.recordedParameters.get("filter_attribute_cd"));
        assertEquals("ACTIVE", jdbcTemplate.recordedParameters.get("lifecycle_status_cd"));
        assertEquals("CUSTOMER_NAME_TO_ID", result.pairing_cd());
        assertEquals("customer_nm", result.display_attribute_cd());
        assertEquals("customer_id", result.filter_attribute_cd());
    }

    @Test
    void bindsWorkflowTaskParametersAndMapsReturnedColumns() {
        RecordingNamedParameterJdbcTemplate jdbcTemplate = new RecordingNamedParameterJdbcTemplate(List.of(workflowTaskRow()));
        JdbcAttributePairingRegistrationWriteDao dao = new JdbcAttributePairingRegistrationWriteDao(
                providerOf(jdbcTemplate),
                new SQLQueryLoaderUtil(new DefaultResourceLoader())
        );

        FilterLookupWorkflowTaskRecord result = dao.insertWorkflowTask(workflowTaskRequest());

        assertTrue(jdbcTemplate.recordedSql.contains("INSERT INTO wkfl.workflow_task"));
        assertEquals("ATTRIBUTE_PAIRING_REGISTRATION", jdbcTemplate.recordedParameters.get("task_type_cd"));
        assertEquals("CUSTOMER_NAME_TO_ID", result.entity_ref());
        assertEquals("PENDING", result.task_status_cd());
    }

    @Test
    void bindsMetadataChangeParametersAndMapsReturnedColumns() {
        RecordingNamedParameterJdbcTemplate jdbcTemplate = new RecordingNamedParameterJdbcTemplate(List.of(metadataChangeRow()));
        JdbcAttributePairingRegistrationWriteDao dao = new JdbcAttributePairingRegistrationWriteDao(
                providerOf(jdbcTemplate),
                new SQLQueryLoaderUtil(new DefaultResourceLoader())
        );

        FilterLookupMetadataChangeHistoryRecord result = dao.insertMetadataChangeHistory(metadataChangeRequest());

        assertTrue(jdbcTemplate.recordedSql.contains("INSERT INTO meta.metadata_change_history"));
        assertEquals("ATTRIBUTE_PAIRING", jdbcTemplate.recordedParameters.get("entity_type_cd"));
        assertEquals("REGISTERED", jdbcTemplate.recordedParameters.get("change_type_cd"));
        assertEquals("Registered attribute pairing CUSTOMER_NAME_TO_ID", result.change_reason_txt());
    }

    @Test
    void failsWhenNamedParameterJdbcTemplateMissing() {
        JdbcAttributePairingRegistrationWriteDao dao = new JdbcAttributePairingRegistrationWriteDao(
                providerOf(null),
                new SQLQueryLoaderUtil(new DefaultResourceLoader())
        );

        assertThrows(SemanticLayerException.class, () -> dao.insertPairing(pairingRequest()));
    }

    @Test
    void usesNamedParameterJdbcTemplateAndDoesNotUseJpa() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/lextr/semanticlayer/dao/impl/JdbcAttributePairingRegistrationWriteDao.java"
        ));

        assertTrue(source.contains("NamedParameterJdbcTemplate"));
        assertTrue(!source.contains("EntityManager"));
        assertTrue(!source.contains("JpaRepository"));
        assertTrue(!source.contains("jakarta.persistence"));
        assertTrue(!source.contains("javax.persistence"));
    }

    private static AttributePairingCatalogWriteRequest pairingRequest() {
        return new AttributePairingCatalogWriteRequest(
                "CUSTOMER_NAME_TO_ID",
                "Customer Name To Id",
                "meta",
                "customer",
                "customer_nm",
                "customer_id",
                "DISPLAY_TO_FILTER",
                "CACHED_LOOKUP",
                "{\"Acme Corp\":\"CUST-100\"}",
                null,
                true,
                3600,
                "ONE_TO_ONE",
                false,
                false,
                true,
                "BTREE",
                20,
                "Resolve customer name to id",
                "client-a",
                "ACTIVE",
                "PENDING",
                1,
                OffsetDateTime.parse("2026-06-18T10:15:30Z"),
                "producer",
                OffsetDateTime.parse("2026-06-18T10:15:30Z"),
                "producer"
        );
    }

    private static FilterLookupWorkflowTaskWriteRequest workflowTaskRequest() {
        return new FilterLookupWorkflowTaskWriteRequest(
                "ATTRIBUTE_PAIRING_REGISTRATION",
                "ATTRIBUTE_PAIRING",
                "CUSTOMER_NAME_TO_ID",
                "PENDING",
                "producer",
                OffsetDateTime.parse("2026-06-18T10:15:30Z"),
                null,
                null,
                "Review attribute pairing CUSTOMER_NAME_TO_ID",
                "client-a",
                null,
                null,
                null
        );
    }

    private static FilterLookupMetadataChangeHistoryWriteRequest metadataChangeRequest() {
        return new FilterLookupMetadataChangeHistoryWriteRequest(
                "ATTRIBUTE_PAIRING",
                "CUSTOMER_NAME_TO_ID",
                "REGISTERED",
                "producer",
                OffsetDateTime.parse("2026-06-18T10:15:30Z"),
                null,
                null,
                "Registered attribute pairing CUSTOMER_NAME_TO_ID"
        );
    }

    private static Map<String, Object> pairingRow() {
        Map<String, Object> row = new HashMap<>();
        row.put("id", 101L);
        row.put("pairing_cd", "CUSTOMER_NAME_TO_ID");
        row.put("pairing_nm", "Customer Name To Id");
        row.put("schema_cd", "meta");
        row.put("object_cd", "customer");
        row.put("display_attribute_cd", "customer_nm");
        row.put("filter_attribute_cd", "customer_id");
        row.put("pairing_type_cd", "DISPLAY_TO_FILTER");
        row.put("lookup_strategy_cd", "CACHED_LOOKUP");
        row.put("lookup_inline_map_jsonb", "{\"Acme Corp\":\"CUST-100\"}");
        row.put("lookup_sql_template_txt", null);
        row.put("lookup_cache_enabled_flg", true);
        row.put("lookup_cache_ttl_seconds_nbr", 3600);
        row.put("cardinality_cd", "ONE_TO_ONE");
        row.put("is_bidirectional_flg", false);
        row.put("is_cross_engine_flg", false);
        row.put("filter_attribute_indexed_flg", true);
        row.put("filter_attribute_index_type_cd", "BTREE");
        row.put("performance_gain_pct_est_nbr", 20);
        row.put("ai_context_txt", "Resolve customer name to id");
        row.put("client_id", "client-a");
        row.put("lifecycle_status_cd", "ACTIVE");
        row.put("governance_review_status_cd", "PENDING");
        row.put("version_nbr", 1);
        row.put("created_ts", OffsetDateTime.parse("2026-06-18T10:15:30Z"));
        row.put("created_by", "producer");
        row.put("updated_ts", OffsetDateTime.parse("2026-06-18T10:15:30Z"));
        row.put("updated_by", "producer");
        return row;
    }

    private static Map<String, Object> workflowTaskRow() {
        Map<String, Object> row = new HashMap<>();
        row.put("id", 201L);
        row.put("task_type_cd", "ATTRIBUTE_PAIRING_REGISTRATION");
        row.put("entity_type_cd", "ATTRIBUTE_PAIRING");
        row.put("entity_ref", "CUSTOMER_NAME_TO_ID");
        row.put("task_status_cd", "PENDING");
        row.put("submitted_by", "producer");
        row.put("submitted_ts", OffsetDateTime.parse("2026-06-18T10:15:30Z"));
        row.put("assigned_to", null);
        row.put("due_dt", null);
        row.put("description_txt", "Review attribute pairing CUSTOMER_NAME_TO_ID");
        row.put("client_id", "client-a");
        row.put("approved_by", null);
        row.put("approved_ts", null);
        row.put("approval_note_txt", null);
        return row;
    }

    private static Map<String, Object> metadataChangeRow() {
        Map<String, Object> row = new HashMap<>();
        row.put("id", 301L);
        row.put("entity_type_cd", "ATTRIBUTE_PAIRING");
        row.put("entity_ref", "CUSTOMER_NAME_TO_ID");
        row.put("change_type_cd", "REGISTERED");
        row.put("changed_by", "producer");
        row.put("changed_ts", OffsetDateTime.parse("2026-06-18T10:15:30Z"));
        row.put("old_value_json", null);
        row.put("new_value_json", null);
        row.put("change_reason_txt", "Registered attribute pairing CUSTOMER_NAME_TO_ID");
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
                        case "getBoolean" -> Boolean.TRUE.equals(row.get(args[0]));
                        case "getObject" -> {
                            if (args.length == 1) {
                                yield row.get(args[0]);
                            }
                            Object value = row.get(args[0]);
                            yield value;
                        }
                        default -> null;
                    }
            );
        }
    }

    private static DataSource noOpDataSource() {
        return new AbstractDataSource() {
            @Override
            public Connection getConnection() {
                throw new UnsupportedOperationException();
            }

            @Override
            public Connection getConnection(String username, String password) {
                throw new UnsupportedOperationException();
            }
        };
    }
}
