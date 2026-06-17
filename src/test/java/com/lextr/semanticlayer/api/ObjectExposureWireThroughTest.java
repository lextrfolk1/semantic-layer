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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringJUnitWebConfig(classes = {
        com.lextr.semanticlayer.SemanticLayerApplication.class,
        ObjectExposureWireThroughTest.ObjectExposureWireThroughTestConfiguration.class
})
@TestExecutionListeners(
        listeners = {
                DependencyInjectionTestExecutionListener.class,
                DirtiesContextBeforeModesTestExecutionListener.class,
                DirtiesContextTestExecutionListener.class
        },
        mergeMode = TestExecutionListeners.MergeMode.REPLACE_DEFAULTS
)
class ObjectExposureWireThroughTest {

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
    void honorsObjectListContractEndToEnd() throws Exception {
        UUID objectId = UUID.fromString("00000000-0000-0000-0000-000000000101");
        UUID connectionId = UUID.fromString("00000000-0000-0000-0000-000000000201");
        jdbcTemplate.setResponses(List.of(List.of(objectRow(objectId, connectionId))));

        mockMvc.perform(get("/api/objects")
                        .queryParam("client_id", "client-a")
                        .queryParam("schema_cd", "meta")
                        .queryParam("lifecycle_status_cd", "ACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].object_id").value(objectId.toString()))
                .andExpect(jsonPath("$[0].object_cd").value("GL_BALANCE"))
                .andExpect(jsonPath("$[0].object_nm").value("GL Balance Override"))
                .andExpect(jsonPath("$[0].schema_cd").value("meta"))
                .andExpect(jsonPath("$[0].lifecycle_status_cd").value("ACTIVE"));

        assertEquals(1, jdbcTemplate.recordedSqls().size());
        assertTrue(jdbcTemplate.recordedSqls().get(0).contains("FROM meta.object_catalog"));
        assertEquals("client-a", jdbcTemplate.recordedParameters().get(0).get("client_id"));
        assertEquals("meta", jdbcTemplate.recordedParameters().get(0).get("schema_cd"));
        assertEquals("ACTIVE", jdbcTemplate.recordedParameters().get(0).get("lifecycle_status_cd"));
    }

    @Test
    void honorsObjectDetailContractEndToEnd() throws Exception {
        UUID objectId = UUID.fromString("00000000-0000-0000-0000-000000000101");
        UUID connectionId = UUID.fromString("00000000-0000-0000-0000-000000000201");
        UUID attributeId = UUID.fromString("00000000-0000-0000-0000-000000000102");
        jdbcTemplate.setResponses(List.of(
                List.of(objectRow(objectId, connectionId)),
                List.of(attributeRow(attributeId, objectId))
        ));

        mockMvc.perform(get("/api/objects/{object_id}", objectId)
                        .queryParam("client_id", "client-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.object_id").value(objectId.toString()))
                .andExpect(jsonPath("$.object_nm").value("GL Balance Override"))
                .andExpect(jsonPath("$.attributes[0].attribute_id").value(attributeId.toString()))
                .andExpect(jsonPath("$.attributes[0].attribute_cd").value("AMOUNT"))
                .andExpect(jsonPath("$.attributes[0].attribute_nm").value("Amount Override"))
                .andExpect(jsonPath("$.attributes[0].taxonomy_cd").value("MDRM12345678"));

        assertEquals(2, jdbcTemplate.recordedSqls().size());
        assertTrue(jdbcTemplate.recordedSqls().get(0).contains("object_id = :object_id"));
        assertTrue(jdbcTemplate.recordedSqls().get(1).contains("FROM meta.attribute_catalog"));
        assertEquals("client-a", jdbcTemplate.recordedParameters().get(0).get("client_id"));
        assertEquals(objectId, jdbcTemplate.recordedParameters().get(0).get("object_id"));
        assertEquals("client-a", jdbcTemplate.recordedParameters().get(1).get("client_id"));
        assertEquals(objectId, jdbcTemplate.recordedParameters().get(1).get("object_id"));
    }

    @Test
    void returnsNotFoundEndToEndWhenObjectUnknown() throws Exception {
        UUID objectId = UUID.fromString("00000000-0000-0000-0000-000000000999");
        jdbcTemplate.setResponses(List.of(List.of()));

        mockMvc.perform(get("/api/objects/{object_id}", objectId)
                        .queryParam("client_id", "client-a"))
                .andExpect(status().isNotFound());

        assertEquals(1, jdbcTemplate.recordedSqls().size());
        assertTrue(jdbcTemplate.recordedSqls().get(0).contains("object_id = :object_id"));
        assertEquals("client-a", jdbcTemplate.recordedParameters().get(0).get("client_id"));
        assertEquals(objectId, jdbcTemplate.recordedParameters().get(0).get("object_id"));
    }

    private static Map<String, Object> objectRow(UUID objectId, UUID connectionId) {
        Map<String, Object> row = new HashMap<>();
        row.put("object_id", objectId);
        row.put("client_id", "client-a");
        row.put("object_cd", "GL_BALANCE");
        row.put("object_nm", "GL Balance");
        row.put("effective_object_nm", "GL Balance Override");
        row.put("object_type_cd", "TABLE");
        row.put("schema_cd", "meta");
        row.put("connection_id", connectionId);
        row.put("lifecycle_status_cd", "ACTIVE");
        row.put("created_ts", OffsetDateTime.parse("2026-06-16T10:15:30+05:30"));
        row.put("created_by", "producer");
        row.put("updated_ts", OffsetDateTime.parse("2026-06-17T10:15:30+05:30"));
        row.put("updated_by", "platform");
        return row;
    }

    private static Map<String, Object> attributeRow(UUID attributeId, UUID objectId) {
        Map<String, Object> row = new HashMap<>();
        row.put("attribute_id", attributeId);
        row.put("object_id", objectId);
        row.put("client_id", "client-a");
        row.put("attribute_cd", "AMOUNT");
        row.put("attribute_nm", "Amount");
        row.put("effective_attribute_nm", "Amount Override");
        row.put("data_type_cd", "DECIMAL");
        row.put("taxonomy_cd", "MDRM12345678");
        row.put("taxonomy_source_cd", "MDRM");
        row.put("taxonomy_jurisdiction_cd", "US");
        row.put("created_ts", OffsetDateTime.parse("2026-06-16T10:15:30+05:30"));
        row.put("created_by", "producer");
        row.put("updated_ts", OffsetDateTime.parse("2026-06-17T10:15:30+05:30"));
        row.put("updated_by", "platform");
        return row;
    }

    @TestConfiguration
    static class ObjectExposureWireThroughTestConfiguration {

        @Bean
        RecordingNamedParameterJdbcTemplate recordingNamedParameterJdbcTemplate() {
            return new RecordingNamedParameterJdbcTemplate();
        }
    }

    static final class RecordingNamedParameterJdbcTemplate extends NamedParameterJdbcTemplate {

        private List<List<Map<String, Object>>> responses = List.of();
        private final List<String> recordedSqls = new ArrayList<>();
        private final List<Map<String, Object>> recordedParameters = new ArrayList<>();

        RecordingNamedParameterJdbcTemplate() {
            super(noOpDataSource());
        }

        void reset() {
            responses = List.of();
            recordedSqls.clear();
            recordedParameters.clear();
        }

        void setResponses(List<List<Map<String, Object>>> responses) {
            this.responses = new ArrayList<>(responses);
        }

        List<String> recordedSqls() {
            return recordedSqls;
        }

        List<Map<String, Object>> recordedParameters() {
            return recordedParameters;
        }

        @Override
        public <T> List<T> query(String sql, SqlParameterSource paramSource, RowMapper<T> rowMapper) {
            recordedSqls.add(sql);
            if (paramSource instanceof MapSqlParameterSource source) {
                recordedParameters.add(new HashMap<>(source.getValues()));
            }
            List<Map<String, Object>> rows = responses.isEmpty() ? List.of() : responses.remove(0);
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
