package com.lextr.semanticlayer.api;

import com.lextr.semanticlayer.dto.TaxonomyPolicyDecisionDto;
import com.lextr.semanticlayer.dto.TaxonomyPolicyRequestDto;
import com.lextr.semanticlayer.service.TaxonomyPolicyClient;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringJUnitWebConfig(classes = {
        com.lextr.semanticlayer.SemanticLayerApplication.class,
        ObjectRegistrationWireThroughTest.ObjectRegistrationWireThroughTestConfiguration.class
})
@TestExecutionListeners(
        listeners = {
                DependencyInjectionTestExecutionListener.class,
                DirtiesContextBeforeModesTestExecutionListener.class,
                DirtiesContextTestExecutionListener.class
        },
        mergeMode = TestExecutionListeners.MergeMode.REPLACE_DEFAULTS
)
class ObjectRegistrationWireThroughTest {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private RecordingNamedParameterJdbcTemplate jdbcTemplate;

    @Autowired
    private RecordingTaxonomyPolicyClient taxonomyPolicyClient;

    @BeforeEach
    void resetDependencies() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        jdbcTemplate.reset();
        taxonomyPolicyClient.allow();
    }

    @Test
    void honorsObjectRegistrationContractEndToEnd() throws Exception {
        UUID objectId = UUID.fromString("00000000-0000-0000-0000-000000000101");
        UUID connectionId = UUID.fromString("00000000-0000-0000-0000-000000000201");
        UUID attributeId = UUID.fromString("00000000-0000-0000-0000-000000000102");
        UUID workflowTaskId = UUID.fromString("00000000-0000-0000-0000-000000000301");
        UUID changeHistoryId = UUID.fromString("00000000-0000-0000-0000-000000000401");
        OffsetDateTime timestamp = OffsetDateTime.parse("2026-06-17T10:15:30+05:30");
        jdbcTemplate.setResponses(List.of(
                List.of(objectRow(objectId, connectionId, timestamp)),
                List.of(attributeRow(attributeId, objectId, timestamp)),
                List.of(workflowTaskRow(workflowTaskId, objectId, timestamp)),
                List.of(metadataChangeRow(changeHistoryId, objectId, timestamp))
        ));

        mockMvc.perform(post("/api/objects")
                        .contentType("application/json")
                        .content("""
                                {
                                  "client_id": "client-a",
                                  "object_cd": "GL_BALANCE",
                                  "object_nm": "GL Balance",
                                  "object_type_cd": "TABLE",
                                  "schema_cd": "meta",
                                  "connection_id": "00000000-0000-0000-0000-000000000201",
                                  "registered_by": "producer",
                                  "attributes": [
                                    {
                                      "attribute_cd": "AMOUNT",
                                      "attribute_nm": "Amount",
                                      "data_type_cd": "DECIMAL",
                                      "taxonomy_cd": "MDRM12345678",
                                      "taxonomy_source_cd": "MDRM",
                                      "taxonomy_jurisdiction_cd": "US"
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.object_id").value(objectId.toString()))
                .andExpect(jsonPath("$.object_cd").value("GL_BALANCE"))
                .andExpect(jsonPath("$.lifecycle_status_cd").value("DRAFT"))
                .andExpect(jsonPath("$.workflow_task_id").value(workflowTaskId.toString()))
                .andExpect(jsonPath("$.workflow_status_cd").value("PENDING_APPROVAL"))
                .andExpect(jsonPath("$.change_history_id").value(changeHistoryId.toString()))
                .andExpect(jsonPath("$.attributes[0].attribute_id").value(attributeId.toString()))
                .andExpect(jsonPath("$.attributes[0].taxonomy_cd").value("MDRM12345678"))
                .andExpect(jsonPath("$.attributes[0].taxonomy_jurisdiction_cd").value("US"));

        assertEquals(4, jdbcTemplate.recordedSqls().size());
        assertTrue(jdbcTemplate.recordedSqls().get(0).contains("INSERT INTO meta.object_catalog"));
        assertTrue(jdbcTemplate.recordedSqls().get(1).contains("INSERT INTO meta.attribute_catalog"));
        assertTrue(jdbcTemplate.recordedSqls().get(2).contains("INSERT INTO wkfl.workflow_task"));
        assertTrue(jdbcTemplate.recordedSqls().get(3).contains("INSERT INTO meta.metadata_change_history"));

        assertEquals("client-a", jdbcTemplate.recordedParameters().get(0).get("client_id"));
        assertEquals("GL_BALANCE", jdbcTemplate.recordedParameters().get(0).get("object_cd"));
        assertEquals("MDRM12345678", jdbcTemplate.recordedParameters().get(1).get("taxonomy_cd"));
        assertEquals("US", jdbcTemplate.recordedParameters().get(1).get("taxonomy_jurisdiction_cd"));
        assertEquals("PENDING_APPROVAL", jdbcTemplate.recordedParameters().get(2).get("task_status_cd"));
        assertEquals("REGISTERED", jdbcTemplate.recordedParameters().get(3).get("change_type_cd"));

        assertEquals(1, taxonomyPolicyClient.recordedRequests().size());
        TaxonomyPolicyRequestDto policyRequest = taxonomyPolicyClient.recordedRequests().get(0);
        assertEquals("client-a", policyRequest.client_id());
        assertEquals("MDRM12345678", policyRequest.taxonomy_cd());
        assertEquals("MDRM", policyRequest.taxonomy_source_cd());
        assertEquals("US", policyRequest.taxonomy_jurisdiction_cd());
    }

    @Test
    void returnsUnprocessableEntityEndToEndWhenPolicyBlocksRequest() throws Exception {
        taxonomyPolicyClient.deny("taxonomy.jurisdiction_valid", "Taxonomy jurisdiction is invalid");

        mockMvc.perform(post("/api/objects")
                        .contentType("application/json")
                        .content("""
                                {
                                  "client_id": "client-a",
                                  "object_cd": "GL_BALANCE",
                                  "object_nm": "GL Balance",
                                  "object_type_cd": "TABLE",
                                  "schema_cd": "meta",
                                  "connection_id": "00000000-0000-0000-0000-000000000201",
                                  "registered_by": "producer",
                                  "attributes": [
                                    {
                                      "attribute_cd": "AMOUNT",
                                      "attribute_nm": "Amount",
                                      "data_type_cd": "DECIMAL",
                                      "taxonomy_cd": "MDRM12345678",
                                      "taxonomy_source_cd": "MDRM",
                                      "taxonomy_jurisdiction_cd": "USA"
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("taxonomy.jurisdiction_valid"))
                .andExpect(jsonPath("$.message").value("Taxonomy jurisdiction is invalid"));

        assertEquals(0, jdbcTemplate.recordedSqls().size());
        assertEquals(1, taxonomyPolicyClient.recordedRequests().size());
    }

    private static Map<String, Object> objectRow(UUID objectId, UUID connectionId, OffsetDateTime timestamp) {
        Map<String, Object> row = new HashMap<>();
        row.put("object_id", objectId);
        row.put("client_id", "client-a");
        row.put("object_cd", "GL_BALANCE");
        row.put("object_nm", "GL Balance");
        row.put("object_type_cd", "TABLE");
        row.put("schema_cd", "meta");
        row.put("connection_id", connectionId);
        row.put("lifecycle_status_cd", "DRAFT");
        row.put("created_ts", timestamp);
        row.put("created_by", "producer");
        row.put("updated_ts", timestamp);
        row.put("updated_by", "producer");
        return row;
    }

    private static Map<String, Object> attributeRow(UUID attributeId, UUID objectId, OffsetDateTime timestamp) {
        Map<String, Object> row = new HashMap<>();
        row.put("attribute_id", attributeId);
        row.put("object_id", objectId);
        row.put("client_id", "client-a");
        row.put("attribute_cd", "AMOUNT");
        row.put("attribute_nm", "Amount");
        row.put("data_type_cd", "DECIMAL");
        row.put("taxonomy_cd", "MDRM12345678");
        row.put("taxonomy_source_cd", "MDRM");
        row.put("taxonomy_jurisdiction_cd", "US");
        row.put("created_ts", timestamp);
        row.put("created_by", "producer");
        row.put("updated_ts", timestamp);
        row.put("updated_by", "producer");
        return row;
    }

    private static Map<String, Object> workflowTaskRow(UUID workflowTaskId, UUID objectId, OffsetDateTime timestamp) {
        Map<String, Object> row = new HashMap<>();
        row.put("workflow_task_id", workflowTaskId);
        row.put("client_id", "client-a");
        row.put("workflow_type_cd", "OBJECT_REGISTRATION");
        row.put("entity_type_cd", "OBJECT");
        row.put("entity_id", objectId);
        row.put("task_status_cd", "PENDING_APPROVAL");
        row.put("created_ts", timestamp);
        row.put("created_by", "producer");
        row.put("updated_ts", timestamp);
        row.put("updated_by", "producer");
        return row;
    }

    private static Map<String, Object> metadataChangeRow(UUID changeHistoryId, UUID objectId, OffsetDateTime timestamp) {
        Map<String, Object> row = new HashMap<>();
        row.put("change_history_id", changeHistoryId);
        row.put("client_id", "client-a");
        row.put("entity_type_cd", "OBJECT");
        row.put("entity_id", objectId);
        row.put("change_type_cd", "REGISTERED");
        row.put("change_summary_txt", "Registered draft object");
        row.put("created_ts", timestamp);
        row.put("created_by", "producer");
        return row;
    }

    @TestConfiguration
    static class ObjectRegistrationWireThroughTestConfiguration {

        @Bean
        RecordingNamedParameterJdbcTemplate recordingNamedParameterJdbcTemplate() {
            return new RecordingNamedParameterJdbcTemplate();
        }

        @Bean
        RecordingTaxonomyPolicyClient recordingTaxonomyPolicyClient() {
            return new RecordingTaxonomyPolicyClient();
        }
    }

    static final class RecordingTaxonomyPolicyClient implements TaxonomyPolicyClient {

        private final List<TaxonomyPolicyRequestDto> recordedRequests = new ArrayList<>();
        private TaxonomyPolicyDecisionDto decision = new TaxonomyPolicyDecisionDto(true, null, null);

        void allow() {
            recordedRequests.clear();
            decision = new TaxonomyPolicyDecisionDto(true, null, null);
        }

        void deny(String code, String message) {
            recordedRequests.clear();
            decision = new TaxonomyPolicyDecisionDto(false, code, message);
        }

        List<TaxonomyPolicyRequestDto> recordedRequests() {
            return recordedRequests;
        }

        @Override
        public TaxonomyPolicyDecisionDto validateJurisdiction(TaxonomyPolicyRequestDto request) {
            recordedRequests.add(request);
            return decision;
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
