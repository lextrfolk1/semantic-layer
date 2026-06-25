package com.lextr.semanticlayer.dao.impl;

import com.lextr.semanticlayer.exception.SemanticLayerException;
import com.lextr.semanticlayer.model.GovernancePolicyPresetRecord;
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

class JdbcGovernancePolicyPresetReadDaoTest {

    @Test
    void bindsGovernancePolicyPresetParametersAndMapsReturnedColumns() {
        RecordingNamedParameterJdbcTemplate jdbcTemplate = new RecordingNamedParameterJdbcTemplate(List.of(policyPresetRow()));
        JdbcGovernancePolicyPresetReadDao dao = new JdbcGovernancePolicyPresetReadDao(
                providerOf(jdbcTemplate),
                new SQLQueryLoaderUtil(new DefaultResourceLoader())
        );

        GovernancePolicyPresetRecord result = dao.findPolicyPreset(
                "GOV-FL-001",
                "FILTER_LOOKUP",
                LocalDate.parse("2026-06-18")
        ).orElseThrow();

        assertTrue(jdbcTemplate.recordedSql.contains("FROM governance.policy_preset"));
        assertEquals("GOV-FL-001", jdbcTemplate.recordedParameters.get("policy_cd"));
        assertEquals("FILTER_LOOKUP", jdbcTemplate.recordedParameters.get("policy_scope_cd"));
        assertEquals(LocalDate.parse("2026-06-18"), jdbcTemplate.recordedParameters.get("as_of_dt"));
        assertEquals("90", result.default_value_txt());
        assertEquals("INTEGER", result.data_type_cd());
        assertTrue(result.is_overrideable_flg());
    }

    @Test
    void bindsGovernancePolicyPresetsParametersAndMapsReturnedColumns() {
        RecordingNamedParameterJdbcTemplate jdbcTemplate = new RecordingNamedParameterJdbcTemplate(List.of(policyPresetRow()));
        JdbcGovernancePolicyPresetReadDao dao = new JdbcGovernancePolicyPresetReadDao(
                providerOf(jdbcTemplate),
                new SQLQueryLoaderUtil(new DefaultResourceLoader())
        );

        List<GovernancePolicyPresetRecord> results = dao.findPolicyPresets(
                "FILTER_LOOKUP",
                LocalDate.parse("2026-06-18")
        );

        assertEquals(1, results.size());
        GovernancePolicyPresetRecord result = results.get(0);
        assertTrue(jdbcTemplate.recordedSql.contains("FROM governance.policy_preset"));
        assertEquals("FILTER_LOOKUP", jdbcTemplate.recordedParameters.get("policy_scope_cd"));
        assertEquals(LocalDate.parse("2026-06-18"), jdbcTemplate.recordedParameters.get("as_of_dt"));
        assertEquals("90", result.default_value_txt());
        assertEquals("INTEGER", result.data_type_cd());
        assertTrue(result.is_overrideable_flg());
    }

    @Test
    void failsWhenNamedParameterJdbcTemplateMissing() {
        JdbcGovernancePolicyPresetReadDao dao = new JdbcGovernancePolicyPresetReadDao(
                providerOf(null),
                new SQLQueryLoaderUtil(new DefaultResourceLoader())
        );

        assertThrows(SemanticLayerException.class, () -> dao.findPolicyPreset(
                "GOV-FL-001",
                "FILTER_LOOKUP",
                LocalDate.parse("2026-06-18")
        ));
    }

    @Test
    void usesNamedParameterJdbcTemplateAndDoesNotUseJpa() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/lextr/semanticlayer/dao/impl/JdbcGovernancePolicyPresetReadDao.java"
        ));

        assertTrue(source.contains("NamedParameterJdbcTemplate"));
        assertTrue(source.contains("sqlQueryLoaderUtil.getQuery(FIND_BY_CODE)"));
        assertTrue(source.contains("sqlQueryLoaderUtil.getQuery(FIND_ALL)"));
        assertFalse(source.contains("EntityManager"));
        assertFalse(source.contains("JpaRepository"));
        assertFalse(source.contains("jakarta.persistence"));
        assertFalse(source.contains("javax.persistence"));
    }

    private static Map<String, Object> policyPresetRow() {
        Map<String, Object> row = new HashMap<>();
        row.put("policy_cd", "GOV-FL-001");
        row.put("policy_nm", "Minimum review frequency (floor, days)");
        row.put("policy_scope_cd", "FILTER_LOOKUP");
        row.put("default_value_txt", "90");
        row.put("data_type_cd", "INTEGER");
        row.put("is_overrideable_flg", true);
        row.put("override_requires_approval_flg", true);
        row.put("effective_from_dt", LocalDate.parse("2026-01-01"));
        row.put("effective_to_dt", null);
        row.put("approved_by", "governance-owner");
        row.put("approved_ts", OffsetDateTime.parse("2026-01-01T00:00:00Z"));
        row.put("created_ts", OffsetDateTime.parse("2026-01-01T00:00:00Z"));
        row.put("created_by", "governance-owner");
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
}
