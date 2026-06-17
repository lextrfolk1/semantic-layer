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
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringJUnitWebConfig(classes = {
        com.lextr.semanticlayer.SemanticLayerApplication.class,
        RegistryReadWireThroughTest.RegistryReadWireThroughTestConfiguration.class
})
@TestExecutionListeners(
        listeners = {
                DependencyInjectionTestExecutionListener.class,
                DirtiesContextBeforeModesTestExecutionListener.class,
                DirtiesContextTestExecutionListener.class
        },
        mergeMode = TestExecutionListeners.MergeMode.REPLACE_DEFAULTS
)
class RegistryReadWireThroughTest {

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
    void honorsSchemaContractEndToEnd() throws Exception {
        jdbcTemplate.setRows(List.of(Map.of(
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

        mockMvc.perform(get("/api/registry/schemas")
                        .queryParam("client_id", "client-a")
                        .queryParam("lifecycle_status_cd", "ACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].schema_cd").value("meta"))
                .andExpect(jsonPath("$[0].schema_nm").value("Metadata Override"))
                .andExpect(jsonPath("$[0].lifecycle_status_cd").value("ACTIVE"));

        assertTrue(jdbcTemplate.recordedSql().contains("FROM meta.schema_catalog"));
        assertEquals("client-a", jdbcTemplate.recordedParameters().get("client_id"));
        assertEquals("ACTIVE", jdbcTemplate.recordedParameters().get("lifecycle_status_cd"));
    }

    @Test
    void honorsConnectionContractEndToEndAndWithholdsSecrets() throws Exception {
        UUID connectionId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        jdbcTemplate.setRows(List.of(connectionRow(connectionId)));

        mockMvc.perform(get("/api/registry/connections/{connection_id}", connectionId)
                        .queryParam("client_id", "client-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.connection_id").value(connectionId.toString()))
                .andExpect(jsonPath("$.connection_nm").value("Lextr PostgreSQL Override"))
                .andExpect(jsonPath("$.engine_cd").value("POSTGRES"))
                .andExpect(jsonPath("$.secrets_ref").doesNotExist());

        assertTrue(jdbcTemplate.recordedSql().contains("connection_id = :connection_id"));
        assertFalse(jdbcTemplate.recordedSql().contains("secrets_ref"));
        assertEquals("client-a", jdbcTemplate.recordedParameters().get("client_id"));
        assertEquals(connectionId, jdbcTemplate.recordedParameters().get("connection_id"));
    }

    @Test
    void returnsNotFoundEndToEndWhenRecordUnknown() throws Exception {
        jdbcTemplate.setRows(List.of());

        mockMvc.perform(get("/api/registry/connections/{connection_id}", UUID.fromString("00000000-0000-0000-0000-000000000099"))
                        .queryParam("client_id", "client-a"))
                .andExpect(status().isNotFound());

        assertTrue(jdbcTemplate.recordedSql().contains("connection_id = :connection_id"));
        assertEquals("client-a", jdbcTemplate.recordedParameters().get("client_id"));
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
        row.put("updated_ts", OffsetDateTime.parse("2026-06-17T10:15:30+05:30"));
        row.put("updated_by", "platform");
        return row;
    }

    @TestConfiguration
    static class RegistryReadWireThroughTestConfiguration {

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
