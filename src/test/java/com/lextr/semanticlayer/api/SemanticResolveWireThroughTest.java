package com.lextr.semanticlayer.api;

import com.lextr.semanticlayer.dto.ObjectExposureClassificationPolicyDecisionDto;
import com.lextr.semanticlayer.dto.ObjectExposureClassificationPolicyRequestDto;
import com.lextr.semanticlayer.dto.ObjectExposurePolicyDecisionDto;
import com.lextr.semanticlayer.dto.SemanticResolvePolicyRequestDto;
import com.lextr.semanticlayer.dto.SemanticResolveRequestDto;
import com.lextr.semanticlayer.service.ObjectExposurePolicyClient;
import com.lextr.semanticlayer.service.SemanticResolvePolicyClient;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringJUnitWebConfig(classes = {
        com.lextr.semanticlayer.SemanticLayerApplication.class,
        SemanticResolveWireThroughTest.SemanticResolveWireThroughTestConfiguration.class
})
@TestExecutionListeners(
        listeners = {
                DependencyInjectionTestExecutionListener.class,
                DirtiesContextBeforeModesTestExecutionListener.class,
                DirtiesContextTestExecutionListener.class
        },
        mergeMode = TestExecutionListeners.MergeMode.REPLACE_DEFAULTS
)
class SemanticResolveWireThroughTest {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private RecordingNamedParameterJdbcTemplate jdbcTemplate;

    @Autowired
    private RecordingSemanticResolvePolicyClient semanticResolvePolicyClient;

    @Autowired
    private RecordingObjectExposurePolicyClient objectExposurePolicyClient;

    @BeforeEach
    void resetDependencies() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        jdbcTemplate.reset();
        semanticResolvePolicyClient.allow();
        objectExposurePolicyClient.reset();
        objectExposurePolicyClient.maskObject("REDACTED");
        objectExposurePolicyClient.maskAttribute("LEDGER_ID", "REDACTED");
        objectExposurePolicyClient.withholdAttribute("ACCOUNT_NO");
    }

    @Test
    void resolvesAttributeListEndToEndWithMaskingAndAudit() throws Exception {
        UUID objectId = UUID.fromString("00000000-0000-0000-0000-000000000101");
        UUID connectionId = UUID.fromString("00000000-0000-0000-0000-000000000201");
        jdbcTemplate.setResponses(List.of(
                List.of(objectRow(objectId, connectionId)),
                List.of(
                        logicalResolutionRow(null, null, null, "client-a", "meta", "ledger", "LEDGER_ID", "Ledger Identifier Override", "ledger_id", "ledger_source", "POSTGRES", "NUMBER"),
                        logicalResolutionRow(null, null, null, "client-a", "meta", "ledger", "ACCOUNT_NO", "Account Number", "account_no", "ledger_source", "POSTGRES", "STRING")
                ),
                List.of(
                        attributeRow(objectId, "LEDGER_ID", "Ledger Identifier", "Ledger Identifier Override"),
                        attributeRow(objectId, "ACCOUNT_NO", "Account Number", "Account Number")
                )
        ));

        mockMvc.perform(post("/api/semantic/resolve")
                        .header("X-Actor-Id", "engine-1")
                        .header("X-Role-Cd", "ENGINE")
                        .header("X-Purpose-Cd", "RESOLUTION")
                        .contentType("application/json")
                        .content("""
                                {
                                  "client_id": "client-a",
                                  "schema_cd": "meta",
                                  "object_cd": "ledger",
                                  "logical_attribute_cd": ["LEDGER_ID", "ACCOUNT_NO"]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].logical_attribute_cd").value("LEDGER_ID"))
                .andExpect(jsonPath("$[0].effective_logical_attribute_nm").value("REDACTED"))
                .andExpect(jsonPath("$[0].physical_attribute_nm").value("ledger_id"))
                .andExpect(jsonPath("$[0].source_object_nm").value("REDACTED"))
                .andExpect(jsonPath("$[0].masked_flg").value(true));

        assertEquals(3, jdbcTemplate.recordedSqls().size());
        assertTrue(jdbcTemplate.recordedSqls().get(0).contains("FROM meta.object_catalog"));
        assertTrue(jdbcTemplate.recordedSqls().get(1).contains("FROM meta.object_catalog o"));
        assertTrue(jdbcTemplate.recordedSqls().get(2).contains("FROM meta.attribute_catalog a"));
        assertEquals(1, jdbcTemplate.recordedUpdates().size());
        assertTrue(jdbcTemplate.recordedUpdates().get(0).contains("INSERT INTO meta.metadata_change_history"));
        assertEquals(1, semanticResolvePolicyClient.recordedRequests().size());
        assertEquals("SEMANTIC", semanticResolvePolicyClient.recordedRequests().get(0).request_type_cd());
        assertEquals(3, objectExposurePolicyClient.recordedRequests().size());
        assertTrue(objectExposurePolicyClient.recordedRequests().get(0).attribute_cd() == null);
        assertEquals("LEDGER_ID", objectExposurePolicyClient.recordedRequests().get(1).attribute_cd());
        assertEquals("ACCOUNT_NO", objectExposurePolicyClient.recordedRequests().get(2).attribute_cd());
        assertTrue(((String) jdbcTemplate.recordedUpdateParameters().get(0).get("change_reason_txt")).contains("masked=1"));
        assertTrue(((String) jdbcTemplate.recordedUpdateParameters().get(0).get("change_reason_txt")).contains("withheld=1"));
    }

    @Test
    void resolvesOutboundGrainEndToEndWithMaskingAndAudit() throws Exception {
        UUID objectId = UUID.fromString("00000000-0000-0000-0000-000000000101");
        UUID connectionId = UUID.fromString("00000000-0000-0000-0000-000000000201");
        jdbcTemplate.setResponses(List.of(
                List.of(outboundRow()),
                List.of(
                        logicalResolutionRow(101L, "OB-101", 1, "client-a", "meta", "ledger", "LEDGER_ID", "Ledger Identifier Override", "ledger_id", "ledger_source", "POSTGRES", "NUMBER")
                ),
                List.of(objectRow(objectId, connectionId)),
                List.of(attributeRow(objectId, "LEDGER_ID", "Ledger Identifier", "Ledger Identifier Override"))
        ));

        mockMvc.perform(get("/api/consumption/101/resolve")
                        .queryParam("client_id", "client-a")
                        .header("X-Actor-Id", "engine-1")
                        .header("X-Role-Cd", "ENGINE")
                        .header("X-Purpose-Cd", "RESOLUTION"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].outbound_id").value(101))
                .andExpect(jsonPath("$[0].outbound_cd").value("OB-101"))
                .andExpect(jsonPath("$[0].effective_logical_attribute_nm").value("REDACTED"))
                .andExpect(jsonPath("$[0].source_object_nm").value("REDACTED"))
                .andExpect(jsonPath("$[0].masked_flg").value(true));

        assertEquals(4, jdbcTemplate.recordedSqls().size());
        assertTrue(jdbcTemplate.recordedSqls().get(0).contains("FROM meta.consumption_outbound co"));
        assertTrue(jdbcTemplate.recordedSqls().get(1).contains("FROM meta.consumption_outbound co"));
        assertTrue(jdbcTemplate.recordedSqls().get(2).contains("FROM meta.object_catalog"));
        assertTrue(jdbcTemplate.recordedSqls().get(3).contains("FROM meta.attribute_catalog"));
        assertEquals(1, jdbcTemplate.recordedUpdates().size());
        assertTrue(jdbcTemplate.recordedUpdates().get(0).contains("INSERT INTO meta.metadata_change_history"));
        assertEquals(1, semanticResolvePolicyClient.recordedRequests().size());
        assertEquals("CONSUMPTION", semanticResolvePolicyClient.recordedRequests().get(0).request_type_cd());
        assertEquals(2, objectExposurePolicyClient.recordedRequests().size());
        assertTrue(objectExposurePolicyClient.recordedRequests().get(0).attribute_cd() == null);
        assertEquals("LEDGER_ID", objectExposurePolicyClient.recordedRequests().get(1).attribute_cd());
        assertTrue(((String) jdbcTemplate.recordedUpdateParameters().get(0).get("change_reason_txt")).contains("masked=1"));
        assertTrue(((String) jdbcTemplate.recordedUpdateParameters().get(0).get("change_reason_txt")).contains("withheld=0"));
    }

    @Test
    void deniesNonEnginePrincipalWithoutResolutionSql() throws Exception {
        UUID objectId = UUID.fromString("00000000-0000-0000-0000-000000000101");
        UUID connectionId = UUID.fromString("00000000-0000-0000-0000-000000000201");
        jdbcTemplate.setResponses(List.of(List.of(objectRow(objectId, connectionId))));
        semanticResolvePolicyClient.deny(
                "POL-RS-001",
                "Resolve denied for non-engine principal role ANALYST and purpose RESOLUTION"
        );

        mockMvc.perform(post("/api/semantic/resolve")
                        .header("X-Actor-Id", "human-1")
                        .header("X-Role-Cd", "ANALYST")
                        .header("X-Purpose-Cd", "RESOLUTION")
                        .contentType("application/json")
                        .content("""
                                {
                                  "client_id": "client-a",
                                  "schema_cd": "meta",
                                  "object_cd": "ledger",
                                  "logical_attribute_cd": ["LEDGER_ID"]
                                }
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("POL-RS-001"))
                .andExpect(jsonPath("$.message").value("Resolve denied for non-engine principal role ANALYST and purpose RESOLUTION"));

        assertEquals(1, jdbcTemplate.recordedSqls().size());
        assertTrue(jdbcTemplate.recordedSqls().get(0).contains("FROM meta.object_catalog"));
        assertEquals(1, jdbcTemplate.recordedUpdates().size());
        assertTrue(jdbcTemplate.recordedUpdates().get(0).contains("INSERT INTO meta.metadata_change_history"));
        assertEquals(1, semanticResolvePolicyClient.recordedRequests().size());
        assertEquals("ANALYST", semanticResolvePolicyClient.recordedRequests().get(0).role_cd());
    }

    private static Map<String, Object> objectRow(UUID objectId, UUID connectionId) {
        Map<String, Object> row = new HashMap<>();
        row.put("object_id", objectId);
        row.put("client_id", "client-a");
        row.put("object_cd", "ledger");
        row.put("object_nm", "Ledger");
        row.put("effective_object_nm", "Ledger Override");
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

    private static Map<String, Object> outboundRow() {
        Map<String, Object> row = new HashMap<>();
        row.put("id", 101L);
        row.put("client_id", "client-a");
        row.put("layer_cd", "LAYER-01");
        row.put("object_id", 202L);
        row.put("outbound_cd", "OB-101");
        row.put("outbound_nm", "Outbound 101");
        row.put("structure_type_cd", "TECHNICAL");
        row.put("description_txt", "Consumption outbound");
        row.put("attributes_jsonb", "[\"LEDGER_ID\"]");
        row.put("sdlc_status_cd", "DEV");
        row.put("version_nbr", 1);
        row.put("created_ts", OffsetDateTime.parse("2026-06-16T10:15:30+05:30"));
        row.put("created_by", "producer");
        row.put("updated_ts", OffsetDateTime.parse("2026-06-17T10:15:30+05:30"));
        row.put("updated_by", "platform");
        return row;
    }

    private static Map<String, Object> attributeRow(UUID objectId,
                                                    String attributeCode,
                                                    String attributeName,
                                                    String effectiveAttributeName) {
        Map<String, Object> row = new HashMap<>();
        row.put("attribute_id", UUID.nameUUIDFromBytes((attributeCode + "-id").getBytes()));
        row.put("object_id", objectId);
        row.put("client_id", "client-a");
        row.put("attribute_cd", attributeCode);
        row.put("attribute_nm", attributeName);
        row.put("effective_attribute_nm", effectiveAttributeName);
        row.put("data_type_cd", "STRING");
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
        row.put("pk_flg", "LEDGER_ID".equals(attributeCode));
        row.put("fk_flg", false);
        row.put("nullable_flg", false);
        row.put("created_ts", OffsetDateTime.parse("2026-06-16T10:15:30+05:30"));
        row.put("created_by", "producer");
        row.put("updated_ts", OffsetDateTime.parse("2026-06-17T10:15:30+05:30"));
        row.put("updated_by", "platform");
        return row;
    }

    private static Map<String, Object> logicalResolutionRow(Long outboundId,
                                                            String outboundCd,
                                                            Integer grainLevelNbr,
                                                            String clientId,
                                                            String schemaCd,
                                                            String objectCd,
                                                            String logicalAttributeCd,
                                                            String effectiveLogicalAttributeNm,
                                                            String physicalAttributeNm,
                                                            String sourceObjectNm,
                                                            String engineCd,
                                                            String dataTypeCd) {
        Map<String, Object> row = new HashMap<>();
        row.put("outbound_id", outboundId);
        row.put("outbound_cd", outboundCd);
        row.put("grain_level_nbr", grainLevelNbr);
        row.put("client_id", clientId);
        row.put("schema_cd", schemaCd);
        row.put("object_cd", objectCd);
        row.put("logical_attribute_cd", logicalAttributeCd);
        row.put("effective_logical_attribute_nm", effectiveLogicalAttributeNm);
        row.put("physical_attribute_nm", physicalAttributeNm);
        row.put("source_object_nm", sourceObjectNm);
        row.put("engine_cd", engineCd);
        row.put("data_type_cd", dataTypeCd);
        return row;
    }

    @TestConfiguration
    static class SemanticResolveWireThroughTestConfiguration {

        @Bean
        RecordingNamedParameterJdbcTemplate recordingNamedParameterJdbcTemplate() {
            return new RecordingNamedParameterJdbcTemplate();
        }

        @Bean
        RecordingSemanticResolvePolicyClient recordingSemanticResolvePolicyClient() {
            return new RecordingSemanticResolvePolicyClient();
        }

        @Bean
        RecordingObjectExposurePolicyClient recordingObjectExposurePolicyClient() {
            return new RecordingObjectExposurePolicyClient();
        }
    }

    static final class RecordingSemanticResolvePolicyClient implements SemanticResolvePolicyClient {

        private final List<SemanticResolvePolicyRequestDto> recordedRequests = new ArrayList<>();
        private ObjectExposurePolicyDecisionDto decision = new ObjectExposurePolicyDecisionDto(true, null, null);

        void allow() {
            recordedRequests.clear();
            decision = new ObjectExposurePolicyDecisionDto(true, null, null);
        }

        void deny(String code, String message) {
            recordedRequests.clear();
            decision = new ObjectExposurePolicyDecisionDto(false, code, message);
        }

        List<SemanticResolvePolicyRequestDto> recordedRequests() {
            return recordedRequests;
        }

        @Override
        public ObjectExposurePolicyDecisionDto evaluateAccess(SemanticResolvePolicyRequestDto request) {
            recordedRequests.add(request);
            return decision;
        }
    }

    static final class RecordingObjectExposurePolicyClient implements ObjectExposurePolicyClient {

        private final List<ObjectExposureClassificationPolicyRequestDto> recordedRequests = new ArrayList<>();
        private ObjectExposureClassificationPolicyDecisionDto objectDecision = new ObjectExposureClassificationPolicyDecisionDto(true, false, false, null, List.of(), null, null);
        private final Map<String, ObjectExposureClassificationPolicyDecisionDto> attributeDecisions = new LinkedHashMap<>();

        void reset() {
            recordedRequests.clear();
            objectDecision = new ObjectExposureClassificationPolicyDecisionDto(true, false, false, null, List.of(), null, null);
            attributeDecisions.clear();
        }

        void maskObject(String maskValue) {
            objectDecision = new ObjectExposureClassificationPolicyDecisionDto(true, true, false, maskValue, List.of("object_nm"), null, null);
        }

        void maskAttribute(String attributeCode, String maskValue) {
            attributeDecisions.put(attributeCode, new ObjectExposureClassificationPolicyDecisionDto(true, true, false, maskValue, List.of("attribute_nm"), null, null));
        }

        void withholdAttribute(String attributeCode) {
            attributeDecisions.put(attributeCode, new ObjectExposureClassificationPolicyDecisionDto(true, false, true, null, List.of(), "POL-DC-001", "Attribute withheld"));
        }

        List<ObjectExposureClassificationPolicyRequestDto> recordedRequests() {
            return recordedRequests;
        }

        @Override
        public ObjectExposurePolicyDecisionDto evaluateAccess(com.lextr.semanticlayer.dto.ObjectExposureAccessPolicyRequestDto request) {
            return new ObjectExposurePolicyDecisionDto(true, null, null);
        }

        @Override
        public ObjectExposureClassificationPolicyDecisionDto evaluateClassification(ObjectExposureClassificationPolicyRequestDto request) {
            recordedRequests.add(request);
            if (request.attribute_cd() == null) {
                return objectDecision;
            }
            return attributeDecisions.getOrDefault(
                    request.attribute_cd(),
                    new ObjectExposureClassificationPolicyDecisionDto(true, false, false, null, List.of(), null, null)
            );
        }
    }

    static final class RecordingNamedParameterJdbcTemplate extends NamedParameterJdbcTemplate {

        private List<List<Map<String, Object>>> responses = List.of();
        private final List<String> recordedSqls = new ArrayList<>();
        private final List<Map<String, Object>> recordedParameters = new ArrayList<>();
        private final List<String> recordedUpdates = new ArrayList<>();
        private final List<Map<String, Object>> recordedUpdateParameters = new ArrayList<>();

        RecordingNamedParameterJdbcTemplate() {
            super(noOpDataSource());
        }

        void reset() {
            responses = List.of();
            recordedSqls.clear();
            recordedParameters.clear();
            recordedUpdates.clear();
            recordedUpdateParameters.clear();
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

        List<String> recordedUpdates() {
            return recordedUpdates;
        }

        List<Map<String, Object>> recordedUpdateParameters() {
            return recordedUpdateParameters;
        }

        @Override
        public <T> List<T> query(String sql, SqlParameterSource paramSource, RowMapper<T> rowMapper) {
            recordedSqls.add(sql);
            if (paramSource instanceof MapSqlParameterSource source) {
                recordedParameters.add(new HashMap<>(source.getValues()));
            }
            List<Map<String, Object>> rows = responses.isEmpty() ? List.of() : responses.remove(0);
            List<T> mappedRows = new ArrayList<>();
            for (int i = 0; i < rows.size(); i++) {
                mappedRows.add(mapRow(rowMapper, rows.get(i), i));
            }
            return mappedRows;
        }

        @Override
        public int update(String sql, SqlParameterSource paramSource) {
            recordedUpdates.add(sql);
            if (paramSource instanceof MapSqlParameterSource source) {
                recordedUpdateParameters.add(new HashMap<>(source.getValues()));
            }
            return 1;
        }

        private <T> T mapRow(RowMapper<T> rowMapper, Map<String, Object> row, int rowNum) {
            try {
                return rowMapper.mapRow(resultSet(row), rowNum);
            } catch (SQLException exception) {
                throw new RuntimeException(exception);
            }
        }

        private ResultSet resultSet(Map<String, Object> row) {
            return (ResultSet) Proxy.newProxyInstance(
                    ResultSet.class.getClassLoader(),
                    new Class[]{ResultSet.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "getString" -> valueAsString(row.get(args[0]));
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
                        default -> throw new UnsupportedOperationException(method.getName());
                    }
            );
        }

        private String valueAsString(Object value) {
            return value == null ? null : String.valueOf(value);
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
