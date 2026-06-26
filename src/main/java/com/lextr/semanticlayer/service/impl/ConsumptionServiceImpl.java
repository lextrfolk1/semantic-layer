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
import com.lextr.semanticlayer.service.ConsumptionService;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Service
public class ConsumptionServiceImpl implements ConsumptionService {

    private static final String ENTITY_TYPE_CD = "CONSUMPTION_EXPOSURE";
    private static final String CHANGE_TYPE_CD = "PROMOTED";
    private static final String WORKFLOW_STATUS_CD = "PENDING";
    private static final String PROMOTION_STATUS_CD = "PENDING_APPROVAL";
    private static final String VALIDATION_STATUS_CD = "PENDING";
    private static final String OPA_DECISION_CD = "PENDING";

    private final ObjectExposureReadDao objectExposureReadDao;
    private final ConsumptionDao consumptionDao;

    public ConsumptionServiceImpl(ObjectExposureReadDao objectExposureReadDao,
                                  ConsumptionDao consumptionDao) {
        this.objectExposureReadDao = objectExposureReadDao;
        this.consumptionDao = consumptionDao;
    }

    @Override
    public List<ConsumptionLayerDto> findLayers(String clientId, String lifecycleStatusCode) {
        return consumptionDao.findLayers(clientId, lifecycleStatusCode).stream()
                .map(this::toLayerDto)
                .toList();
    }

    @Override
    public ConsumptionLayerDto findLayer(String clientId, String layerCode) {
        ConsumptionLayerRecord record = consumptionDao.findLayer(clientId, layerCode)
                .orElseThrow(() -> new RegistryResourceNotFoundException("consumption layer", layerCode));
        return toLayerDto(record);
    }

    @Override
    public List<ConsumptionExposureDto> findExposures(String clientId, UUID objectId, String structureTypeCode) {
        ObjectExposureRecord object = objectExposureReadDao.findObject(clientId, objectId)
                .orElseThrow(() -> new RegistryResourceNotFoundException("object", objectId.toString()));
        return consumptionDao.findExposures(clientId, object.object_id(), structureTypeCode).stream()
                .map(this::toExposureDto)
                .toList();
    }

    @Override
    public ConsumptionExposureDto promoteExposure(String clientId, Long exposureId, ConsumptionPromotionRequestDto request) {
        ConsumptionOutboundRecord exposure = consumptionDao.findExposure(clientId, exposureId)
                .orElseThrow(() -> new RegistryResourceNotFoundException("consumption exposure", exposureId.toString()));

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        ConsumptionPromotionRecord latestPromotion = consumptionDao.findLatestPromotion(clientId, exposureId).orElse(null);
        Integer versionNumber = latestPromotion == null ? 1 : latestPromotion.version_nbr() + 1;

        ConsumptionPromotionRecord createdPromotion = consumptionDao.insertPromotionRequest(
                clientId,
                exposureId,
                exposure.sdlc_status_cd(),
                request.target_sdlc_status_cd(),
                VALIDATION_STATUS_CD,
                OPA_DECISION_CD,
                null,
                PROMOTION_STATUS_CD,
                versionNumber,
                now,
                request.promoted_by(),
                now,
                request.promoted_by()
        );

        consumptionDao.insertWorkflowTask(
                clientId,
                exposureId.toString(),
                WORKFLOW_STATUS_CD,
                request.promoted_by(),
                now,
                request.promotion_reason_txt() == null || request.promotion_reason_txt().isBlank()
                        ? "Promote outbound exposure " + exposure.outbound_cd()
                        : request.promotion_reason_txt(),
                null,
                null,
                null
        );
        consumptionDao.insertMetadataChangeHistory(
                clientId,
                ENTITY_TYPE_CD,
                exposureId.toString(),
                CHANGE_TYPE_CD,
                request.promotion_reason_txt() == null || request.promotion_reason_txt().isBlank()
                        ? "Promoted outbound exposure " + exposure.outbound_cd() + " to " + request.target_sdlc_status_cd()
                        : request.promotion_reason_txt(),
                request.promoted_by(),
                now
        );

        return toExposureDto(exposure, request.target_sdlc_status_cd(), createdPromotion.version_nbr());
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
}
