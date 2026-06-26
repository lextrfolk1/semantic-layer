package com.lextr.semanticlayer.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lextr.semanticlayer.dao.ObjectExposureReadDao;
import com.lextr.semanticlayer.dto.ObjectExposureAccessPolicyRequestDto;
import com.lextr.semanticlayer.dto.ObjectExposureClassificationPolicyDecisionDto;
import com.lextr.semanticlayer.dto.ObjectExposureClassificationPolicyRequestDto;
import com.lextr.semanticlayer.dto.ObjectRegistrationRequestDto;
import com.lextr.semanticlayer.dto.ObjectRegistrationResponseDto;
import com.lextr.semanticlayer.dto.ObjectExposurePolicyDecisionDto;
import com.lextr.semanticlayer.model.AttributeAccessGrantRecord;
import com.lextr.semanticlayer.model.AttributeExposureRecord;
import com.lextr.semanticlayer.model.ObjectExposureAccessAuditWriteRequest;
import com.lextr.semanticlayer.model.ObjectExposureRecord;
import com.lextr.semanticlayer.service.ObjectExposurePolicyClient;
import com.lextr.semanticlayer.service.ObjectExposureReadService;
import com.lextr.semanticlayer.service.ObjectRegistrationService;
import com.lextr.semanticlayer.service.impl.ObjectExposureReadServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ObjectExposureReadControllerTest {

    @Test
    void appliesObjectFiltersClientScopingAndGatewayHeaders() throws Exception {
        UUID objectId = UUID.fromString("00000000-0000-0000-0000-000000000101");
        RecordingObjectExposureReadDao dao = new RecordingObjectExposureReadDao();
        dao.objectsByClient = Map.of("client-a", List.of(objectRow(objectId)));
        RecordingObjectExposurePolicyClient policyClient = new RecordingObjectExposurePolicyClient();
        MockMvc mockMvc = mockMvc(dao, policyClient);

        mockMvc.perform(get("/api/objects")
                        .queryParam("client_id", "client-a")
                        .queryParam("schema_cd", "meta")
                        .queryParam("lifecycle_status_cd", "ACTIVE")
                        .header("X-Actor-Id", "analyst-1")
                        .header("X-Role-Cd", "FINANCE")
                        .header("X-Purpose-Cd", "REPORTING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].object_id").value(objectId.toString()))
                .andExpect(jsonPath("$[0].object_nm").value("GL Balance Override"))
                .andExpect(jsonPath("$[0].lifecycle_status_cd").value("ACTIVE"));

        assertEquals("client-a", dao.lastClientId);
        assertEquals("meta", dao.lastSchemaCode);
        assertEquals("ACTIVE", dao.lastLifecycleStatusCode);
        assertEquals("analyst-1", policyClient.accessRequests.get(0).actor_id());
        assertEquals("FINANCE", policyClient.accessRequests.get(0).role_cd());
        assertEquals("REPORTING", policyClient.accessRequests.get(0).purpose_cd());
    }

    @Test
    void returnsMaskedObjectDetailWithinClientScope() throws Exception {
        UUID objectId = UUID.fromString("00000000-0000-0000-0000-000000000101");
        RecordingObjectExposureReadDao dao = new RecordingObjectExposureReadDao();
        dao.objectsByClient = Map.of("client-a", List.of(objectRow(objectId)));
        dao.attributesByObjectId = Map.of(
                objectId, List.of(attributeRow(UUID.fromString("00000000-0000-0000-0000-000000000102"), objectId))
        );
        dao.attributeAccessGrants = List.of(new AttributeAccessGrantRecord(
                10L,
                "client-a",
                "meta",
                "GL_BALANCE",
                "AMOUNT",
                "FINANCE",
                "REPORTING",
                "READ",
                "ACTIVE",
                "approver",
                OffsetDateTime.parse("2026-06-18T12:00:00Z"),
                OffsetDateTime.parse("2026-06-18T11:00:00Z"),
                "approver",
                null,
                null
        ));
        RecordingObjectExposurePolicyClient policyClient = new RecordingObjectExposurePolicyClient();
        policyClient.maskAttribute("AMOUNT");
        MockMvc mockMvc = mockMvc(dao, policyClient);

        mockMvc.perform(get("/api/objects/{object_id}", objectId)
                        .queryParam("client_id", "client-a")
                        .header("X-Actor-Id", "analyst-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.object_id").value(objectId.toString()))
                .andExpect(jsonPath("$.attributes[0].attribute_cd").value("AMOUNT"))
                .andExpect(jsonPath("$.attributes[0].attribute_nm").value("MASKED"))
                .andExpect(jsonPath("$.attributes[0].taxonomy_cd").value("MASKED"));

        assertEquals("client-a", dao.lastObjectClientId);
        assertEquals(objectId, dao.lastObjectId);
        assertEquals(1, dao.audits.size());
    }

    @Test
    void returnsUnprocessableEntityWhenCrossTenantPolicyDeniesObjectRead() throws Exception {
        UUID objectId = UUID.fromString("00000000-0000-0000-0000-000000000101");
        RecordingObjectExposureReadDao dao = new RecordingObjectExposureReadDao();
        dao.objectsByClient = Map.of("client-b", List.of(objectRow(objectId)));
        RecordingObjectExposurePolicyClient policyClient = new RecordingObjectExposurePolicyClient();
        policyClient.denyObject();
        MockMvc mockMvc = mockMvc(dao, policyClient);

        mockMvc.perform(get("/api/objects/{object_id}", objectId)
                        .queryParam("client_id", "client-b")
                        .header("X-Actor-Id", "analyst-2"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("POL-AC-001"))
                .andExpect(jsonPath("$.message").value("Cross-tenant access denied"));
    }

    @Test
    void returns404ForUnknownObjectIdWithinClientScope() throws Exception {
        MockMvc mockMvc = mockMvc(new RecordingObjectExposureReadDao(), new RecordingObjectExposurePolicyClient());

        mockMvc.perform(get("/api/objects/{object_id}", UUID.fromString("00000000-0000-0000-0000-000000000999"))
                        .queryParam("client_id", "client-a"))
                .andExpect(status().isNotFound());
    }

    private static MockMvc mockMvc(ObjectExposureReadDao dao, ObjectExposurePolicyClient policyClient) {
        ObjectExposureReadService readService = new ObjectExposureReadServiceImpl(dao, policyClient);
        ObjectRegistrationController controller = new ObjectRegistrationController(new NoOpObjectRegistrationService(), readService);
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        return MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ApiExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    private static ObjectExposureRecord objectRow(UUID objectId) {
        return new ObjectExposureRecord(
                objectId,
                "client-a",
                "GL_BALANCE",
                "GL Balance",
                "GL Balance Override",
                "TABLE",
                "meta",
                UUID.fromString("00000000-0000-0000-0000-000000000201"),
                "CONFIDENTIAL",
                true,
                true,
                "ACTIVE",
                OffsetDateTime.parse("2026-06-16T10:15:30+05:30"),
                "producer",
                null,
                null
        );
    }

    private static AttributeExposureRecord attributeRow(UUID attributeId, UUID objectId) {
        return new AttributeExposureRecord(
                attributeId,
                objectId,
                "client-a",
                "AMOUNT",
                "Amount",
                "Amount Override",
                "DECIMAL",
                "MDRM12345678",
                "MDRM",
                "US",
                "RESTRICTED",
                true,
                true,
                "MASK_FULL",
                false,
                false,
                "RESTRICTED",
                true,
                false,
                false,
                OffsetDateTime.parse("2026-06-16T10:15:30+05:30"),
                "producer",
                null,
                null
        );
    }

    private static final class RecordingObjectExposureReadDao implements ObjectExposureReadDao {

        private Map<String, List<ObjectExposureRecord>> objectsByClient = Map.of();
        private Map<UUID, List<AttributeExposureRecord>> attributesByObjectId = Map.of();
        private List<AttributeAccessGrantRecord> attributeAccessGrants = List.of();
        private final List<ObjectExposureAccessAuditWriteRequest> audits = new ArrayList<>();
        private String lastClientId;
        private String lastSchemaCode;
        private String lastLifecycleStatusCode;
        private String lastObjectClientId;
        private UUID lastObjectId;

        @Override
        public List<ObjectExposureRecord> findObjects(String clientId, String schemaCode, String lifecycleStatusCode) {
            lastClientId = clientId;
            lastSchemaCode = schemaCode;
            lastLifecycleStatusCode = lifecycleStatusCode;
            return objectsByClient.getOrDefault(clientId, List.of()).stream()
                    .filter(record -> schemaCode == null || schemaCode.equals(record.schema_cd()))
                    .filter(record -> lifecycleStatusCode == null || lifecycleStatusCode.equals(record.lifecycle_status_cd()))
                    .toList();
        }

        @Override
        public Optional<ObjectExposureRecord> findObject(String clientId, UUID objectId) {
            lastObjectClientId = clientId;
            lastObjectId = objectId;
            return objectsByClient.getOrDefault(clientId, List.of()).stream()
                    .filter(record -> objectId.equals(record.object_id()))
                    .findFirst();
        }

        @Override
        public Optional<ObjectExposureRecord> findObject(String schemaCode, String objectCode) {
            return objectsByClient.values().stream()
                    .flatMap(List::stream)
                    .filter(record -> schemaCode.equals(record.schema_cd()))
                    .filter(record -> objectCode.equals(record.object_cd()))
                    .findFirst();
        }

        @Override
        public List<AttributeExposureRecord> findAttributes(String clientId, UUID objectId) {
            return attributesByObjectId.getOrDefault(objectId, List.of()).stream()
                    .filter(record -> clientId.equals(record.client_id()))
                    .toList();
        }

        @Override
        public List<AttributeAccessGrantRecord> findAttributeAccessGrants(String clientId,
                                                                          String schemaCode,
                                                                          String objectCode,
                                                                          String attributeCode) {
            return attributeAccessGrants;
        }

        @Override
        public void insertAccessAudit(ObjectExposureAccessAuditWriteRequest request) {
            audits.add(request);
        }
    }

    private static final class RecordingObjectExposurePolicyClient implements ObjectExposurePolicyClient {

        private final List<ObjectExposureAccessPolicyRequestDto> accessRequests = new ArrayList<>();
        private boolean denyObject;
        private String maskedAttributeCode;

        void denyObject() {
            denyObject = true;
        }

        void maskAttribute(String attributeCode) {
            maskedAttributeCode = attributeCode;
        }

        @Override
        public ObjectExposurePolicyDecisionDto evaluateAccess(ObjectExposureAccessPolicyRequestDto request) {
            accessRequests.add(request);
            if (denyObject && request.attribute_cd() == null) {
                return new ObjectExposurePolicyDecisionDto(false, "POL-AC-001", "Cross-tenant access denied");
            }
            return new ObjectExposurePolicyDecisionDto(true, null, null);
        }

        @Override
        public ObjectExposureClassificationPolicyDecisionDto evaluateClassification(ObjectExposureClassificationPolicyRequestDto request) {
            if (request.attribute_cd() != null && request.attribute_cd().equals(maskedAttributeCode)) {
                return new ObjectExposureClassificationPolicyDecisionDto(
                        true,
                        true,
                        false,
                        "MASKED",
                        List.of("attribute_nm", "taxonomy_cd"),
                        null,
                        null
                );
            }
            return new ObjectExposureClassificationPolicyDecisionDto(true, false, false, null, List.of(), null, null);
        }
    }

    private static final class NoOpObjectRegistrationService implements ObjectRegistrationService {

        @Override
        public ObjectRegistrationResponseDto registerObject(ObjectRegistrationRequestDto request) {
            throw new UnsupportedOperationException("Not used in read tests");
        }
    }
}
