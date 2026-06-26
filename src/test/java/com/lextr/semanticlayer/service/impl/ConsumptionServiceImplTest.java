package com.lextr.semanticlayer.service.impl;

import com.lextr.semanticlayer.dao.ConsumptionDao;
import com.lextr.semanticlayer.dao.ObjectExposureReadDao;
import com.lextr.semanticlayer.dto.ConsumptionExposureDto;
import com.lextr.semanticlayer.dto.ConsumptionLayerDto;
import com.lextr.semanticlayer.dto.ConsumptionPromotionRequestDto;
import com.lextr.semanticlayer.exception.RegistryResourceNotFoundException;
import com.lextr.semanticlayer.model.ConsumptionLayerRecord;
import com.lextr.semanticlayer.model.ConsumptionOutboundRecord;
import com.lextr.semanticlayer.model.ConsumptionPromotionRecord;
import com.lextr.semanticlayer.model.ObjectExposureRecord;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConsumptionServiceImplTest {

    @Test
    void routesLayerAndExposureReadsThroughDao() {
        UUID objectId = UUID.fromString("00000000-0000-0000-0000-000000000101");
        RecordingObjectExposureReadDao objectDao = new RecordingObjectExposureReadDao(Optional.of(objectRecord(objectId)));
        RecordingConsumptionDao dao = new RecordingConsumptionDao();
        dao.layers = List.of(layerRecord());
        dao.exposures = List.of(exposureRecord());

        ConsumptionServiceImpl service = new ConsumptionServiceImpl(objectDao, dao);

        List<ConsumptionLayerDto> layers = service.findLayers("client-a", "ACTIVE");
        List<ConsumptionExposureDto> exposures = service.findExposures("client-a", objectId, "TECHNICAL");

        assertEquals("CL-01", layers.get(0).layer_cd());
        assertEquals("OB-01", exposures.get(0).outbound_cd());
        assertEquals("client-a", objectDao.lastClientId);
        assertEquals(objectId, objectDao.lastObjectId);
        assertEquals("TECHNICAL", dao.lastStructureTypeCode);
    }

    @Test
    void promotesExposureAndReturnsRequestedTargetStatus() {
        RecordingObjectExposureReadDao objectDao = new RecordingObjectExposureReadDao(Optional.empty());
        RecordingConsumptionDao dao = new RecordingConsumptionDao();
        dao.exposure = exposureRecord();
        dao.latestPromotion = Optional.of(new ConsumptionPromotionRecord(
                301L,
                "client-a",
                101L,
                "DEV",
                "QA",
                "PENDING",
                "PENDING",
                501L,
                "PENDING_APPROVAL",
                1,
                OffsetDateTime.parse("2026-06-20T10:15:30Z"),
                "approver",
                OffsetDateTime.parse("2026-06-20T10:15:30Z"),
                "approver",
                OffsetDateTime.parse("2026-06-20T10:15:30Z"),
                "approver"
        ));

        ConsumptionServiceImpl service = new ConsumptionServiceImpl(objectDao, dao);
        ConsumptionExposureDto promoted = service.promoteExposure(
                "client-a",
                101L,
                new ConsumptionPromotionRequestDto("QA", "approver", "Promote for QA")
        );

        assertEquals("QA", promoted.sdlc_status_cd());
        assertEquals(2, promoted.version_nbr());
        assertEquals("client-a", dao.lastPromotionClientId);
        assertEquals(101L, dao.lastPromotionExposureId);
        assertEquals("QA", dao.lastInsertedTargetStatus);
    }

    @Test
    void returns404WhenObjectIsMissingForExposureRead() {
        ConsumptionServiceImpl service = new ConsumptionServiceImpl(
                new RecordingObjectExposureReadDao(Optional.empty()),
                new RecordingConsumptionDao()
        );

        assertThrows(
                RegistryResourceNotFoundException.class,
                () -> service.findExposures("client-a", UUID.fromString("00000000-0000-0000-0000-000000000999"), null)
        );
    }

    private static ConsumptionLayerRecord layerRecord() {
        return new ConsumptionLayerRecord(
                11L,
                "client-a",
                "CL-01",
                "Finance Layer",
                "Finance outbound descriptor",
                "DATA_ASSET",
                "ACTIVE",
                OffsetDateTime.parse("2026-06-20T10:15:30Z"),
                "owner",
                OffsetDateTime.parse("2026-06-20T10:15:30Z"),
                "owner"
        );
    }

    private static ConsumptionOutboundRecord exposureRecord() {
        return new ConsumptionOutboundRecord(
                101L,
                "client-a",
                "CL-01",
                202L,
                "OB-01",
                "Outbound 01",
                "TECHNICAL",
                "Technical exposure",
                List.of("ledger_id", "company_id"),
                "DEV",
                1,
                OffsetDateTime.parse("2026-06-20T10:15:30Z"),
                "owner",
                OffsetDateTime.parse("2026-06-20T10:15:30Z"),
                "owner"
        );
    }

    private static ObjectExposureRecord objectRecord(UUID objectId) {
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
                OffsetDateTime.parse("2026-06-17T10:15:30+05:30"),
                "platform"
        );
    }

    private static final class RecordingObjectExposureReadDao implements ObjectExposureReadDao {

        private final Optional<ObjectExposureRecord> object;
        private String lastClientId;
        private UUID lastObjectId;

        private RecordingObjectExposureReadDao(Optional<ObjectExposureRecord> object) {
            this.object = object;
        }

        @Override
        public List<ObjectExposureRecord> findObjects(String clientId, String schemaCode, String lifecycleStatusCode) {
            return List.of();
        }

        @Override
        public Optional<ObjectExposureRecord> findObject(String clientId, UUID objectId) {
            lastClientId = clientId;
            lastObjectId = objectId;
            return object;
        }

        @Override
        public Optional<ObjectExposureRecord> findObject(String schemaCode, String objectCode) {
            return object;
        }

        @Override
        public List<com.lextr.semanticlayer.model.AttributeExposureRecord> findAttributes(String clientId, UUID objectId) {
            return List.of();
        }

        @Override
        public List<com.lextr.semanticlayer.model.AttributeAccessGrantRecord> findAttributeAccessGrants(String clientId, String schemaCode, String objectCode, String attributeCode) {
            return List.of();
        }

        @Override
        public void insertAccessAudit(com.lextr.semanticlayer.model.ObjectExposureAccessAuditWriteRequest request) {
        }
    }

    private static final class RecordingConsumptionDao implements ConsumptionDao {

        private List<ConsumptionLayerRecord> layers = List.of();
        private List<ConsumptionOutboundRecord> exposures = List.of();
        private ConsumptionOutboundRecord exposure = exposureRecord();
        private Optional<ConsumptionPromotionRecord> latestPromotion = Optional.empty();
        private String lastStructureTypeCode;
        private String lastPromotionClientId;
        private Long lastPromotionExposureId;
        private String lastInsertedTargetStatus;

        @Override
        public List<ConsumptionLayerRecord> findLayers(String clientId, String lifecycleStatusCode) {
            return layers;
        }

        @Override
        public Optional<ConsumptionLayerRecord> findLayer(String clientId, String layerCode) {
            return layers.stream().findFirst();
        }

        @Override
        public List<ConsumptionOutboundRecord> findExposures(String clientId, UUID objectId, String structureTypeCode) {
            lastStructureTypeCode = structureTypeCode;
            return exposures;
        }

        @Override
        public Optional<ConsumptionOutboundRecord> findExposure(String clientId, Long exposureId) {
            lastPromotionClientId = clientId;
            lastPromotionExposureId = exposureId;
            return Optional.of(exposure);
        }

        @Override
        public Optional<ConsumptionPromotionRecord> findLatestPromotion(String clientId, Long exposureId) {
            return latestPromotion;
        }

        @Override
        public ConsumptionPromotionRecord insertPromotionRequest(String clientId, Long outboundId, String sourceSdlcStatusCode, String targetSdlcStatusCode, String validationStatusCode, String opaDecisionCode, Long workflowTaskId, String promotionStatusCode, Integer versionNumber, OffsetDateTime createdTs, String createdBy, OffsetDateTime updatedTs, String updatedBy) {
            lastInsertedTargetStatus = targetSdlcStatusCode;
            return new ConsumptionPromotionRecord(301L, clientId, outboundId, sourceSdlcStatusCode, targetSdlcStatusCode, validationStatusCode, opaDecisionCode, workflowTaskId, promotionStatusCode, versionNumber, createdTs, createdBy, createdTs, createdBy, updatedTs, updatedBy);
        }

        @Override
        public ConsumptionPromotionRecord applyPromotion(String clientId, Long id, String targetSdlcStatusCode, String validationStatusCode, String opaDecisionCode, String promotionStatusCode, OffsetDateTime appliedTs, String appliedBy, OffsetDateTime updatedTs, String updatedBy) {
            return latestPromotion.orElseThrow();
        }

        @Override
        public void insertWorkflowTask(String clientId, String entityRef, String taskStatusCode, String submittedBy, OffsetDateTime submittedTs, String descriptionTxt, String approvedBy, OffsetDateTime approvedTs, String approvalNoteTxt) {
        }

        @Override
        public void insertMetadataChangeHistory(String clientId, String entityTypeCode, String entityRef, String changeTypeCode, String changeReasonTxt, String changedBy, OffsetDateTime changedTs) {
        }
    }
}
