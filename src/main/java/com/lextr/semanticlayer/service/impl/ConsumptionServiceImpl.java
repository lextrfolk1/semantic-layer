package com.lextr.semanticlayer.service.impl;

import com.lextr.semanticlayer.dao.ConsumptionDao;
import com.lextr.semanticlayer.dao.ObjectExposureReadDao;
import com.lextr.semanticlayer.dto.ConsumptionExposureDto;
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
import com.lextr.semanticlayer.service.ConsumptionService;
import com.lextr.semanticlayer.service.WorkflowApprovalService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Service
public class ConsumptionServiceImpl implements ConsumptionService {

    private static final Logger logger = LoggerFactory.getLogger(ConsumptionServiceImpl.class);

    private static final String CONSUMPTION_LAYER_ENTITY_TYPE_CD = "CONSUMPTION_LAYER";
    private static final String CONSUMPTION_EXPOSURE_ENTITY_TYPE_CD = "CONSUMPTION_EXPOSURE";
    private static final String CONSUMPTION_PROMOTE_TASK_TYPE_CD = "CONSUMPTION_PROMOTE";
    private static final String REGISTERED_CHANGE_TYPE_CD = "REGISTERED";
    private static final String PROMOTED_CHANGE_TYPE_CD = "PROMOTED";
    private static final String DRAFT_LAYER_STATUS_CD = "DRAFT";
    private static final String PENDING_TASK_STATUS_CD = "PENDING";
    private static final String PENDING_APPROVAL_STATUS_CD = "PENDING_APPROVAL";
    private static final String APPLIED_STATUS_CD = "APPLIED";
    private static final String VALIDATED_STATUS_CD = "VALIDATED";
    private static final String OPA_ALLOW_DECISION_CD = "ALLOW";
    private static final List<String> SDLC_SEQUENCE = List.of("DRAFT", "DEV", "QA", "UAT", "PROD");

    private final ObjectExposureReadDao objectExposureReadDao;
    private final ConsumptionDao consumptionDao;
    private final ConsumptionPolicyClient consumptionPolicyClient;
    private final WorkflowApprovalService workflowApprovalService;
    private final TransactionOperations transactionOperations;

    @Autowired
    public ConsumptionServiceImpl(
            ObjectProvider<ObjectExposureReadDao> objectExposureReadDaoProvider,
            ObjectProvider<ConsumptionDao> consumptionDaoProvider,
            ObjectProvider<ConsumptionPolicyClient> consumptionPolicyClientProvider,
            ObjectProvider<WorkflowApprovalService> workflowApprovalServiceProvider,
            @Qualifier("semanticLayerTransactionOperations")
            ObjectProvider<TransactionOperations> transactionOperationsProvider
    ) {
        this(
                objectExposureReadDaoProvider.getIfAvailable(EmptyObjectExposureReadDao::new),
                consumptionDaoProvider.getIfAvailable(MissingConsumptionDao::new),
                consumptionPolicyClientProvider.getIfAvailable(NoOpConsumptionPolicyClient::new),
                workflowApprovalServiceProvider.getIfAvailable(NoOpWorkflowApprovalService::new),
                transactionOperationsProvider.getIfAvailable(NoOpTransactionOperations::new)
        );
    }

    public ConsumptionServiceImpl(ObjectExposureReadDao objectExposureReadDao,
                                  ConsumptionDao consumptionDao) {
        this(objectExposureReadDao,
                consumptionDao,
                new NoOpConsumptionPolicyClient(),
                new NoOpWorkflowApprovalService(),
                new NoOpTransactionOperations());
    }

    ConsumptionServiceImpl(ObjectExposureReadDao objectExposureReadDao,
                           ConsumptionDao consumptionDao,
                           ConsumptionPolicyClient consumptionPolicyClient,
                           WorkflowApprovalService workflowApprovalService,
                           TransactionOperations transactionOperations) {
        this.objectExposureReadDao = objectExposureReadDao;
        this.consumptionDao = consumptionDao;
        this.consumptionPolicyClient = consumptionPolicyClient;
        this.workflowApprovalService = workflowApprovalService;
        this.transactionOperations = transactionOperations;
    }

    @Override
    public List<ConsumptionLayerDto> findLayers(String clientId, String lifecycleStatusCode) {
        logger.debug("Finding consumption layers. clientId={}, lifecycleStatusCode={}", clientId, lifecycleStatusCode);
        List<ConsumptionLayerDto> layers = consumptionDao.findLayers(clientId, lifecycleStatusCode).stream()
                .map(this::toLayerDto)
                .toList();
        logger.debug("Consumption layers resolved in service. clientId={}, resultCount={}", clientId, layers.size());
        return layers;
    }

    @Override
    public ConsumptionLayerDto findLayer(String clientId, String layerCode) {
        logger.debug("Finding consumption layer. clientId={}, layerCode={}", clientId, layerCode);
        ConsumptionLayerRecord record = consumptionDao.findLayer(clientId, layerCode)
                .orElseThrow(() -> {
                    logger.warn("Consumption layer not found. clientId={}, layerCode={}", clientId, layerCode);
                    return new RegistryResourceNotFoundException("consumption layer", layerCode);
                });
        return toLayerDto(record);
    }

    @Override
    public List<ConsumptionExposureDto> findExposures(String clientId, UUID objectId, String structureTypeCode) {
        logger.debug("Finding consumption exposures. clientId={}, objectId={}, structureTypeCode={}", clientId, objectId, structureTypeCode);
        ObjectExposureRecord object = objectExposureReadDao.findObject(clientId, objectId)
                .orElseThrow(() -> {
                    logger.warn("Consumption exposures cannot be resolved because object was not found. clientId={}, objectId={}", clientId, objectId);
                    return new RegistryResourceNotFoundException("object", objectId.toString());
                });
        List<ConsumptionExposureDto> exposures = consumptionDao.findExposures(clientId, object.object_id(), structureTypeCode).stream()
                .map(this::toExposureDto)
                .toList();
        logger.debug("Consumption exposures resolved in service. clientId={}, objectId={}, resultCount={}", clientId, objectId, exposures.size());
        return exposures;
    }

    @Override
    public ConsumptionLayerDto registerConsumptionLayer(ConsumptionLayerRegistrationRequestDto request) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        logger.debug(
                "Registering consumption layer. clientId={}, layerCode={}, outboundCount={}",
                request.client_id(),
                request.layer_cd(),
                request.outbounds().size()
        );
        try {
            ConsumptionLayerDto layer = transactionOperations.execute(status -> persistLayerRegistration(request, now));
            logger.info("Consumption layer registered. clientId={}, layerCode={}", request.client_id(), request.layer_cd());
            return layer;
        } catch (IllegalArgumentException exception) {
            logger.warn(
                    "Consumption layer registration validation failed. clientId={}, layerCode={}, errorMessage={}",
                    request.client_id(),
                    request.layer_cd(),
                    exception.getMessage(),
                    exception
            );
            throw exception;
        } catch (RuntimeException exception) {
            logger.error(
                    "Consumption layer registration failed. clientId={}, layerCode={}, errorMessage={}",
                    request.client_id(),
                    request.layer_cd(),
                    exception.getMessage(),
                    exception
            );
            throw new SemanticLayerException("Unable to register consumption layer", exception);
        }
    }

    @Override
    public ConsumptionExposureDto promoteExposure(String clientId, Long exposureId, ConsumptionPromotionRequestDto request) {
        logger.debug(
                "Promoting consumption exposure in service. clientId={}, exposureId={}, targetSdlcStatusCode={}",
                clientId,
                exposureId,
                request.target_sdlc_status_cd()
        );
        try {
            ConsumptionExposureDto exposure =
                    transactionOperations.execute(status -> promoteExposureWithinTransaction(clientId, exposureId, request));
            logger.info(
                    "Consumption exposure promoted in service. clientId={}, exposureId={}, targetSdlcStatusCode={}",
                    clientId,
                    exposureId,
                    request.target_sdlc_status_cd()
            );
            return exposure;
        } catch (PolicyViolationException | IllegalArgumentException exception) {
            logger.warn(
                    "Consumption exposure promotion blocked. clientId={}, exposureId={}, errorMessage={}",
                    clientId,
                    exposureId,
                    exception.getMessage(),
                    exception
            );
            throw exception;
        } catch (RuntimeException exception) {
            logger.error(
                    "Consumption exposure promotion failed. clientId={}, exposureId={}, errorMessage={}",
                    clientId,
                    exposureId,
                    exception.getMessage(),
                    exception
            );
            throw new SemanticLayerException("Unable to promote consumption exposure", exception);
        }
    }

    private ConsumptionLayerDto persistLayerRegistration(ConsumptionLayerRegistrationRequestDto request, OffsetDateTime now) {
        ConsumptionLayerRecord layer = consumptionDao.insertLayer(new ConsumptionLayerWriteRequest(
                request.client_id(),
                request.layer_cd(),
                request.layer_nm(),
                request.layer_desc_txt(),
                request.layer_type_cd(),
                DRAFT_LAYER_STATUS_CD,
                now,
                request.registered_by(),
                now,
                request.registered_by()
        ));

        logger.debug("Persisting consumption outbounds. clientId={}, layerCode={}, outboundCount={}", request.client_id(), layer.layer_cd(), request.outbounds().size());
        request.outbounds().forEach(outbound -> persistOutboundRegistration(request, layer, outbound, now));

        consumptionDao.insertMetadataChangeHistory(
                request.client_id(),
                CONSUMPTION_LAYER_ENTITY_TYPE_CD,
                layer.layer_cd(),
                REGISTERED_CHANGE_TYPE_CD,
                "Registered consumption layer " + layer.layer_cd(),
                request.registered_by(),
                now
        );

        return toLayerDto(layer);
    }

    private void persistOutboundRegistration(ConsumptionLayerRegistrationRequestDto request,
                                             ConsumptionLayerRecord layer,
                                             ConsumptionOutboundRegistrationRequestDto outbound,
                                             OffsetDateTime now) {
        ConsumptionOutboundRecord outboundRecord = consumptionDao.insertOutbound(new ConsumptionOutboundWriteRequest(
                request.client_id(),
                layer.layer_cd(),
                outbound.object_id(),
                outbound.outbound_cd(),
                outbound.outbound_nm(),
                outbound.structure_type_cd(),
                outbound.description_txt(),
                outbound.sdlc_status_cd(),
                1,
                now,
                request.registered_by(),
                now,
                request.registered_by()
        ));

        outbound.grains().forEach(grain -> consumptionDao.insertOutboundGrain(new ConsumptionOutboundGrainWriteRequest(
                request.client_id(),
                outboundRecord.id(),
                grain.grain_level_nbr(),
                grain.logical_attribute_cd(),
                grain.attribute_role_cd(),
                now,
                request.registered_by(),
                now,
                request.registered_by()
        )));
    }

    private ConsumptionExposureDto promoteExposureWithinTransaction(String clientId,
                                                                     Long exposureId,
                                                                     ConsumptionPromotionRequestDto request) {
        ConsumptionOutboundRecord exposure = consumptionDao.findExposure(clientId, exposureId)
                .orElseThrow(() -> {
                    logger.warn("Consumption exposure not found for promotion. clientId={}, exposureId={}", clientId, exposureId);
                    return new RegistryResourceNotFoundException("consumption exposure", exposureId.toString());
                });

        validateTransition(exposure.sdlc_status_cd(), request.target_sdlc_status_cd());

        ConsumptionPolicyDecisionDto decision = consumptionPolicyClient.validatePromotion(
                new ConsumptionPolicyRequestDto(
                        clientId,
                        exposureId,
                        exposure.sdlc_status_cd(),
                        request.target_sdlc_status_cd(),
                        request.promoted_by(),
                        request.promotion_reason_txt()
                )
        );
        logger.info(
                "Consumption promotion policy decision resolved. clientId={}, exposureId={}, decisionAllowed={}, decisionCode={}",
                clientId,
                exposureId,
                decision.allowed(),
                decision.code()
        );
        if (!decision.allowed()) {
            logger.warn("Consumption promotion denied by policy. clientId={}, exposureId={}, decisionCode={}", clientId, exposureId, decision.code());
            throw new PolicyViolationException(decision.code(), decision.message());
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        ConsumptionPromotionRecord latestPromotion = consumptionDao.findLatestPromotion(clientId, exposureId).orElse(null);
        Integer versionNumber = latestPromotion == null ? 1 : latestPromotion.version_nbr();
        logger.debug("Resolved promotion version. clientId={}, exposureId={}, versionNumber={}", clientId, exposureId, versionNumber);

        FilterLookupWorkflowTaskRecord workflowTask = consumptionDao.insertWorkflowTask(
                clientId,
                exposureId.toString(),
                PENDING_TASK_STATUS_CD,
                request.promoted_by(),
                now,
                promotionTaskDescription(exposure.outbound_cd(), request.target_sdlc_status_cd()),
                null,
                null,
                null
        );

        ConsumptionPromotionRecord pendingPromotion = consumptionDao.insertPromotionRequest(
                clientId,
                exposureId,
                exposure.sdlc_status_cd(),
                request.target_sdlc_status_cd(),
                VALIDATED_STATUS_CD,
                OPA_ALLOW_DECISION_CD,
                workflowTask.id(),
                PENDING_APPROVAL_STATUS_CD,
                versionNumber,
                now,
                request.promoted_by(),
                now,
                request.promoted_by()
        );

        WorkflowTaskResponseDto approvedTask = workflowApprovalService.approveTask(
                workflowTask.id(),
                new WorkflowApprovalRequestDto(clientId, request.promoted_by(), promotionApprovalNote(exposure, request))
        );

        ConsumptionPromotionRecord appliedPromotion = consumptionDao.applyPromotion(
                clientId,
                pendingPromotion.id(),
                request.target_sdlc_status_cd(),
                VALIDATED_STATUS_CD,
                OPA_ALLOW_DECISION_CD,
                APPLIED_STATUS_CD,
                now,
                request.promoted_by(),
                now,
                request.promoted_by()
        );
        logger.info(
                "Consumption promotion applied. clientId={}, exposureId={}, targetSdlcStatusCode={}, versionNumber={}",
                clientId,
                exposureId,
                appliedPromotion.target_sdlc_status_cd(),
                appliedPromotion.version_nbr()
        );

        consumptionDao.insertMetadataChangeHistory(
                clientId,
                CONSUMPTION_EXPOSURE_ENTITY_TYPE_CD,
                exposureId.toString(),
                PROMOTED_CHANGE_TYPE_CD,
                promotionApprovalNote(exposure, request),
                approvedTask.approved_by() == null ? request.promoted_by() : approvedTask.approved_by(),
                now
        );

        return toExposureDto(exposure, appliedPromotion.target_sdlc_status_cd(), appliedPromotion.version_nbr());
    }

    private void validateTransition(String currentStatus, String targetStatus) {
        int currentIndex = SDLC_SEQUENCE.indexOf(currentStatus == null ? "" : currentStatus.toUpperCase());
        int targetIndex = SDLC_SEQUENCE.indexOf(targetStatus == null ? "" : targetStatus.toUpperCase());
        if (currentIndex < 0 || targetIndex < 0) {
            logger.warn("Invalid consumption SDLC transition requested. currentStatus={}, targetStatus={}", currentStatus, targetStatus);
            throw new IllegalArgumentException("target_sdlc_status_cd is not a valid SDLC transition");
        }
        if (targetIndex != currentIndex + 1) {
            logger.warn("Rejected non-sequential consumption SDLC transition. currentStatus={}, targetStatus={}", currentStatus, targetStatus);
            throw new IllegalArgumentException(
                    "target_sdlc_status_cd must be the next SDLC gate after " + currentStatus
            );
        }
    }

    private String promotionTaskDescription(String outboundCode, String targetStatus) {
        return "Promote outbound exposure " + outboundCode + " to " + targetStatus;
    }

    private String promotionApprovalNote(ConsumptionOutboundRecord exposure, ConsumptionPromotionRequestDto request) {
        if (request.promotion_reason_txt() != null && !request.promotion_reason_txt().isBlank()) {
            return request.promotion_reason_txt();
        }
        return "Promoted outbound exposure " + exposure.outbound_cd() + " to " + request.target_sdlc_status_cd();
    }

    private ConsumptionLayerDto toLayerDto(ConsumptionLayerRecord record) {
        return new ConsumptionLayerDto(
                record.id(),
                record.client_id(),
                record.layer_cd(),
                record.layer_nm(),
                record.layer_desc_txt(),
                record.layer_type_cd(),
                record.lifecycle_status_cd(),
                record.created_ts(),
                record.created_by(),
                record.updated_ts(),
                record.updated_by()
        );
    }

    private ConsumptionExposureDto toExposureDto(ConsumptionOutboundRecord record) {
        return toExposureDto(record, record.sdlc_status_cd(), record.version_nbr());
    }

    private ConsumptionExposureDto toExposureDto(ConsumptionOutboundRecord record, String sdlcStatusCode, Integer versionNumber) {
        return new ConsumptionExposureDto(
                record.id(),
                record.client_id(),
                record.layer_cd(),
                record.object_id(),
                record.outbound_cd(),
                record.outbound_nm(),
                record.structure_type_cd(),
                record.description_txt(),
                record.attributes_jsonb(),
                sdlcStatusCode,
                versionNumber,
                record.created_ts(),
                record.created_by(),
                record.updated_ts(),
                record.updated_by()
        );
    }

    private static final class NoOpConsumptionPolicyClient implements ConsumptionPolicyClient {
    }

    private static final class NoOpWorkflowApprovalService implements WorkflowApprovalService {

        @Override
        public WorkflowTaskResponseDto approveTask(Long id, WorkflowApprovalRequestDto request) {
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            return new WorkflowTaskResponseDto(
                    id,
                    CONSUMPTION_PROMOTE_TASK_TYPE_CD,
                    CONSUMPTION_EXPOSURE_ENTITY_TYPE_CD,
                    String.valueOf(id),
                    "APPROVED",
                    request.approved_by(),
                    now,
                    null,
                    null,
                    null,
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

    private static final class EmptyObjectExposureReadDao implements ObjectExposureReadDao {

        @Override
        public List<ObjectExposureRecord> findObjects(String clientId, String schemaCode, String lifecycleStatusCode) {
            return List.of();
        }

        @Override
        public java.util.Optional<ObjectExposureRecord> findObject(String clientId, UUID objectId) {
            return java.util.Optional.empty();
        }

        @Override
        public java.util.Optional<ObjectExposureRecord> findObject(String schemaCode, String objectCode) {
            return java.util.Optional.empty();
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

    private static final class MissingConsumptionDao implements ConsumptionDao {

        @Override
        public ConsumptionLayerRecord insertLayer(ConsumptionLayerWriteRequest request) {
            throw new SemanticLayerException("ConsumptionDao is not configured");
        }

        @Override
        public ConsumptionOutboundRecord insertOutbound(ConsumptionOutboundWriteRequest request) {
            throw new SemanticLayerException("ConsumptionDao is not configured");
        }

        @Override
        public void insertOutboundGrain(ConsumptionOutboundGrainWriteRequest request) {
            throw new SemanticLayerException("ConsumptionDao is not configured");
        }

        @Override
        public List<ConsumptionLayerRecord> findLayers(String clientId, String lifecycleStatusCode) {
            throw new SemanticLayerException("ConsumptionDao is not configured");
        }

        @Override
        public java.util.Optional<ConsumptionLayerRecord> findLayer(String clientId, String layerCode) {
            throw new SemanticLayerException("ConsumptionDao is not configured");
        }

        @Override
        public List<ConsumptionOutboundRecord> findExposures(String clientId, UUID objectId, String structureTypeCode) {
            throw new SemanticLayerException("ConsumptionDao is not configured");
        }

        @Override
        public java.util.Optional<ConsumptionOutboundRecord> findExposure(String clientId, Long exposureId) {
            throw new SemanticLayerException("ConsumptionDao is not configured");
        }

        @Override
        public java.util.Optional<ConsumptionOutboundRecord> findExposure(Long exposureId) {
            throw new SemanticLayerException("ConsumptionDao is not configured");
        }

        @Override
        public java.util.Optional<ConsumptionPromotionRecord> findLatestPromotion(String clientId, Long exposureId) {
            throw new SemanticLayerException("ConsumptionDao is not configured");
        }

        @Override
        public ConsumptionPromotionRecord insertPromotionRequest(String clientId, Long outboundId, String sourceSdlcStatusCode, String targetSdlcStatusCode, String validationStatusCode, String opaDecisionCode, Long workflowTaskId, String promotionStatusCode, Integer versionNumber, OffsetDateTime createdTs, String createdBy, OffsetDateTime updatedTs, String updatedBy) {
            throw new SemanticLayerException("ConsumptionDao is not configured");
        }

        @Override
        public ConsumptionPromotionRecord applyPromotion(String clientId, Long id, String targetSdlcStatusCode, String validationStatusCode, String opaDecisionCode, String promotionStatusCode, OffsetDateTime appliedTs, String appliedBy, OffsetDateTime updatedTs, String updatedBy) {
            throw new SemanticLayerException("ConsumptionDao is not configured");
        }

        @Override
        public FilterLookupWorkflowTaskRecord insertWorkflowTask(String clientId, String entityRef, String taskStatusCode, String submittedBy, OffsetDateTime submittedTs, String descriptionTxt, String approvedBy, OffsetDateTime approvedTs, String approvalNoteTxt) {
            throw new SemanticLayerException("ConsumptionDao is not configured");
        }

        @Override
        public void insertMetadataChangeHistory(String clientId, String entityTypeCode, String entityRef, String changeTypeCode, String changeReasonTxt, String changedBy, OffsetDateTime changedTs) {
            throw new SemanticLayerException("ConsumptionDao is not configured");
        }
    }

    private static final class NoOpTransactionOperations implements TransactionOperations {

        @Override
        public <T> T execute(TransactionCallback<T> action) {
            return action.doInTransaction(new NoOpTransactionStatus());
        }
    }

    private static final class NoOpTransactionStatus implements TransactionStatus {

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
}
