package com.lextr.semanticlayer.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
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
        GovernanceHistoryWireThroughTest.GovernanceHistoryWireThroughTestConfiguration.class
})
@TestExecutionListeners(
        listeners = {
                DependencyInjectionTestExecutionListener.class,
                DirtiesContextBeforeModesTestExecutionListener.class,
                DirtiesContextTestExecutionListener.class
        },
        mergeMode = TestExecutionListeners.MergeMode.REPLACE_DEFAULTS
)
class GovernanceHistoryWireThroughTest {

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
    void honorsGovernanceHistoryContractEndToEnd() throws Exception {
        jdbcTemplate.setRows(List.of(historyRow("APPROVED")));

        mockMvc.perform(get("/api/governance/history")
                        .queryParam("client_id", "client-a")
                        .queryParam("entity_type_cd", "OBJECT")
                        .queryParam("entity_ref", "meta.gl_balance")
                        .queryParam("change_type_cd", "APPROVED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].event_id").value(501L))
                .andExpect(jsonPath("$[0].client_id").value("client-a"))
                .andExpect(jsonPath("$[0].entity_type_cd").value("OBJECT"))
                .andExpect(jsonPath("$[0].entity_ref").value("meta.gl_balance"))
                .andExpect(jsonPath("$[0].change_type_cd").value("APPROVED"))
                .andExpect(jsonPath("$[0].change_summary_txt").value("Object registered"))
                .andExpect(jsonPath("$[0].actor_id").value("producer"))
                .andExpect(jsonPath("$[0].event_ts").value("2026-06-18T00:00:00Z"))
                .andExpect(jsonPath("$[0].old_value_json").value("{\"status\":\"DRAFT\"}"))
                .andExpect(jsonPath("$[0].new_value_json").value("{\"status\":\"ACTIVE\"}"));

        assertTrue(jdbcTemplate.recordedSql().contains("FROM meta.metadata_change_history mch"));
        assertEquals("client-a", jdbcTemplate.recordedParameters().get("client_id"));
        assertEquals("OBJECT", jdbcTemplate.recordedParameters().get("entity_type_cd"));
        assertEquals("meta.gl_balance", jdbcTemplate.recordedParameters().get("entity_ref"));
        assertEquals("APPROVED", jdbcTemplate.recordedParameters().get("change_type_cd"));
    }

    @Test
    void returnsNotFoundWhenEntityUnknown() throws Exception {
        jdbcTemplate.setRows(List.of());

        mockMvc.perform(get("/api/governance/history")
                        .queryParam("client_id", "client-a")
                        .queryParam("entity_type_cd", "OBJECT")
                        .queryParam("entity_ref", "missing"))
                .andExpect(status().isNotFound());

        assertEquals("missing", jdbcTemplate.recordedParameters().get("entity_ref"));
    }

    @Test
    void returnsUnprocessableEntityWhenClientIdBlank() throws Exception {
        mockMvc.perform(get("/api/governance/history")
                        .queryParam("client_id", "")
                        .queryParam("entity_type_cd", "OBJECT")
                        .queryParam("entity_ref", "meta.gl_balance"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("CLIENT_ID_REQUIRED"))
                .andExpect(jsonPath("$.message").value("client_id is required"));
    }

    private static Map<String, Object> historyRow(String changeTypeCode) {
        Map<String, Object> row = new HashMap<>();
        row.put("event_id", 501L);
        row.put("client_id", "client-a");
        row.put("entity_type_cd", "OBJECT");
        row.put("entity_ref", "meta.gl_balance");
        row.put("change_type_cd", changeTypeCode);
        row.put("change_summary_txt", "Object registered");
        row.put("actor_id", "producer");
        row.put("event_ts", OffsetDateTime.parse("2026-06-18T00:00:00Z"));
        row.put("old_value_json", "{\"status\":\"DRAFT\"}");
        row.put("new_value_json", "{\"status\":\"ACTIVE\"}");
        return row;
    }

    @TestConfiguration
    static class GovernanceHistoryWireThroughTestConfiguration {

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
