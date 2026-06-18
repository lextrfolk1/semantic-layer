package com.lextr.semanticlayer.dao.impl;

import com.lextr.semanticlayer.exception.SemanticLayerException;
import com.lextr.semanticlayer.model.FilterLookupPreviewValueRecord;
import com.lextr.semanticlayer.model.FilterLookupValueCountRecord;
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
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JdbcFilterLookupEffectiveReviewReadDaoTest {

    @Test
    void bindsLookupParametersAndMapsSnakeCaseColumns() {
        RecordingNamedParameterJdbcTemplate jdbcTemplate = new RecordingNamedParameterJdbcTemplate(List.of(lookupRow()));
        JdbcFilterLookupEffectiveReviewReadDao dao = new JdbcFilterLookupEffectiveReviewReadDao(
                providerOf(jdbcTemplate),
                new SQLQueryLoaderUtil(new DefaultResourceLoader())
        );

        SemanticFilterLookupRecord result = dao.findLookupByCode("client-a", "LEDGER_SCOPE").orElseThrow();

        assertTrue(jdbcTemplate.recordedSql.contains("FROM meta.semantic_filter_lookup"));
        assertEquals("client-a", jdbcTemplate.recordedParameters.get("client_id"));
        assertEquals("LEDGER_SCOPE", jdbcTemplate.recordedParameters.get("lookup_cd"));
        assertEquals("LEDGER_SCOPE", result.lookup_cd());
        assertEquals("REVIEW", result.governance_status_cd());
        assertEquals("client-a", result.client_id());
    }

    @Test
    void bindsManualPreviewParametersAndMapsExpectedSnakeCaseColumns() {
        RecordingNamedParameterJdbcTemplate jdbcTemplate = new RecordingNamedParameterJdbcTemplate(List.of(previewValueRow()));
        JdbcFilterLookupEffectiveReviewReadDao dao = new JdbcFilterLookupEffectiveReviewReadDao(
                providerOf(jdbcTemplate),
                new SQLQueryLoaderUtil(new DefaultResourceLoader())
        );

        List<FilterLookupPreviewValueRecord> result = dao.findManualValuesByLookup("client-a", "LEDGER_SCOPE");

        assertTrue(jdbcTemplate.recordedSql.contains("FROM meta.filter_lookup_value flv"));
        assertTrue(jdbcTemplate.recordedSql.contains("JOIN meta.semantic_filter_lookup sfl ON sfl.lookup_cd = flv.lookup_cd"));
        assertTrue(jdbcTemplate.recordedSql.contains("flv.lifecycle_status_cd IN ('ACTIVE','ANTICIPATED')"));
        assertEquals("client-a", jdbcTemplate.recordedParameters.get("client_id"));
        assertEquals("LEDGER_SCOPE", jdbcTemplate.recordedParameters.get("lookup_cd"));
        assertEquals(1, result.size());
        assertEquals("client-a", result.get(0).client_id());
        assertEquals("LEDGER_100", result.get(0).value_cd());
        assertEquals("ANTICIPATED", result.get(0).lifecycle_status_cd());
        assertEquals(LocalDate.parse("2026-07-01"), result.get(0).anticipated_dt());
    }

    @Test
    void bindsCountParametersAndMapsValueCountColumns() {
        RecordingNamedParameterJdbcTemplate jdbcTemplate = new RecordingNamedParameterJdbcTemplate(List.of(valueCountRow()));
        JdbcFilterLookupEffectiveReviewReadDao dao = new JdbcFilterLookupEffectiveReviewReadDao(
                providerOf(jdbcTemplate),
                new SQLQueryLoaderUtil(new DefaultResourceLoader())
        );

        FilterLookupValueCountRecord result = dao.countValuesByLookup("client-a", "LEDGER_SCOPE");

        assertTrue(jdbcTemplate.recordedSql.contains("COUNT(*) AS value_count"));
        assertTrue(jdbcTemplate.recordedSql.contains("JOIN meta.semantic_filter_lookup sfl ON sfl.lookup_cd = flv.lookup_cd"));
        assertEquals("client-a", jdbcTemplate.recordedParameters.get("client_id"));
        assertEquals("LEDGER_SCOPE", jdbcTemplate.recordedParameters.get("lookup_cd"));
        assertEquals("LEDGER_SCOPE", result.lookup_cd());
        assertEquals("client-a", result.client_id());
        assertEquals(7L, result.value_count());
    }

    @Test
    void returnsZeroCountWhenNoMatchingPreviewValues() {
        RecordingNamedParameterJdbcTemplate jdbcTemplate = new RecordingNamedParameterJdbcTemplate(List.of());
        JdbcFilterLookupEffectiveReviewReadDao dao = new JdbcFilterLookupEffectiveReviewReadDao(
                providerOf(jdbcTemplate),
                new SQLQueryLoaderUtil(new DefaultResourceLoader())
        );

        FilterLookupValueCountRecord result = dao.countValuesByLookup("client-a", "LEDGER_SCOPE");

        assertEquals("LEDGER_SCOPE", result.lookup_cd());
        assertEquals("client-a", result.client_id());
        assertEquals(0L, result.value_count());
    }

    @Test
    void failsWhenNamedParameterJdbcTemplateMissing() {
        JdbcFilterLookupEffectiveReviewReadDao dao = new JdbcFilterLookupEffectiveReviewReadDao(
                providerOf(null),
                new SQLQueryLoaderUtil(new DefaultResourceLoader())
        );

        assertThrows(SemanticLayerException.class, () -> dao.findLookupByCode("client-a", "LEDGER_SCOPE"));
    }

    @Test
    void usesNamedParameterJdbcTemplateAndDoesNotUseJpa() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/lextr/semanticlayer/dao/impl/JdbcFilterLookupEffectiveReviewReadDao.java"
        ));

        assertTrue(source.contains("NamedParameterJdbcTemplate"));
        assertFalse(source.contains("EntityManager"));
        assertFalse(source.contains("JpaRepository"));
        assertFalse(source.contains("jakarta.persistence"));
        assertFalse(source.contains("javax.persistence"));
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

    private static Map<String, Object> previewValueRow() {
        Map<String, Object> row = new HashMap<>();
        row.put("lookup_cd", "LEDGER_SCOPE");
        row.put("client_id", "client-a");
        row.put("value_cd", "LEDGER_100");
        row.put("value_desc", "Ledger 100");
        row.put("lifecycle_status_cd", "ANTICIPATED");
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

    private static Map<String, Object> valueCountRow() {
        Map<String, Object> row = new HashMap<>();
        row.put("lookup_cd", "LEDGER_SCOPE");
        row.put("client_id", "client-a");
        row.put("value_count", 7L);
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
