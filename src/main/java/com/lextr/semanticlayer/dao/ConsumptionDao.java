package com.lextr.semanticlayer.dao;

import com.lextr.semanticlayer.model.ConsumptionLayerRecord;
import com.lextr.semanticlayer.model.ConsumptionOutboundRecord;
import com.lextr.semanticlayer.model.ConsumptionPromotionRecord;
import com.lextr.semanticlayer.model.ConsumptionLayerWriteRequest;
import com.lextr.semanticlayer.model.ConsumptionOutboundGrainWriteRequest;
import com.lextr.semanticlayer.model.ConsumptionOutboundWriteRequest;
import com.lextr.semanticlayer.model.FilterLookupWorkflowTaskRecord;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConsumptionDao {

    ConsumptionLayerRecord insertLayer(ConsumptionLayerWriteRequest request);

    ConsumptionOutboundRecord insertOutbound(ConsumptionOutboundWriteRequest request);

    void insertOutboundGrain(ConsumptionOutboundGrainWriteRequest request);

    List<ConsumptionLayerRecord> findLayers(String clientId, String lifecycleStatusCode);

    Optional<ConsumptionLayerRecord> findLayer(String clientId, String layerCode);

    List<ConsumptionOutboundRecord> findExposures(String clientId, UUID objectId, String structureTypeCode);

    Optional<ConsumptionOutboundRecord> findExposure(String clientId, Long exposureId);

    Optional<ConsumptionPromotionRecord> findLatestPromotion(String clientId, Long exposureId);

    ConsumptionPromotionRecord insertPromotionRequest(String clientId,
                                                      Long outboundId,
                                                      String sourceSdlcStatusCode,
                                                      String targetSdlcStatusCode,
                                                      String validationStatusCode,
                                                      String opaDecisionCode,
                                                      Long workflowTaskId,
                                                      String promotionStatusCode,
                                                      Integer versionNumber,
                                                      OffsetDateTime createdTs,
                                                      String createdBy,
                                                      OffsetDateTime updatedTs,
                                                      String updatedBy);

    ConsumptionPromotionRecord applyPromotion(String clientId,
                                              Long id,
                                              String targetSdlcStatusCode,
                                              String validationStatusCode,
                                              String opaDecisionCode,
                                              String promotionStatusCode,
                                              OffsetDateTime appliedTs,
                                              String appliedBy,
                                              OffsetDateTime updatedTs,
                                              String updatedBy);

    FilterLookupWorkflowTaskRecord insertWorkflowTask(String clientId,
                                                      String entityRef,
                                                      String taskStatusCode,
                                                      String submittedBy,
                                                      OffsetDateTime submittedTs,
                                                      String descriptionTxt,
                                                      String approvedBy,
                                                      OffsetDateTime approvedTs,
                                                      String approvalNoteTxt);

    void insertMetadataChangeHistory(String clientId,
                                     String entityTypeCode,
                                     String entityRef,
                                     String changeTypeCode,
                                     String changeReasonTxt,
                                     String changedBy,
                                     OffsetDateTime changedTs);
}
