package com.lextr.semanticlayer.dao.impl;

import com.lextr.semanticlayer.exception.SemanticLayerException;
import com.lextr.semanticlayer.model.FilterLookupPreviewValueRecord;
import com.lextr.semanticlayer.model.SemanticFilterLookupRecord;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JdbcFilterLookupReadDaoTest {

    @Test
    void bindsLookupListParametersAndMapsSnakeCaseColumns() {
        RecordingNamedParameterJdbcTemplate jdbcTemplate =
                new RecordingNamedParameterJdbcTemplate(List.of(filterLookupRow("LEDGER_SCOPE", 45)));
        JdbcFilterLookupReadDao dao = new JdbcFilterLookupReadDao(providerOf(jdbcTemplate), new SQLQueryLoaderUtil(new DefaultResourceLoader()));

        List<SemanticFilterLookupRecord> results = dao.findLookups("client-a", "REVIEW", "PENDING", "ACTIVE");

        assertTrue(jdbcTemplate.recordedSql.contains("client_id = :client_id"));
        assertEquals("client-a", jdbcTemplate.recordedParameters.get("client_id"));
        assertEquals("REVIEW", jdbcTemplate.recordedParameters.get("governance_status_cd"));
        assertEquals("PENDING", jdbcTemplate.recordedParameters.get("health_status_cd"));
        assertEquals("ACTIVE", jdbcTemplate.recordedParameters.get("lifecycle_status_cd"));
        assertEquals(1, results.size());
        assertEquals(Long.valueOf(101L), results.get(0).id());
        assertEquals("LEDGER_SCOPE", results.get(0).lookup_cd());
        assertEquals(Integer.valueOf(45), results.get(0).review_period_days_override());
        assertEquals("PENDING", results.get(0).health_status_cd());
        assertEquals(LocalDate.parse("2026-08-02"), results.get(0).next_review_due_dt());
        assertEquals("producer", results.get(0).created_by());
    }

    @Test
    void findsSingleLookupByCodeWithinClientScope() {
        RecordingNamedParameterJdbcTemplate jdbcTemplate =
                new RecordingNamedParameterJdbcTemplate(List.of(filterLookupRow("LEDGER_SCOPE", null)));
        JdbcFilterLookupReadDao dao = new JdbcFilterLookupReadDao(providerOf(jdbcTemplate), new SQLQueryLoaderUtil(new DefaultResourceLoader()));

        SemanticFilterLookupRecord result = dao.findLookup("client-a", "LEDGER_SCOPE").orElseThrow();

        assertTrue(jdbcTemplate.recordedSql.contains("lookup_cd = :lookup_cd"));
        assertEquals("client-a", jdbcTemplate.recordedParameters.get("client_id"));
        assertEquals("LEDGER_SCOPE", jdbcTemplate.recordedParameters.get("lookup_cd"));
        assertEquals("MANUAL_LIST", result.construction_type_cd());
        assertEquals("certifier", result.last_certified_by());
        assertEquals("ACTIVE", result.lifecycle_status_cd());
    }

    @Test
    void bindsManualValueParametersAndMapsPreviewSnakeCaseColumns() {
        RecordingNamedParameterJdbcTemplate jdbcTemplate =
                new RecordingNamedParameterJdbcTemplate(List.of(previewValueRow("LEDGER_100")));
        JdbcFilterLookupReadDao dao = new JdbcFilterLookupReadDao(providerOf(jdbcTemplate), new SQLQueryLoaderUtil(new DefaultResourceLoader()));

        List<FilterLookupPreviewValueRecord> results = dao.findManualValues("client-a", "LEDGER_SCOPE");

        assertTrue(jdbcTemplate.recordedSql.contains("FROM meta.filter_lookup_value flv"));
        assertTrue(jdbcTemplate.recordedSql.contains("JOIN meta.semantic_filter_lookup sfl ON sfl.lookup_cd = flv.lookup_cd"));
        assertEquals("client-a", jdbcTemplate.recordedParameters.get("client_id"));
        assertEquals("LEDGER_SCOPE", jdbcTemplate.recordedParameters.get("lookup_cd"));
        assertEquals(1, results.size());
        assertEquals("client-a", results.get(0).client_id());
        assertEquals("LEDGER_100", results.get(0).value_cd());
        assertEquals("ACTIVE", results.get(0).lifecycle_status_cd());
        assertEquals(LocalDate.parse("2026-07-01"), results.get(0).anticipated_dt());
    }

    @Test
    void buildsSqlPreviewQueryFromLookupMetadataAndMapsDistinctValues() {
        RecordingNamedParameterJdbcTemplate jdbcTemplate =
                new RecordingNamedParameterJdbcTemplate(List.of(sqlPreviewValueRow("LEDGER_100")));
        JdbcFilterLookupReadDao dao = new JdbcFilterLookupReadDao(providerOf(jdbcTemplate), new SQLQueryLoaderUtil(new DefaultResourceLoader()));

        List<FilterLookupPreviewValueRecord> results = dao.findSqlValues("client-a", filterLookupRowRecord(
                "SQL_LEDGER_SCOPE",
                "SQL_QUERY",
                "meta.gl_balance",
                "ledger_id",
                "ledger_status = 'ACTIVE'",
                250
        ));

        assertTrue(jdbcTemplate.recordedSql.contains("SELECT DISTINCT :lookup_cd AS lookup_cd"));
        assertTrue(jdbcTemplate.recordedSql.contains("CAST(ledger_id AS varchar(100)) AS value_cd"));
        assertTrue(jdbcTemplate.recordedSql.contains("FROM meta.gl_balance"));
        assertTrue(jdbcTemplate.recordedSql.contains("WHERE ledger_status = 'ACTIVE'"));
        assertEquals("SQL_LEDGER_SCOPE", jdbcTemplate.recordedParameters.get("lookup_cd"));
        assertEquals("client-a", jdbcTemplate.recordedParameters.get("client_id"));
        assertEquals(250, jdbcTemplate.recordedParameters.get("max_output_rows"));
        assertEquals(1, results.size());
        assertEquals("LEDGER_100", results.get(0).value_cd());
        assertEquals("ACTIVE", results.get(0).lifecycle_status_cd());
    }

    @Test
    void rejectsUnsafeSqlPreviewMetadata() {
        RecordingNamedParameterJdbcTemplate jdbcTemplate = new RecordingNamedParameterJdbcTemplate(List.of());
        JdbcFilterLookupReadDao dao = new JdbcFilterLookupReadDao(providerOf(jdbcTemplate), new SQLQueryLoaderUtil(new DefaultResourceLoader()));

        assertThrows(
                SemanticLayerException.class,
                () -> dao.findSqlValues("client-a", filterLookupRowRecord(
                        "SQL_LEDGER_SCOPE",
                        "SQL_QUERY",
                        "meta.gl_balance; DROP TABLE meta.semantic_filter_lookup",
                        "ledger_id",
                        "ledger_status = 'ACTIVE'",
                        250
                ))
        );

        assertThrows(
                SemanticLayerException.class,
                () -> dao.findSqlValues("client-a", filterLookupRowRecord(
                        "SQL_LEDGER_SCOPE",
                        "SQL_QUERY",
                        "meta.gl_balance",
                        "ledger_id",
                        "ledger_status = 'ACTIVE'; DELETE FROM meta.filter_lookup_exec_log",
                        250
                ))
        );
    }

    @Test
    void countsLookupValuesWithinClientScope() {
        RecordingNamedParameterJdbcTemplate jdbcTemplate =
                new RecordingNamedParameterJdbcTemplate(List.of(valueCountRow(3L)));
        JdbcFilterLookupReadDao dao = new JdbcFilterLookupReadDao(providerOf(jdbcTemplate), new SQLQueryLoaderUtil(new DefaultResourceLoader()));

        long result = dao.countValues("client-a", "LEDGER_SCOPE");

        assertTrue(jdbcTemplate.recordedSql.contains("COUNT(*) AS value_count"));
        assertEquals("client-a", jdbcTemplate.recordedParameters.get("client_id"));
        assertEquals("LEDGER_SCOPE", jdbcTemplate.recordedParameters.get("lookup_cd"));
        assertTrue(jdbcTemplate.recordedSql.contains("JOIN meta.semantic_filter_lookup sfl ON sfl.lookup_cd = flv.lookup_cd"));
        assertEquals(3L, result);
    }

    @Test
    void defaultsValueCountToZeroWhenNoRowsReturn() {
        RecordingNamedParameterJdbcTemplate jdbcTemplate = new RecordingNamedParameterJdbcTemplate(List.of());
        JdbcFilterLookupReadDao dao = new JdbcFilterLookupReadDao(providerOf(jdbcTemplate), new SQLQueryLoaderUtil(new DefaultResourceLoader()));

        long result = dao.countValues("client-a", "LEDGER_SCOPE");

        assertEquals(0L, result);
    }

    @Test
    void failsWhenNamedParameterJdbcTemplateMissing() {
        JdbcFilterLookupReadDao dao = new JdbcFilterLookupReadDao(providerOf(null), new SQLQueryLoaderUtil(new DefaultResourceLoader()));

        assertThrows(SemanticLayerException.class, () -> dao.findLookups("client-a", null, null, null));
    }

    @Test
    void usesNamedParameterJdbcTemplateAndDoesNotUseJpa() throws Exception {
        Path daoPath = Path.of("src/main/java/com/lextr/semanticlayer/dao/impl/JdbcFilterLookupReadDao.java");
        String source = Files.readString(daoPath);

        assertTrue(source.contains("NamedParameterJdbcTemplate"));
        assertTrue(!source.contains("JpaRepository"));
        assertTrue(!source.contains("EntityManager"));
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

    private static Map<String, Object> filterLookupRow(String lookupCode, Integer overrideDays) {
        Map<String, Object> row = new HashMap<>();
        row.put("id", 101L);
        row.put("lookup_cd", lookupCode);
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
        row.put("review_period_days_override", overrideDays);
        row.put("rules_eligible_flg", true);
        row.put("qs_eligible_flg", true);
        row.put("ai_eligible_flg", false);
        row.put("replicate_to_ch_flg", false);
        row.put("description_txt", "Ledger scope values");
        row.put("client_id", "client-a");
        row.put("governance_status_cd", "REVIEW");
        row.put("health_status_cd", "PENDING");
        row.put("last_certified_ts", OffsetDateTime.parse("2026-06-16T10:15:30+05:30"));
        row.put("last_certified_by", "certifier");
        row.put("next_review_due_dt", LocalDate.parse("2026-08-02"));
        row.put("lifecycle_status_cd", "ACTIVE");
        row.put("created_ts", OffsetDateTime.parse("2026-06-16T10:15:30+05:30"));
        row.put("created_by", "producer");
        row.put("updated_ts", OffsetDateTime.parse("2026-06-17T10:15:30+05:30"));
        row.put("updated_by", "platform");
        return row;
    }

    private static Map<String, Object> valueCountRow(long valueCount) {
        Map<String, Object> row = new HashMap<>();
        row.put("value_count", valueCount);
        return row;
    }

    private static SemanticFilterLookupRecord filterLookupRowRecord(String lookupCode,
                                                                   String constructionTypeCode,
                                                                   String filterObject,
                                                                   String filterAttributeCode,
                                                                   String filterCondition,
                                                                   Integer maxOutputRows) {
        return new SemanticFilterLookupRecord(
                101L,
                lookupCode,
                constructionTypeCode,
                "HAND_TYPED",
                filterObject,
                filterCondition,
                filterAttributeCode,
                "meta.ledger",
                "ledger_id",
                "ledger_id",
                "IN_LIST",
                500,
                maxOutputRows,
                60,
                null,
                true,
                true,
                false,
                false,
                "Ledger scope values",
                "client-a",
                "ACTIVE",
                "HEALTHY",
                OffsetDateTime.parse("2026-06-16T10:15:30Z"),
                "certifier",
                LocalDate.parse("2026-08-02"),
                "ACTIVE",
                OffsetDateTime.parse("2026-06-16T10:15:30Z"),
                "producer",
                OffsetDateTime.parse("2026-06-17T10:15:30Z"),
                "platform"
        );
    }

    private static Map<String, Object> previewValueRow(String valueCode) {
        Map<String, Object> row = new HashMap<>();
        row.put("lookup_cd", "LEDGER_SCOPE");
        row.put("client_id", "client-a");
        row.put("value_cd", valueCode);
        row.put("value_desc", "Ledger 100");
        row.put("lifecycle_status_cd", "ACTIVE");
        row.put("validated_flg", true);
        row.put("anticipated_dt", LocalDate.parse("2026-07-01"));
        row.put("workflow_ref", "WKFL-100");
        row.put("last_seen_in_source_ts", OffsetDateTime.parse("2026-06-18T10:15:30Z"));
        row.put("auto_expire_after_days", 14);
        row.put("alert_txt", "Pending activation");
        row.put("added_by", "producer");
        row.put("added_ts", OffsetDateTime.parse("2026-06-18T09:15:30Z"));
        row.put("certified_by", "reviewer");
        row.put("certified_ts", OffsetDateTime.parse("2026-06-18T11:15:30Z"));
        row.put("updated_ts", OffsetDateTime.parse("2026-06-18T12:15:30Z"));
        return row;
    }

    private static Map<String, Object> sqlPreviewValueRow(String valueCode) {
        Map<String, Object> row = previewValueRow(valueCode);
        row.put("lookup_cd", "SQL_LEDGER_SCOPE");
        row.put("workflow_ref", null);
        row.put("anticipated_dt", null);
        row.put("added_by", null);
        row.put("added_ts", null);
        row.put("certified_by", null);
        row.put("certified_ts", null);
        row.put("updated_ts", null);
        row.put("auto_expire_after_days", null);
        row.put("alert_txt", null);
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
