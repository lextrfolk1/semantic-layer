package com.lextr.semanticlayer.service.impl;

import com.lextr.semanticlayer.dao.ConsumptionDao;
import com.lextr.semanticlayer.dao.LogicalPhysicalResolutionDao;
import com.lextr.semanticlayer.dao.ObjectExposureReadDao;
import com.lextr.semanticlayer.dto.LogicalPhysicalResolutionDto;
import com.lextr.semanticlayer.dto.ObjectExposureClassificationPolicyDecisionDto;
import com.lextr.semanticlayer.dto.ObjectExposureClassificationPolicyRequestDto;
import com.lextr.semanticlayer.dto.ObjectExposurePolicyDecisionDto;
import com.lextr.semanticlayer.dto.SemanticResolvePolicyRequestDto;
import com.lextr.semanticlayer.dto.SemanticResolveRequestDto;
import com.lextr.semanticlayer.exception.PolicyViolationException;
import com.lextr.semanticlayer.exception.RegistryResourceNotFoundException;
import com.lextr.semanticlayer.model.AttributeExposureRecord;
import com.lextr.semanticlayer.model.ConsumptionOutboundRecord;
import com.lextr.semanticlayer.model.LogicalPhysicalResolutionRecord;
import com.lextr.semanticlayer.model.ObjectExposureAccessAuditWriteRequest;
import com.lextr.semanticlayer.model.ObjectExposureRecord;
import com.lextr.semanticlayer.service.ObjectExposurePolicyClient;
import com.lextr.semanticlayer.service.SemanticResolvePolicyClient;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SemanticResolveServiceImplTest {

    @Test
    void masksAndWithholdsSemanticResolveRowsAndWritesAudit() {
        UUID objectId = UUID.fromString("00000000-0000-0000-0000-000000000101");
        RecordingObjectExposureReadDao objectDao = new RecordingObjectExposureReadDao();
        objectDao.object = objectRecord(objectId, "client-a");
        objectDao.attributesByObjectId.put(objectId, List.of(
                attributeRecord(objectId, "LEDGER_ID", "ledger_id"),
                attributeRecord(objectId, "ACCOUNT_NO", "account_no")
        ));

        RecordingLogicalPhysicalResolutionDao resolutionDao = new RecordingLogicalPhysicalResolutionDao();
        resolutionDao.attributeResults = List.of(
                record(null, null, null, "client-a", "meta", "ledger", "LEDGER_ID", "Ledger Identifier Override", "ledger_id", "ledger_source", "POSTGRES", "NUMBER"),
                record(null, null, null, "client-a", "meta", "ledger", "ACCOUNT_NO", "Account Number", "account_no", "ledger_source", "POSTGRES", "STRING")
        );
        RecordingSemanticResolvePolicyClient policyClient = new RecordingSemanticResolvePolicyClient();
        RecordingObjectExposurePolicyClient classificationClient = new RecordingObjectExposurePolicyClient();
        classificationClient.maskObject("REDACTED");
        classificationClient.maskAttribute("LEDGER_ID", "REDACTED");
        classificationClient.withholdAttribute("ACCOUNT_NO");

        SemanticResolveServiceImpl service = new SemanticResolveServiceImpl(
                objectDao,
                new RecordingConsumptionDao(),
                resolutionDao,
                policyClient,
                classificationClient
        );

        List<LogicalPhysicalResolutionDto> results = service.resolveAttributes(
                new SemanticResolveRequestDto("client-a", "meta", "ledger", List.of("LEDGER_ID", "ACCOUNT_NO")),
                "engine-1",
                "ENGINE",
                "RESOLUTION"
        );

        assertEquals(1, results.size());
        assertEquals("LEDGER_ID", results.get(0).logical_attribute_cd());
        assertEquals("REDACTED", results.get(0).effective_logical_attribute_nm());
        assertEquals("ledger_id", results.get(0).physical_attribute_nm());
        assertEquals("REDACTED", results.get(0).source_object_nm());
        assertTrue(results.get(0).masked_flg());
        assertEquals(1, policyClient.requests.size());
        assertEquals("SEMANTIC", policyClient.requests.get(0).request_type_cd());
        assertEquals(3, classificationClient.requests.size());
        assertEquals(1, objectDao.audits.size());
        assertTrue(objectDao.audits.get(0).change_reason_txt().contains("masked=1"));
        assertTrue(objectDao.audits.get(0).change_reason_txt().contains("withheld=1"));
        assertEquals("meta.ledger", objectDao.audits.get(0).entity_ref());
    }

    @Test
    void deniesCrossTenantSemanticResolve() {
        UUID objectId = UUID.fromString("00000000-0000-0000-0000-000000000101");
        RecordingObjectExposureReadDao objectDao = new RecordingObjectExposureReadDao();
        objectDao.object = objectRecord(objectId, "client-b");
        objectDao.attributesByObjectId.put(objectId, List.of(attributeRecord(objectId, "LEDGER_ID", "ledger_id")));

        SemanticResolveServiceImpl service = new SemanticResolveServiceImpl(
                objectDao,
                new RecordingConsumptionDao(),
                new RecordingLogicalPhysicalResolutionDao(),
                new RecordingSemanticResolvePolicyClient(),
                new RecordingObjectExposurePolicyClient()
        );

        PolicyViolationException exception = assertThrows(
                PolicyViolationException.class,
                () -> service.resolveAttributes(
                        new SemanticResolveRequestDto("client-a", "meta", "ledger", List.of("LEDGER_ID")),
                        "engine-1",
                        "ENGINE",
                        "RESOLUTION"
                )
        );

        assertEquals("POL-RS-001", exception.code());
        assertTrue(objectDao.audits.get(0).change_reason_txt().contains("denied"));
    }

    @Test
    void deniesNonEnginePrincipalSemanticResolve() {
        UUID objectId = UUID.fromString("00000000-0000-0000-0000-000000000101");
        RecordingObjectExposureReadDao objectDao = new RecordingObjectExposureReadDao();
        objectDao.object = objectRecord(objectId, "client-a");
        objectDao.attributesByObjectId.put(objectId, List.of(attributeRecord(objectId, "LEDGER_ID", "ledger_id")));

        SemanticResolveServiceImpl service = new SemanticResolveServiceImpl(
                objectDao,
                new RecordingConsumptionDao(),
                new RecordingLogicalPhysicalResolutionDao(),
                new RecordingSemanticResolvePolicyClient(),
                new RecordingObjectExposurePolicyClient()
        );

        PolicyViolationException exception = assertThrows(
                PolicyViolationException.class,
                () -> service.resolveAttributes(
                        new SemanticResolveRequestDto("client-a", "meta", "ledger", List.of("LEDGER_ID")),
                        "human-1",
                        "ANALYST",
                        "RESOLUTION"
                )
        );

        assertEquals("POL-RS-001", exception.code());
        assertTrue(exception.getMessage().contains("non-engine principal"));
        assertTrue(objectDao.audits.get(0).change_reason_txt().contains("denied"));
    }

    @Test
    void resolvesConsumptionOutboundAndWritesAudit() {
        UUID objectId = UUID.fromString("00000000-0000-0000-0000-000000000101");
        RecordingObjectExposureReadDao objectDao = new RecordingObjectExposureReadDao();
        objectDao.object = objectRecord(objectId, "client-a");
        objectDao.attributesByObjectId.put(objectId, List.of(attributeRecord(objectId, "LEDGER_ID", "ledger_id")));

        RecordingLogicalPhysicalResolutionDao resolutionDao = new RecordingLogicalPhysicalResolutionDao();
        resolutionDao.outboundResults = List.of(
                record(101L, "OB-101", 1, "client-a", "meta", "ledger", "LEDGER_ID", "Ledger Identifier Override", "ledger_id", "ledger_source", "POSTGRES", "NUMBER")
        );

        RecordingConsumptionDao consumptionDao = new RecordingConsumptionDao();
        consumptionDao.exposure = outboundRecord(101L, objectId, "client-a");

        SemanticResolveServiceImpl service = new SemanticResolveServiceImpl(
                objectDao,
                consumptionDao,
                resolutionDao,
                new RecordingSemanticResolvePolicyClient(),
                new RecordingObjectExposurePolicyClient()
        );

        List<LogicalPhysicalResolutionDto> results = service.resolveOutboundGrain("client-a", "engine-1", "ENGINE", "RESOLUTION", 101L);

        assertEquals(1, results.size());
        assertEquals(101L, results.get(0).outbound_id());
        assertEquals("OB-101", results.get(0).outbound_cd());
        assertFalse(results.get(0).masked_flg());
        assertEquals(1, consumptionDao.unscopedRequests.size());
        assertEquals(1, objectDao.audits.size());
        assertTrue(objectDao.audits.get(0).change_reason_txt().contains("masked=0"));
        assertTrue(objectDao.audits.get(0).change_reason_txt().contains("withheld=0"));
    }

    @Test
    void returns404WhenOutboundIsMissing() {
        SemanticResolveServiceImpl service = new SemanticResolveServiceImpl(
                new RecordingObjectExposureReadDao(),
                new RecordingConsumptionDao(),
                new RecordingLogicalPhysicalResolutionDao(),
                new RecordingSemanticResolvePolicyClient(),
                new RecordingObjectExposurePolicyClient()
        );

        assertThrows(
                RegistryResourceNotFoundException.class,
                () -> service.resolveOutboundGrain("client-a", "engine-1", "ENGINE", "RESOLUTION", 101L)
        );
    }

    private static ObjectExposureRecord objectRecord(UUID objectId, String clientId) {
        return new ObjectExposureRecord(
                objectId,
                clientId,
                "ledger",
                "Ledger",
                "Ledger Override",
                "TABLE",
                "meta",
                UUID.fromString("00000000-0000-0000-0000-000000000201"),
                "CONFIDENTIAL",
                true,
                true,
                "ACTIVE",
                OffsetDateTime.parse("2026-06-16T10:15:30+05:30"),
                "producer",
                OffsetDateTime.parse("2026-06-17T10:15:30+05:30"),
                "platform"
        );
    }

    private static AttributeExposureRecord attributeRecord(UUID objectId, String attributeCode, String effectiveName) {
        return new AttributeExposureRecord(
                UUID.fromString("00000000-0000-0000-0000-000000000102"),
                objectId,
                "client-a",
                attributeCode,
                attributeCode.equals("LEDGER_ID") ? "Ledger Identifier" : "Account Number",
                effectiveName,
                "STRING",
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

    private static ConsumptionOutboundRecord outboundRecord(Long id, UUID objectId, String clientId) {
        return new ConsumptionOutboundRecord(
                id,
                clientId,
                "CL-01",
                202L,
                "OB-101",
                "Outbound 101",
                "TECHNICAL",
                "Technical exposure",
                List.of("LEDGER_ID"),
                "DEV",
                1,
                OffsetDateTime.parse("2026-06-20T10:15:30Z"),
                "owner",
                OffsetDateTime.parse("2026-06-20T10:15:30Z"),
                "owner"
        );
    }

    private static LogicalPhysicalResolutionRecord record(Long outboundId,
                                                          String outboundCode,
                                                          Integer grainLevel,
                                                          String clientId,
                                                          String schemaCode,
                                                          String objectCode,
                                                          String logicalAttributeCode,
                                                          String effectiveAttributeName,
                                                          String physicalAttributeName,
                                                          String sourceObjectName,
                                                          String engineCode,
                                                          String dataTypeCode) {
        return new LogicalPhysicalResolutionRecord(
                outboundId,
                outboundCode,
                grainLevel,
                clientId,
                schemaCode,
                objectCode,
                logicalAttributeCode,
                effectiveAttributeName,
                physicalAttributeName,
                sourceObjectName,
                engineCode,
                dataTypeCode
        );
    }

    private static final class RecordingObjectExposureReadDao implements ObjectExposureReadDao {

        private ObjectExposureRecord object;
        private final Map<UUID, List<AttributeExposureRecord>> attributesByObjectId = new LinkedHashMap<>();
        private final List<ObjectExposureAccessAuditWriteRequest> audits = new ArrayList<>();

        @Override
        public List<ObjectExposureRecord> findObjects(String clientId, String schemaCode, String lifecycleStatusCode) {
            return List.of();
        }

        @Override
        public Optional<ObjectExposureRecord> findObject(String clientId, UUID objectId) {
            return Optional.ofNullable(object).filter(record -> objectId.equals(record.object_id()));
        }

        @Override
        public Optional<ObjectExposureRecord> findObject(String schemaCode, String objectCode) {
            return Optional.ofNullable(object)
                    .filter(record -> schemaCode.equals(record.schema_cd()))
                    .filter(record -> objectCode.equals(record.object_cd()));
        }

        @Override
        public List<AttributeExposureRecord> findAttributes(String clientId, UUID objectId) {
            return attributesByObjectId.getOrDefault(objectId, List.of());
        }

        @Override
        public List<com.lextr.semanticlayer.model.AttributeAccessGrantRecord> findAttributeAccessGrants(String clientId, String schemaCode, String objectCode, String attributeCode) {
            return List.of();
        }

        @Override
        public void insertAccessAudit(ObjectExposureAccessAuditWriteRequest request) {
            audits.add(request);
        }
    }

    private static final class RecordingConsumptionDao implements ConsumptionDao {

        private ConsumptionOutboundRecord exposure;
        private final List<Long> unscopedRequests = new ArrayList<>();

        @Override
        public com.lextr.semanticlayer.model.ConsumptionLayerRecord insertLayer(com.lextr.semanticlayer.model.ConsumptionLayerWriteRequest request) {
            throw new UnsupportedOperationException("Not used");
        }

        @Override
        public com.lextr.semanticlayer.model.ConsumptionOutboundRecord insertOutbound(com.lextr.semanticlayer.model.ConsumptionOutboundWriteRequest request) {
            throw new UnsupportedOperationException("Not used");
        }

        @Override
        public void insertOutboundGrain(com.lextr.semanticlayer.model.ConsumptionOutboundGrainWriteRequest request) {
            throw new UnsupportedOperationException("Not used");
        }

        @Override
        public List<com.lextr.semanticlayer.model.ConsumptionLayerRecord> findLayers(String clientId, String lifecycleStatusCode) {
            return List.of();
        }

        @Override
        public Optional<com.lextr.semanticlayer.model.ConsumptionLayerRecord> findLayer(String clientId, String layerCode) {
            return Optional.empty();
        }

        @Override
        public List<ConsumptionOutboundRecord> findExposures(String clientId, UUID objectId, String structureTypeCode) {
            return List.of();
        }

        @Override
        public Optional<ConsumptionOutboundRecord> findExposure(String clientId, Long exposureId) {
            return Optional.ofNullable(exposure);
        }

        @Override
        public Optional<ConsumptionOutboundRecord> findExposure(Long exposureId) {
            unscopedRequests.add(exposureId);
            return Optional.ofNullable(exposure);
        }

        @Override
        public Optional<com.lextr.semanticlayer.model.ConsumptionPromotionRecord> findLatestPromotion(String clientId, Long exposureId) {
            return Optional.empty();
        }

        @Override
        public com.lextr.semanticlayer.model.ConsumptionPromotionRecord insertPromotionRequest(String clientId, Long outboundId, String sourceSdlcStatusCode, String targetSdlcStatusCode, String validationStatusCode, String opaDecisionCode, Long workflowTaskId, String promotionStatusCode, Integer versionNumber, OffsetDateTime createdTs, String createdBy, OffsetDateTime updatedTs, String updatedBy) {
            throw new UnsupportedOperationException("Not used");
        }

        @Override
        public com.lextr.semanticlayer.model.ConsumptionPromotionRecord applyPromotion(String clientId, Long id, String targetSdlcStatusCode, String validationStatusCode, String opaDecisionCode, String promotionStatusCode, OffsetDateTime appliedTs, String appliedBy, OffsetDateTime updatedTs, String updatedBy) {
            throw new UnsupportedOperationException("Not used");
        }

        @Override
        public com.lextr.semanticlayer.model.FilterLookupWorkflowTaskRecord insertWorkflowTask(String clientId, String entityRef, String taskStatusCode, String submittedBy, OffsetDateTime submittedTs, String descriptionTxt, String approvedBy, OffsetDateTime approvedTs, String approvalNoteTxt) {
            throw new UnsupportedOperationException("Not used");
        }

        @Override
        public void insertMetadataChangeHistory(String clientId, String entityTypeCode, String entityRef, String changeTypeCode, String changeReasonTxt, String changedBy, OffsetDateTime changedTs) {
            throw new UnsupportedOperationException("Not used");
        }
    }

    private static final class RecordingLogicalPhysicalResolutionDao implements LogicalPhysicalResolutionDao {

        private List<LogicalPhysicalResolutionRecord> attributeResults = List.of();
        private List<LogicalPhysicalResolutionRecord> outboundResults = List.of();

        @Override
        public List<LogicalPhysicalResolutionRecord> findByAttributes(String clientId, String schemaCode, String objectCode, List<String> logicalAttributeCodes) {
            return attributeResults;
        }

        @Override
        public List<LogicalPhysicalResolutionRecord> findByOutboundGrain(String clientId, Long outboundId) {
            return outboundResults;
        }
    }

    private static final class RecordingSemanticResolvePolicyClient implements SemanticResolvePolicyClient {

        private final List<SemanticResolvePolicyRequestDto> requests = new ArrayList<>();

        @Override
        public ObjectExposurePolicyDecisionDto evaluateAccess(SemanticResolvePolicyRequestDto request) {
            requests.add(request);
            if (request.resource_client_id() != null
                    && !request.resource_client_id().isBlank()
                    && !request.client_id().equals(request.resource_client_id())) {
                return new ObjectExposurePolicyDecisionDto(false, "POL-RS-001", "Cross-tenant resolve denied");
            }
            if (!"ENGINE".equalsIgnoreCase(request.role_cd()) || !"RESOLUTION".equalsIgnoreCase(request.purpose_cd())) {
                return new ObjectExposurePolicyDecisionDto(false, "POL-RS-001", "Resolve denied for non-engine principal");
            }
            return new ObjectExposurePolicyDecisionDto(true, null, null);
        }
    }

    private static final class RecordingObjectExposurePolicyClient implements ObjectExposurePolicyClient {

        private final List<ObjectExposureClassificationPolicyRequestDto> requests = new ArrayList<>();
        private ObjectExposureClassificationPolicyDecisionDto objectDecision = new ObjectExposureClassificationPolicyDecisionDto(true, false, false, null, List.of(), null, null);
        private final Map<String, ObjectExposureClassificationPolicyDecisionDto> attributeDecisions = new LinkedHashMap<>();

        void maskObject(String maskValue) {
            objectDecision = new ObjectExposureClassificationPolicyDecisionDto(true, true, false, maskValue, List.of("object_nm"), null, null);
        }

        void maskAttribute(String attributeCode, String maskValue) {
            attributeDecisions.put(attributeCode, new ObjectExposureClassificationPolicyDecisionDto(
                    true,
                    true,
                    false,
                    maskValue,
                    List.of("attribute_nm"),
                    null,
                    null
            ));
        }

        void withholdAttribute(String attributeCode) {
            attributeDecisions.put(attributeCode, new ObjectExposureClassificationPolicyDecisionDto(
                    true,
                    false,
                    true,
                    null,
                    List.of(),
                    "POL-DC-001",
                    "Attribute withheld"
            ));
        }

        @Override
        public ObjectExposurePolicyDecisionDto evaluateAccess(com.lextr.semanticlayer.dto.ObjectExposureAccessPolicyRequestDto request) {
            return new ObjectExposurePolicyDecisionDto(true, null, null);
        }

        @Override
        public ObjectExposureClassificationPolicyDecisionDto evaluateClassification(ObjectExposureClassificationPolicyRequestDto request) {
            requests.add(request);
            if (request.attribute_cd() == null) {
                return objectDecision;
            }
            return attributeDecisions.getOrDefault(
                    request.attribute_cd(),
                    new ObjectExposureClassificationPolicyDecisionDto(true, false, false, null, List.of(), null, null)
            );
        }
    }
}
