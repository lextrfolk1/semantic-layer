package com.lextr.semanticlayer.api;

import com.lextr.semanticlayer.dto.ObjectExposureAccessPolicyRequestDto;
import com.lextr.semanticlayer.dto.ObjectExposureClassificationPolicyDecisionDto;
import com.lextr.semanticlayer.dto.ObjectExposureClassificationPolicyRequestDto;
import com.lextr.semanticlayer.dto.ObjectExposurePolicyDecisionDto;
import com.lextr.semanticlayer.service.ObjectExposurePolicyClient;
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

    @Autowired
    private RecordingObjectExposurePolicyClient policyClient;

    @BeforeEach
    void resetTemplate() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        jdbcTemplate.reset();
        policyClient.reset();
    }

    @Test
    void honorsObjectListContractAndWritesAuditEndToEnd() throws Exception {
        UUID objectId = UUID.fromString("00000000-0000-0000-0000-000000000101");
        UUID connectionId = UUID.fromString("00000000-0000-0000-0000-000000000201");
        jdbcTemplate.setResponses(List.of(List.of(objectRow(objectId, connectionId))));

        mockMvc.perform(get("/api/objects")
                        .queryParam("client_id", "client-a")
                        .queryParam("schema_cd", "meta")
                        .queryParam("lifecycle_status_cd", "ACTIVE")
                        .header("X-Actor-Id", "analyst-1")
                        .header("X-Role-Cd", "FINANCE")
                        .header("X-Purpose-Cd", "REPORTING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].object_id").value(objectId.toString()))
                .andExpect(jsonPath("$[0].object_cd").value("GL_BALANCE"))
                .andExpect(jsonPath("$[0].object_nm").value("GL Balance Override"))
                .andExpect(jsonPath("$[0].schema_cd").value("meta"))
                .andExpect(jsonPath("$[0].lifecycle_status_cd").value("ACTIVE"));

        assertEquals(2, jdbcTemplate.recordedSqls().size());
        assertTrue(jdbcTemplate.recordedSqls().get(0).contains("FROM meta.object_catalog"));
        assertTrue(jdbcTemplate.recordedSqls().get(1).contains("INSERT INTO meta.metadata_change_history"));
        assertEquals("client-a", jdbcTemplate.recordedParameters().get(0).get("client_id"));
        assertEquals("analyst-1", jdbcTemplate.recordedParameters().get(1).get("changed_by"));
        assertEquals("analyst-1", policyClient.accessRequests().get(0).actor_id());
    }

    @Test
    void masksAttributeFieldsAndWritesAuditEndToEnd() throws Exception {
        UUID objectId = UUID.fromString("00000000-0000-0000-0000-000000000101");
        UUID connectionId = UUID.fromString("00000000-0000-0000-0000-000000000201");
        UUID attributeId = UUID.fromString("00000000-0000-0000-0000-000000000102");
        jdbcTemplate.setResponses(List.of(
                List.of(objectRow(objectId, connectionId)),
                List.of(attributeRow(attributeId, objectId)),
                List.of(attributeGrantRow())
        ));

        mockMvc.perform(get("/api/objects/{object_id}", objectId)
                        .queryParam("client_id", "client-a")
                        .header("X-Actor-Id", "analyst-1")
                        .header("X-Role-Cd", "FINANCE")
                        .header("X-Purpose-Cd", "REPORTING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.object_id").value(objectId.toString()))
                .andExpect(jsonPath("$.attributes[0].attribute_id").value(attributeId.toString()))
                .andExpect(jsonPath("$.attributes[0].attribute_cd").value("AMOUNT"))
                .andExpect(jsonPath("$.attributes[0].attribute_nm").value("REDACTED"))
                .andExpect(jsonPath("$.attributes[0].taxonomy_cd").value("REDACTED"))
                .andExpect(jsonPath("$.attributes[0].taxonomy_source_cd").value("REDACTED"))
                .andExpect(jsonPath("$.attributes[0].taxonomy_jurisdiction_cd").value("REDACTED"));

        assertEquals(4, jdbcTemplate.recordedSqls().size());
        assertTrue(jdbcTemplate.recordedSqls().get(2).contains("FROM meta.attribute_access_grant"));
        assertTrue(jdbcTemplate.recordedSqls().get(3).contains("INSERT INTO meta.metadata_change_history"));
    }

    @Test
    void returnsUnprocessableEntityEndToEndWhenCrossTenantPolicyBlocks() throws Exception {
        UUID objectId = UUID.fromString("00000000-0000-0000-0000-000000000101");
        UUID connectionId = UUID.fromString("00000000-0000-0000-0000-000000000201");
        jdbcTemplate.setResponses(List.of(List.of(objectRow(objectId, connectionId))));

        mockMvc.perform(get("/api/objects/{object_id}", objectId)
                        .queryParam("client_id", "client-b")
                        .header("X-Actor-Id", "analyst-1")
                        .header("X-Role-Cd", "FINANCE")
                        .header("X-Purpose-Cd", "REPORTING"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("POL-AC-001"))
                .andExpect(jsonPath("$.message").value("Cross-tenant access denied"));

        assertEquals(2, jdbcTemplate.recordedSqls().size());
        assertTrue(jdbcTemplate.recordedSqls().get(1).contains("INSERT INTO meta.metadata_change_history"));
    }

    @Test
    void returnsUnprocessableEntityEndToEndWhenRoleOrPurposeMissing() throws Exception {
        UUID objectId = UUID.fromString("00000000-0000-0000-0000-000000000101");
        UUID connectionId = UUID.fromString("00000000-0000-0000-0000-000000000201");
        jdbcTemplate.setResponses(List.of(List.of(objectRow(objectId, connectionId))));

        mockMvc.perform(get("/api/objects/{object_id}", objectId)
                        .queryParam("client_id", "client-a")
                        .header("X-Actor-Id", "analyst-1"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("POL-AC-001"))
                .andExpect(jsonPath("$.message").value("Need-to-know denied"));

        assertEquals(2, jdbcTemplate.recordedSqls().size());
        assertTrue(jdbcTemplate.recordedSqls().get(1).contains("INSERT INTO meta.metadata_change_history"));
    }

    @Test
    void withholdsBlockedAiAttributesEndToEnd() throws Exception {
        UUID objectId = UUID.fromString("00000000-0000-0000-0000-000000000101");
        UUID connectionId = UUID.fromString("00000000-0000-0000-0000-000000000201");
        UUID attributeId = UUID.fromString("00000000-0000-0000-0000-000000000103");
        jdbcTemplate.setResponses(List.of(
                List.of(objectRow(objectId, connectionId)),
                List.of(blockedAiAttributeRow(attributeId, objectId)),
                List.of(attributeGrantRow())
        ));

        mockMvc.perform(get("/api/objects/{object_id}", objectId)
                        .queryParam("client_id", "client-a")
                        .header("X-Actor-Id", "assistant-1")
                        .header("X-Role-Cd", "AI_AGENT")
                        .header("X-Purpose-Cd", "AI_ASSIST"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.object_id").value(objectId.toString()))
                .andExpect(jsonPath("$.attributes").isEmpty());

        assertEquals(4, jdbcTemplate.recordedSqls().size());
        assertTrue(jdbcTemplate.recordedSqls().get(2).contains("FROM meta.attribute_access_grant"));
        assertTrue(jdbcTemplate.recordedSqls().get(3).contains("INSERT INTO meta.metadata_change_history"));
        assertEquals("assistant-1", jdbcTemplate.recordedParameters().get(3).get("changed_by"));
        assertTrue(((String) jdbcTemplate.recordedParameters().get(3).get("change_reason_txt")).contains("withheld=1"));
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
        row.put("data_classification_cd", "CONFIDENTIAL");
        row.put("pii_flg", true);
        row.put("confidential_flg", true);
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
        row.put("data_classification_cd", "RESTRICTED");
        row.put("pii_flg", true);
        row.put("confidential_flg", true);
        row.put("masking_policy_cd", "MASK_FULL");
        row.put("mnpi_flg", false);
        row.put("csi_flg", false);
        row.put("ai_exposure_cd", "RESTRICTED");
        row.put("pk_flg", true);
        row.put("fk_flg", false);
        row.put("nullable_flg", false);
        row.put("created_ts", OffsetDateTime.parse("2026-06-16T10:15:30+05:30"));
        row.put("created_by", "producer");
        row.put("updated_ts", OffsetDateTime.parse("2026-06-17T10:15:30+05:30"));
        row.put("updated_by", "platform");
        return row;
    }

    private static Map<String, Object> attributeGrantRow() {
        Map<String, Object> row = new HashMap<>();
        row.put("id", 10L);
        row.put("client_id", "client-a");
        row.put("schema_cd", "meta");
        row.put("object_cd", "GL_BALANCE");
        row.put("attribute_cd", "AMOUNT");
        row.put("role_cd", "FINANCE");
        row.put("purpose_cd", "REPORTING");
        row.put("grant_scope_cd", "READ");
        row.put("grant_status_cd", "ACTIVE");
        row.put("approved_by", "approver");
        row.put("approved_ts", OffsetDateTime.parse("2026-06-18T12:00:00Z"));
        row.put("created_ts", OffsetDateTime.parse("2026-06-18T11:00:00Z"));
        row.put("created_by", "approver");
        row.put("updated_ts", null);
        row.put("updated_by", null);
        return row;
    }

    private static Map<String, Object> blockedAiAttributeRow(UUID attributeId, UUID objectId) {
        Map<String, Object> row = attributeRow(attributeId, objectId);
        row.put("attribute_cd", "ACCOUNT_NO");
        row.put("attribute_nm", "Account Number");
        row.put("effective_attribute_nm", "Account Number");
        row.put("data_classification_cd", "CONFIDENTIAL");
        row.put("pii_flg", true);
        row.put("confidential_flg", true);
        row.put("masking_policy_cd", "MASK_FULL");
        row.put("mnpi_flg", false);
        row.put("csi_flg", false);
        row.put("ai_exposure_cd", "BLOCKED");
        return row;
    }

    @TestConfiguration
    static class ObjectExposureWireThroughTestConfiguration {

        @Bean
        RecordingNamedParameterJdbcTemplate recordingNamedParameterJdbcTemplate() {
            return new RecordingNamedParameterJdbcTemplate();
        }

        @Bean
        RecordingObjectExposurePolicyClient recordingObjectExposurePolicyClient() {
            return new RecordingObjectExposurePolicyClient();
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

        @Override
        public int update(String sql, SqlParameterSource paramSource) {
            recordedSqls.add(sql);
            if (paramSource instanceof MapSqlParameterSource source) {
                recordedParameters.add(new HashMap<>(source.getValues()));
            }
            return 1;
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
                    throw new UnsupportedOperationException("No real JDBC connection required for tests");
                }

                @Override
                public Connection getConnection(String username, String password) {
                    return getConnection();
                }
            };
        }
    }

    static final class RecordingObjectExposurePolicyClient implements ObjectExposurePolicyClient {

        private final List<ObjectExposureAccessPolicyRequestDto> accessRequests = new ArrayList<>();

        void reset() {
            accessRequests.clear();
        }

        List<ObjectExposureAccessPolicyRequestDto> accessRequests() {
            return accessRequests;
        }

        @Override
        public ObjectExposurePolicyDecisionDto evaluateAccess(ObjectExposureAccessPolicyRequestDto request) {
            accessRequests.add(request);
            if (!sameTenant(request)) {
                return new ObjectExposurePolicyDecisionDto(false, "POL-AC-001", "Cross-tenant access denied");
            }
            if (missingNeedToKnow(request)) {
                return new ObjectExposurePolicyDecisionDto(false, "POL-AC-001", "Need-to-know denied");
            }
            if (request.attribute_cd() != null && !hasActiveReadGrant(request)) {
                return new ObjectExposurePolicyDecisionDto(false, "POL-AC-001", "Need-to-know denied");
            }
            return new ObjectExposurePolicyDecisionDto(true, null, null);
        }

        @Override
        public ObjectExposureClassificationPolicyDecisionDto evaluateClassification(ObjectExposureClassificationPolicyRequestDto request) {
            if (isAiPrincipal(request) && "BLOCKED".equals(request.ai_exposure_cd())) {
                return new ObjectExposureClassificationPolicyDecisionDto(
                        false,
                        false,
                        true,
                        null,
                        List.of(),
                        "POL-DC-001",
                        "AI exposure is blocked"
                );
            }
            if (request.attribute_cd() != null
                    && "RESTRICTED".equals(request.attribute_data_classification_cd())
                    && request.masking_policy_cd() != null
                    && !request.masking_policy_cd().isBlank()) {
                return new ObjectExposureClassificationPolicyDecisionDto(
                        true,
                        true,
                        false,
                        "REDACTED",
                        List.of("attribute_nm", "taxonomy_cd", "taxonomy_source_cd", "taxonomy_jurisdiction_cd"),
                        "POL-DC-001",
                        "Classification requires masked exposure"
                );
            }
            return new ObjectExposureClassificationPolicyDecisionDto(true, false, false, null, List.of(), null, null);
        }

        private boolean sameTenant(ObjectExposureAccessPolicyRequestDto request) {
            return request.client_id() != null && request.client_id().equals(request.object_client_id());
        }

        private boolean missingNeedToKnow(ObjectExposureAccessPolicyRequestDto request) {
            return request.role_cd() == null
                    || request.role_cd().isBlank()
                    || request.purpose_cd() == null
                    || request.purpose_cd().isBlank();
        }

        private boolean hasActiveReadGrant(ObjectExposureAccessPolicyRequestDto request) {
            return request.grant_scope_cds().stream().anyMatch("READ"::equals)
                    && request.grant_status_cds().stream().anyMatch("ACTIVE"::equals);
        }

        private boolean isAiPrincipal(ObjectExposureClassificationPolicyRequestDto request) {
            return containsAi(request.role_cd()) || containsAi(request.purpose_cd());
        }

        private boolean containsAi(String value) {
            return value != null && value.toUpperCase().contains("AI");
        }
    }

    private static DataSource noOpDataSource() {
        return new AbstractDataSource() {
            @Override
            public Connection getConnection() {
                throw new UnsupportedOperationException("No real JDBC connection required for tests");
            }

            @Override
            public Connection getConnection(String username, String password) {
                return getConnection();
            }
        };
    }
}
