package com.lextr.semanticlayer.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.jdbc.datasource.AbstractDataSource;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener;
import org.springframework.test.context.support.DirtiesContextBeforeModesTestExecutionListener;
import org.springframework.test.context.support.DirtiesContextTestExecutionListener;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import javax.sql.DataSource;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringJUnitWebConfig(classes = {
        com.lextr.semanticlayer.SemanticLayerApplication.class,
        GovernancePolicyPresetWireThroughTest.GovernancePolicyPresetWireThroughTestConfiguration.class
})
@TestExecutionListeners(
        listeners = {
                DependencyInjectionTestExecutionListener.class,
                DirtiesContextBeforeModesTestExecutionListener.class,
                DirtiesContextTestExecutionListener.class
        },
        mergeMode = TestExecutionListeners.MergeMode.REPLACE_DEFAULTS
)
class GovernancePolicyPresetWireThroughTest {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private RecordingNamedParameterJdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetTemplate() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        jdbcTemplate.reset();
    }

    @Test
    void honorsPolicyPresetsContractEndToEnd() throws Exception {
        jdbcTemplate.setRows(List.of(policyPresetRow("GOV-FL-001", "FILTER_LOOKUP")));

        mockMvc.perform(get("/api/governance/policy-presets")
                        .queryParam("client_id", "client-a")
                        .queryParam("policy_scope_cd", "FILTER_LOOKUP")
                        .queryParam("as_of_dt", "2026-06-18"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].policy_cd").value("GOV-FL-001"))
                .andExpect(jsonPath("$[0].policy_scope_cd").value("FILTER_LOOKUP"))
                .andExpect(jsonPath("$[0].default_value_txt").value("90"));

        assertTrue(jdbcTemplate.recordedSql().contains("FROM governance.policy_preset"));
        assertEquals("FILTER_LOOKUP", jdbcTemplate.recordedParameters().get("policy_scope_cd"));
        assertEquals(LocalDate.parse("2026-06-18"), jdbcTemplate.recordedParameters().get("as_of_dt"));
    }

    @Test
    void honorsSinglePolicyPresetContractEndToEnd() throws Exception {
        jdbcTemplate.setRows(List.of(policyPresetRow("GOV-FL-001", "FILTER_LOOKUP")));

        mockMvc.perform(get("/api/governance/policy-presets/GOV-FL-001")
                        .queryParam("client_id", "client-a")
                        .queryParam("policy_scope_cd", "FILTER_LOOKUP")
                        .queryParam("as_of_dt", "2026-06-18"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.policy_cd").value("GOV-FL-001"))
                .andExpect(jsonPath("$.policy_scope_cd").value("FILTER_LOOKUP"))
                .andExpect(jsonPath("$.default_value_txt").value("90"));

        assertTrue(jdbcTemplate.recordedSql().contains("policy_cd = :policy_cd"));
        assertEquals("GOV-FL-001", jdbcTemplate.recordedParameters().get("policy_cd"));
        assertEquals("FILTER_LOOKUP", jdbcTemplate.recordedParameters().get("policy_scope_cd"));
        assertEquals(LocalDate.parse("2026-06-18"), jdbcTemplate.recordedParameters().get("as_of_dt"));
    }

    @Test
    void returnsNotFoundWhenPresetUnknown() throws Exception {
        jdbcTemplate.setRows(List.of());

        mockMvc.perform(get("/api/governance/policy-presets/GOV-FL-999")
                        .queryParam("client_id", "client-a")
                        .queryParam("policy_scope_cd", "FILTER_LOOKUP"))
                .andExpect(status().isNotFound());

        assertTrue(jdbcTemplate.recordedSql().contains("policy_cd = :policy_cd"));
        assertEquals("GOV-FL-999", jdbcTemplate.recordedParameters().get("policy_cd"));
        assertEquals("FILTER_LOOKUP", jdbcTemplate.recordedParameters().get("policy_scope_cd"));
    }

    private static Map<String, Object> policyPresetRow(String policyCode, String scope) {
        Map<String, Object> row = new HashMap<>();
        row.put("policy_cd", policyCode);
        row.put("policy_nm", "Minimum review frequency (floor, days)");
        row.put("policy_scope_cd", scope);
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

    @TestConfiguration
    static class GovernancePolicyPresetWireThroughTestConfiguration {

        @Bean
        RecordingNamedParameterJdbcTemplate recordingNamedParameterJdbcTemplate() {
            return new RecordingNamedParameterJdbcTemplate();
        }
    }

    static final class RecordingNamedParameterJdbcTemplate extends NamedParameterJdbcTemplate {

        private List<Map<String, Object>> rows = List.of();
        private String recordedSql = "";
        private Map<String, Object> recordedParameters = Map.of();

        RecordingNamedParameterJdbcTemplate() {
            super(noOpDataSource());
        }

        void reset() {
            rows = List.of();
            recordedSql = "";
            recordedParameters = Map.of();
        }

        void setRows(List<Map<String, Object>> rows) {
            this.rows = rows;
        }

        String recordedSql() {
            return recordedSql;
        }

        Map<String, Object> recordedParameters() {
            return recordedParameters;
        }

        @Override
        public <T> List<T> query(String sql, SqlParameterSource paramSource, RowMapper<T> rowMapper) {
            recordedSql = sql;
            if (paramSource instanceof MapSqlParameterSource source) {
                recordedParameters = source.getValues();
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
