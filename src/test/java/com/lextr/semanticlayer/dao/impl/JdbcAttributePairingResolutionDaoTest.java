package com.lextr.semanticlayer.dao.impl;

import com.lextr.semanticlayer.exception.SemanticLayerException;
import com.lextr.semanticlayer.model.AttributePairingCacheHitWriteRequest;
import com.lextr.semanticlayer.model.AttributePairingCatalogRecord;
import com.lextr.semanticlayer.model.AttributePairingValueCacheRecord;
import com.lextr.semanticlayer.model.AttributePairingValueCacheWriteRequest;
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
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JdbcAttributePairingResolutionDaoTest {

    @Test
    void findsPairingByCodeAndMapsReturnedColumns() {
        RecordingNamedParameterJdbcTemplate jdbcTemplate = new RecordingNamedParameterJdbcTemplate(List.of(List.of(pairingRow())));
        JdbcAttributePairingResolutionDao dao = new JdbcAttributePairingResolutionDao(
                providerOf(jdbcTemplate),
                new SQLQueryLoaderUtil(new DefaultResourceLoader())
        );

        Optional<AttributePairingCatalogRecord> result = dao.findPairing("client-a", "CUSTOMER_NAME_TO_ID");

        assertTrue(jdbcTemplate.recordedSqls.get(0).contains("FROM meta.attribute_pairing_catalog"));
        assertEquals("client-a", jdbcTemplate.recordedParameters.get(0).get("client_id"));
        assertEquals("CUSTOMER_NAME_TO_ID", jdbcTemplate.recordedParameters.get(0).get("pairing_cd"));
        assertTrue(result.isPresent());
        assertEquals("customer_nm", result.orElseThrow().display_attribute_cd());
    }

    @Test
    void findsActivePairingAndChecksIndexGate() {
        RecordingNamedParameterJdbcTemplate jdbcTemplate = new RecordingNamedParameterJdbcTemplate(List.of(
                List.of(pairingRow()),
                List.of(Map.of("indexed_flg", true))
        ));
        JdbcAttributePairingResolutionDao dao = new JdbcAttributePairingResolutionDao(
                providerOf(jdbcTemplate),
                new SQLQueryLoaderUtil(new DefaultResourceLoader())
        );

        Optional<AttributePairingCatalogRecord> pairing = dao.findActivePairing("client-a", "meta", "customer", "customer_nm");
        boolean indexed = dao.isAttributeIndexed("meta", "customer", "customer_id");

        assertTrue(jdbcTemplate.recordedSqls.get(0).contains("display_attribute_cd = :display_attribute_cd"));
        assertTrue(jdbcTemplate.recordedSqls.get(1).contains("FROM pg_indexes"));
        assertEquals("customer_id", jdbcTemplate.recordedParameters.get(1).get("attribute_cd"));
        assertTrue(pairing.isPresent());
        assertTrue(indexed);
    }

    @Test
    void findsCachedValueAndUpsertsAndRecordsHit() {
        RecordingNamedParameterJdbcTemplate jdbcTemplate = new RecordingNamedParameterJdbcTemplate(List.of(
                List.of(cacheRow()),
                List.of(cacheRow()),
                List.of(cacheRow())
        ));
        JdbcAttributePairingResolutionDao dao = new JdbcAttributePairingResolutionDao(
                providerOf(jdbcTemplate),
                new SQLQueryLoaderUtil(new DefaultResourceLoader())
        );

        Optional<AttributePairingValueCacheRecord> cached = dao.findCachedValue(
                "CUSTOMER_NAME_TO_ID",
                "client-a",
                "Acme Corp",
                OffsetDateTime.parse("2026-06-18T10:15:30Z")
        );
        AttributePairingValueCacheRecord upserted = dao.upsertCachedValue(new AttributePairingValueCacheWriteRequest(
                "CUSTOMER_NAME_TO_ID",
                "client-a",
                "Acme Corp",
                "CUST-100",
                false,
                0L,
                null,
                OffsetDateTime.parse("2026-06-18T10:15:30Z"),
                OffsetDateTime.parse("2026-06-18T11:15:30Z")
        ));
        AttributePairingValueCacheRecord hit = dao.recordCacheHit(new AttributePairingCacheHitWriteRequest(
                "CUSTOMER_NAME_TO_ID",
                "Acme Corp",
                "client-a",
                OffsetDateTime.parse("2026-06-18T10:30:30Z")
        ));

        assertTrue(jdbcTemplate.recordedSqls.get(0).contains("FROM meta.attribute_pairing_value_cache"));
        assertTrue(jdbcTemplate.recordedSqls.get(1).contains("ON CONFLICT"));
        assertTrue(jdbcTemplate.recordedSqls.get(2).contains("hit_count_nbr = hit_count_nbr + 1"));
        assertEquals("Acme Corp", cached.orElseThrow().display_value_txt());
        assertEquals("CUST-100", upserted.filter_value_txt());
        assertEquals("CUSTOMER_NAME_TO_ID", hit.pairing_cd());
    }

    @Test
    void failsWhenNamedParameterJdbcTemplateMissing() {
        JdbcAttributePairingResolutionDao dao = new JdbcAttributePairingResolutionDao(
                providerOf(null),
                new SQLQueryLoaderUtil(new DefaultResourceLoader())
        );

        assertThrows(SemanticLayerException.class, () -> dao.findPairing("client-a", "CUSTOMER_NAME_TO_ID"));
    }

    @Test
    void usesNamedParameterJdbcTemplateAndDoesNotUseJpa() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/lextr/semanticlayer/dao/impl/JdbcAttributePairingResolutionDao.java"
        ));

        assertTrue(source.contains("NamedParameterJdbcTemplate"));
        assertTrue(!source.contains("EntityManager"));
        assertTrue(!source.contains("JpaRepository"));
        assertTrue(!source.contains("jakarta.persistence"));
        assertTrue(!source.contains("javax.persistence"));
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

    private static Map<String, Object> cacheRow() {
        Map<String, Object> row = new HashMap<>();
        row.put("id", 201L);
        row.put("pairing_cd", "CUSTOMER_NAME_TO_ID");
        row.put("client_id", "client-a");
        row.put("display_value_txt", "Acme Corp");
        row.put("filter_value_txt", "CUST-100");
        row.put("is_one_to_many_flg", false);
        row.put("hit_count_nbr", 1L);
        row.put("last_hit_ts", OffsetDateTime.parse("2026-06-18T10:15:30Z"));
        row.put("cached_ts", OffsetDateTime.parse("2026-06-18T10:15:30Z"));
        row.put("expires_ts", OffsetDateTime.parse("2026-06-18T11:15:30Z"));
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

        private final Queue<List<Map<String, Object>>> rowsByCall;
        private final List<String> recordedSqls = new java.util.ArrayList<>();
        private final List<Map<String, Object>> recordedParameters = new java.util.ArrayList<>();

        private RecordingNamedParameterJdbcTemplate(List<List<Map<String, Object>>> rowsByCall) {
            super(noOpDataSource());
            this.rowsByCall = new ArrayDeque<>(rowsByCall);
        }

        @Override
        public <T> List<T> query(String sql, SqlParameterSource paramSource, RowMapper<T> rowMapper) {
            recordedSqls.add(sql);
            if (paramSource instanceof MapSqlParameterSource source) {
                recordedParameters.add(source.getValues());
            }
            List<Map<String, Object>> rows = rowsByCall.remove();
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
                            yield row.get(args[0]);
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
