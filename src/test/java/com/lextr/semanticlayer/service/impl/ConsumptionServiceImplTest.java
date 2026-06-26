package com.lextr.semanticlayer.service.impl;

import com.lextr.semanticlayer.dao.ConsumptionDao;
import com.lextr.semanticlayer.dao.ObjectExposureReadDao;
import com.lextr.semanticlayer.dto.ConsumptionLayerDto;
import com.lextr.semanticlayer.dto.ConsumptionLayerRegistrationRequestDto;
import com.lextr.semanticlayer.dto.ConsumptionOutboundGrainRegistrationRequestDto;
import com.lextr.semanticlayer.dto.ConsumptionOutboundRegistrationRequestDto;
import com.lextr.semanticlayer.dto.ConsumptionPolicyDecisionDto;
import com.lextr.semanticlayer.dto.ConsumptionPolicyRequestDto;
import com.lextr.semanticlayer.dto.ConsumptionPromotionRequestDto;
import com.lextr.semanticlayer.dto.WorkflowApprovalRequestDto;
import com.lextr.semanticlayer.dto.WorkflowTaskResponseDto;
import com.lextr.semanticlayer.exception.PolicyViolationException;
import com.lextr.semanticlayer.exception.RegistryResourceNotFoundException;
import com.lextr.semanticlayer.exception.SemanticLayerException;
import com.lextr.semanticlayer.model.ConsumptionLayerRecord;
import com.lextr.semanticlayer.model.ConsumptionLayerWriteRequest;
import com.lextr.semanticlayer.model.ConsumptionOutboundGrainWriteRequest;
import com.lextr.semanticlayer.model.ConsumptionOutboundRecord;
import com.lextr.semanticlayer.model.ConsumptionOutboundWriteRequest;
import com.lextr.semanticlayer.model.ConsumptionPromotionRecord;
import com.lextr.semanticlayer.model.FilterLookupWorkflowTaskRecord;
import com.lextr.semanticlayer.model.ObjectExposureRecord;
import com.lextr.semanticlayer.service.ConsumptionPolicyClient;
import com.lextr.semanticlayer.service.WorkflowApprovalService;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConsumptionServiceImplTest {

    @Test
    void registersConsumptionLayerAtomically() {
        UUID objectId = UUID.fromString("00000000-0000-0000-0000-000000000101");
        RecordingObjectExposureReadDao objectDao = new RecordingObjectExposureReadDao(Optional.of(objectRecord(objectId)));
        RecordingConsumptionDao dao = new RecordingConsumptionDao();
        RecordingTransactionOperations tx = new RecordingTransactionOperations();
        ConsumptionServiceImpl service = new ConsumptionServiceImpl(objectDao, dao, new NoOpConsumptionPolicyClient(), new NoOpWorkflowApprovalService(), tx);

        ConsumptionLayerDto layer = service.registerConsumptionLayer(new ConsumptionLayerRegistrationRequestDto(
                "client-a",
                "CL-01",
                "Finance Layer",
                "Finance outbound descriptor",
                "DATA_ASSET",
                List.of(new ConsumptionOutboundRegistrationRequestDto(
                        "OB-01",
                        "Outbound 01",
                        objectId,
                        "TECHNICAL",
                        "Technical exposure",
                        "DEV",
                        List.of(
                                new ConsumptionOutboundGrainRegistrationRequestDto(1, "ledger_id", "PRIMARY"),
                                new ConsumptionOutboundGrainRegistrationRequestDto(2, "company_id", "FOREIGN")
                        )
                )),
                "owner"
        ));

        assertEquals("CL-01", layer.layer_cd());
        assertTrue(tx.executed);
        assertEquals(List.of("insertLayer", "insertOutbound", "insertOutboundGrain", "insertOutboundGrain", "insertMetadataChangeHistory"), dao.events);
    }

    @Test
    void rollsBackConsumptionLayerRegistrationWhenOutboundInsertFails() {
        UUID objectId = UUID.fromString("00000000-0000-0000-0000-000000000101");
        TransactionHarness harness = new TransactionHarness();
        FailingConsumptionDao dao = new FailingConsumptionDao(harness);
        ConsumptionServiceImpl service = new ConsumptionServiceImpl(
                new RecordingObjectExposureReadDao(Optional.of(objectRecord(objectId))),
                dao,
                new NoOpConsumptionPolicyClient(),
                new NoOpWorkflowApprovalService(),
                new HarnessTransactionOperations(harness)
        );

        SemanticLayerException exception = assertThrows(
                SemanticLayerException.class,
                () -> service.registerConsumptionLayer(new ConsumptionLayerRegistrationRequestDto(
                        "client-a",
                        "CL-01",
                        "Finance Layer",
                        "Finance outbound descriptor",
                        "DATA_ASSET",
                        List.of(new ConsumptionOutboundRegistrationRequestDto(
                                "OB-01",
                                "Outbound 01",
                                objectId,
                                "TECHNICAL",
                                "Technical exposure",
                                "DEV",
                                List.of(new ConsumptionOutboundGrainRegistrationRequestDto(1, "ledger_id", "PRIMARY"))
                        )),
                        "owner"
                ))
        );

        assertEquals("Unable to register consumption layer", exception.getMessage());
        assertTrue(harness.rolledBack);
        assertEquals(0, dao.committedLayers.size());
        assertEquals(0, dao.committedOutbounds.size());
    }

    @Test
    void promotesExposureRunsValidationOpaApprovalAndApplyInOrder() {
        RecordingObjectExposureReadDao objectDao = new RecordingObjectExposureReadDao(Optional.empty());
        RecordingConsumptionDao dao = new RecordingConsumptionDao();
        dao.exposure = exposureRecord("DEV");
        dao.latestPromotion = Optional.of(new ConsumptionPromotionRecord(
                301L,
                "client-a",
                101L,
                "DEV",
                "QA",
                "VALIDATED",
                "ALLOW",
                501L,
                "PENDING_APPROVAL",
                1,
                null,
                null,
                OffsetDateTime.parse("2026-06-20T10:15:30Z"),
                "approver",
                OffsetDateTime.parse("2026-06-20T10:15:30Z"),
                "approver"
        ));
        RecordingConsumptionPolicyClient policyClient = new RecordingConsumptionPolicyClient(true, null, null);
        RecordingWorkflowApprovalService workflowApprovalService = new RecordingWorkflowApprovalService();
        RecordingTransactionOperations tx = new RecordingTransactionOperations();

        ConsumptionServiceImpl service = new ConsumptionServiceImpl(objectDao, dao, policyClient, workflowApprovalService, tx);
        ConsumptionPromotionRequestDto request = new ConsumptionPromotionRequestDto("QA", "approver", "Promote for QA");

        var promoted = service.promoteExposure("client-a", 101L, request);

        assertEquals("QA", promoted.sdlc_status_cd());
        assertEquals(2, promoted.version_nbr());
        assertEquals(List.of("insertWorkflowTask", "insertPromotionRequest", "applyPromotion", "insertMetadataChangeHistory"), dao.events);
        assertEquals(List.of("opa"), policyClient.events);
        assertEquals("client-a", policyClient.lastRequest.client_id());
        assertEquals(101L, policyClient.lastRequest.exposure_id());
        assertEquals(List.of("approval"), workflowApprovalService.events);
        assertTrue(tx.executed);
    }

    @Test
    void returns422WhenOpaBlocksPromotion() {
        RecordingConsumptionDao dao = new RecordingConsumptionDao();
        dao.exposure = exposureRecord("DEV");
        RecordingConsumptionPolicyClient policyClient = new RecordingConsumptionPolicyClient(false, "POL-CL-001", "Consumption layer blocked");
        ConsumptionServiceImpl service = new ConsumptionServiceImpl(
                new RecordingObjectExposureReadDao(Optional.empty()),
                dao,
                policyClient,
                new RecordingWorkflowApprovalService(),
                new RecordingTransactionOperations()
        );

        PolicyViolationException exception = assertThrows(
                PolicyViolationException.class,
                () -> service.promoteExposure("client-a", 101L, new ConsumptionPromotionRequestDto("QA", "approver", "Promote for QA"))
        );

        assertEquals("POL-CL-001", exception.code());
        assertEquals(List.of("opa"), policyClient.events);
        assertTrue(dao.events.isEmpty());
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

    private static ConsumptionOutboundRecord exposureRecord(String sdlcStatusCode) {
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
                sdlcStatusCode,
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

        private RecordingObjectExposureReadDao(Optional<ObjectExposureRecord> object) {
            this.object = object;
        }

        @Override
        public List<ObjectExposureRecord> findObjects(String clientId, String schemaCode, String lifecycleStatusCode) {
            return List.of();
        }

        @Override
        public Optional<ObjectExposureRecord> findObject(String clientId, UUID objectId) {
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

        private final List<String> events = new ArrayList<>();
        private ConsumptionLayerRecord layer = layerRecord();
        private List<ConsumptionLayerRecord> layers = List.of(layerRecord());
        private List<ConsumptionOutboundRecord> exposures = List.of();
        private ConsumptionOutboundRecord exposure = exposureRecord("DEV");
        private Optional<ConsumptionPromotionRecord> latestPromotion = Optional.empty();

        @Override
        public ConsumptionLayerRecord insertLayer(ConsumptionLayerWriteRequest request) {
            events.add("insertLayer");
            return layer;
        }

        @Override
        public ConsumptionOutboundRecord insertOutbound(ConsumptionOutboundWriteRequest request) {
            events.add("insertOutbound");
            return exposure;
        }

        @Override
        public void insertOutboundGrain(ConsumptionOutboundGrainWriteRequest request) {
            events.add("insertOutboundGrain");
        }

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
            return exposures;
        }

        @Override
        public Optional<ConsumptionOutboundRecord> findExposure(String clientId, Long exposureId) {
            return Optional.of(exposure);
        }

        @Override
        public Optional<ConsumptionPromotionRecord> findLatestPromotion(String clientId, Long exposureId) {
            return latestPromotion;
        }

        @Override
        public ConsumptionPromotionRecord insertPromotionRequest(String clientId, Long outboundId, String sourceSdlcStatusCode, String targetSdlcStatusCode, String validationStatusCode, String opaDecisionCode, Long workflowTaskId, String promotionStatusCode, Integer versionNumber, OffsetDateTime createdTs, String createdBy, OffsetDateTime updatedTs, String updatedBy) {
            events.add("insertPromotionRequest");
            return new ConsumptionPromotionRecord(301L, clientId, outboundId, sourceSdlcStatusCode, targetSdlcStatusCode, validationStatusCode, opaDecisionCode, workflowTaskId, promotionStatusCode, versionNumber, null, null, createdTs, createdBy, updatedTs, updatedBy);
        }

        @Override
        public ConsumptionPromotionRecord applyPromotion(String clientId, Long id, String targetSdlcStatusCode, String validationStatusCode, String opaDecisionCode, String promotionStatusCode, OffsetDateTime appliedTs, String appliedBy, OffsetDateTime updatedTs, String updatedBy) {
            events.add("applyPromotion");
            return new ConsumptionPromotionRecord(id, clientId, exposure.id(), exposure.sdlc_status_cd(), targetSdlcStatusCode, validationStatusCode, opaDecisionCode, 501L, promotionStatusCode, 2, appliedTs, appliedBy, appliedTs, appliedBy, updatedTs, updatedBy);
        }

        @Override
        public FilterLookupWorkflowTaskRecord insertWorkflowTask(String clientId, String entityRef, String taskStatusCode, String submittedBy, OffsetDateTime submittedTs, String descriptionTxt, String approvedBy, OffsetDateTime approvedTs, String approvalNoteTxt) {
            events.add("insertWorkflowTask");
            return new FilterLookupWorkflowTaskRecord(501L, "CONSUMPTION_PROMOTE", "CONSUMPTION_EXPOSURE", entityRef, taskStatusCode, submittedBy, submittedTs, null, null, descriptionTxt, clientId, approvedBy, approvedTs, approvalNoteTxt);
        }

        @Override
        public void insertMetadataChangeHistory(String clientId, String entityTypeCode, String entityRef, String changeTypeCode, String changeReasonTxt, String changedBy, OffsetDateTime changedTs) {
            events.add("insertMetadataChangeHistory");
        }
    }

    private static final class FailingConsumptionDao implements ConsumptionDao {

        private final TransactionHarness harness;
        private final List<ConsumptionLayerRecord> committedLayers = new ArrayList<>();
        private final List<ConsumptionOutboundRecord> committedOutbounds = new ArrayList<>();

        private FailingConsumptionDao(TransactionHarness harness) {
            this.harness = harness;
        }

        @Override
        public ConsumptionLayerRecord insertLayer(ConsumptionLayerWriteRequest request) {
            ConsumptionLayerRecord record = new ConsumptionLayerRecord(
                    11L,
                    request.client_id(),
                    request.layer_cd(),
                    request.layer_nm(),
                    request.layer_desc_txt(),
                    request.layer_type_cd(),
                    request.lifecycle_status_cd(),
                    request.created_ts(),
                    request.created_by(),
                    request.updated_ts(),
                    request.updated_by()
            );
            harness.stageLayer(record, committedLayers);
            return record;
        }

        @Override
        public ConsumptionOutboundRecord insertOutbound(ConsumptionOutboundWriteRequest request) {
            ConsumptionOutboundRecord record = new ConsumptionOutboundRecord(
                    21L,
                    request.client_id(),
                    request.layer_cd(),
                    202L,
                    request.outbound_cd(),
                    request.outbound_nm(),
                    request.structure_type_cd(),
                    request.description_txt(),
                    List.of("ledger_id"),
                    request.sdlc_status_cd(),
                    request.version_nbr(),
                    request.created_ts(),
                    request.created_by(),
                    request.updated_ts(),
                    request.updated_by()
            );
            harness.stageOutbound(record, committedOutbounds);
            throw new IllegalStateException("db write failed");
        }

        @Override
        public void insertOutboundGrain(ConsumptionOutboundGrainWriteRequest request) {
            throw new UnsupportedOperationException("Not expected");
        }

        @Override
        public List<ConsumptionLayerRecord> findLayers(String clientId, String lifecycleStatusCode) {
            return List.of();
        }

        @Override
        public Optional<ConsumptionLayerRecord> findLayer(String clientId, String layerCode) {
            return Optional.empty();
        }

        @Override
        public List<ConsumptionOutboundRecord> findExposures(String clientId, UUID objectId, String structureTypeCode) {
            return List.of();
        }

        @Override
        public Optional<ConsumptionOutboundRecord> findExposure(String clientId, Long exposureId) {
            return Optional.empty();
        }

        @Override
        public Optional<ConsumptionPromotionRecord> findLatestPromotion(String clientId, Long exposureId) {
            return Optional.empty();
        }

        @Override
        public ConsumptionPromotionRecord insertPromotionRequest(String clientId, Long outboundId, String sourceSdlcStatusCode, String targetSdlcStatusCode, String validationStatusCode, String opaDecisionCode, Long workflowTaskId, String promotionStatusCode, Integer versionNumber, OffsetDateTime createdTs, String createdBy, OffsetDateTime updatedTs, String updatedBy) {
            throw new UnsupportedOperationException("Not expected");
        }

        @Override
        public ConsumptionPromotionRecord applyPromotion(String clientId, Long id, String targetSdlcStatusCode, String validationStatusCode, String opaDecisionCode, String promotionStatusCode, OffsetDateTime appliedTs, String appliedBy, OffsetDateTime updatedTs, String updatedBy) {
            throw new UnsupportedOperationException("Not expected");
        }

        @Override
        public FilterLookupWorkflowTaskRecord insertWorkflowTask(String clientId, String entityRef, String taskStatusCode, String submittedBy, OffsetDateTime submittedTs, String descriptionTxt, String approvedBy, OffsetDateTime approvedTs, String approvalNoteTxt) {
            throw new UnsupportedOperationException("Not expected");
        }

        @Override
        public void insertMetadataChangeHistory(String clientId, String entityTypeCode, String entityRef, String changeTypeCode, String changeReasonTxt, String changedBy, OffsetDateTime changedTs) {
            throw new UnsupportedOperationException("Not expected");
        }
    }

    private static final class RecordingConsumptionPolicyClient implements ConsumptionPolicyClient {

        private final boolean allowed;
        private final String code;
        private final String message;
        private ConsumptionPolicyRequestDto lastRequest;
        private final List<String> events = new ArrayList<>();

        private RecordingConsumptionPolicyClient(boolean allowed, String code, String message) {
            this.allowed = allowed;
            this.code = code;
            this.message = message;
        }

        @Override
        public ConsumptionPolicyDecisionDto validatePromotion(ConsumptionPolicyRequestDto request) {
            events.add("opa");
            lastRequest = request;
            return new ConsumptionPolicyDecisionDto(allowed, code, message);
        }
    }

    private static final class RecordingWorkflowApprovalService implements WorkflowApprovalService {

        private final List<String> events = new ArrayList<>();

        @Override
        public WorkflowTaskResponseDto approveTask(Long id, WorkflowApprovalRequestDto request) {
            events.add("approval");
            OffsetDateTime now = OffsetDateTime.parse("2026-06-20T10:15:30Z");
            return new WorkflowTaskResponseDto(
                    id,
                    "CONSUMPTION_PROMOTE",
                    "CONSUMPTION_EXPOSURE",
                    String.valueOf(id),
                    "APPROVED",
                    request.approved_by(),
                    now,
                    null,
                    null,
                    request.approval_note_txt(),
                    request.client_id(),
                    request.approved_by(),
                    now,
                    request.approval_note_txt()
            );
        }

        @Override
        public WorkflowTaskResponseDto rejectTask(Long id, java.util.Map<String, String> body) {
            throw new UnsupportedOperationException("Not used");
        }
    }

    private static final class RecordingTransactionOperations implements org.springframework.transaction.support.TransactionOperations {

        private boolean executed;

        @Override
        public <T> T execute(org.springframework.transaction.support.TransactionCallback<T> action) {
            executed = true;
            return action.doInTransaction(new NoOpTransactionStatus());
        }
    }

    private static final class HarnessTransactionOperations implements org.springframework.transaction.support.TransactionOperations {

        private final TransactionHarness harness;

        private HarnessTransactionOperations(TransactionHarness harness) {
            this.harness = harness;
        }

        @Override
        public <T> T execute(org.springframework.transaction.support.TransactionCallback<T> action) {
            harness.begin();
            try {
                T result = action.doInTransaction(new NoOpTransactionStatus());
                harness.commit();
                return result;
            } catch (RuntimeException exception) {
                harness.rollback();
                throw exception;
            }
        }
    }

    private static final class NoOpTransactionStatus implements org.springframework.transaction.TransactionStatus {

        @Override
        public boolean isNewTransaction() {
            return false;
        }

        @Override
        public boolean hasSavepoint() {
            return false;
        }

        @Override
        public void setRollbackOnly() {
        }

        @Override
        public boolean isRollbackOnly() {
            return false;
        }

        @Override
        public void flush() {
        }

        @Override
        public boolean isCompleted() {
            return false;
        }

        @Override
        public Object createSavepoint() {
            return null;
        }

        @Override
        public void rollbackToSavepoint(Object savepoint) {
        }

        @Override
        public void releaseSavepoint(Object savepoint) {
        }
    }

    private static final class NoOpConsumptionPolicyClient implements ConsumptionPolicyClient {
    }

    private static final class TransactionHarness {

        private boolean committed;
        private boolean rolledBack;
        private final List<ConsumptionLayerRecord> pendingLayers = new ArrayList<>();
        private final List<ConsumptionOutboundRecord> pendingOutbounds = new ArrayList<>();
        private List<ConsumptionLayerRecord> layerSink;
        private List<ConsumptionOutboundRecord> outboundSink;

        private void begin() {
            committed = false;
            rolledBack = false;
            pendingLayers.clear();
            pendingOutbounds.clear();
        }

        private void commit() {
            if (layerSink != null) {
                layerSink.addAll(pendingLayers);
            }
            if (outboundSink != null) {
                outboundSink.addAll(pendingOutbounds);
            }
            committed = true;
        }

        private void rollback() {
            pendingLayers.clear();
            pendingOutbounds.clear();
            rolledBack = true;
        }

        private void stageLayer(ConsumptionLayerRecord record, List<ConsumptionLayerRecord> sink) {
            layerSink = sink;
            pendingLayers.add(record);
        }

        private void stageOutbound(ConsumptionOutboundRecord record, List<ConsumptionOutboundRecord> sink) {
            outboundSink = sink;
            pendingOutbounds.add(record);
        }
    }

    private static final class NoOpWorkflowApprovalService implements WorkflowApprovalService {

        @Override
        public WorkflowTaskResponseDto approveTask(Long id, WorkflowApprovalRequestDto request) {
            return new WorkflowTaskResponseDto(id, "CONSUMPTION_PROMOTE", "CONSUMPTION_EXPOSURE", String.valueOf(id), "APPROVED", request.approved_by(), OffsetDateTime.parse("2026-06-20T10:15:30Z"), null, null, request.approval_note_txt(), request.client_id(), request.approved_by(), OffsetDateTime.parse("2026-06-20T10:15:30Z"), request.approval_note_txt());
        }

        @Override
        public WorkflowTaskResponseDto rejectTask(Long id, java.util.Map<String, String> body) {
            throw new UnsupportedOperationException("Not used");
        }
    }
}
