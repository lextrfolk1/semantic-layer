package com.lextr.semanticlayer.service;

import com.lextr.semanticlayer.dto.ConsumptionExposureDto;
import com.lextr.semanticlayer.dto.ConsumptionLayerDto;
import com.lextr.semanticlayer.dto.ConsumptionPromotionRequestDto;

import java.util.List;
import java.util.UUID;

public interface ConsumptionService {

    List<ConsumptionLayerDto> findLayers(String clientId, String lifecycleStatusCode);

    ConsumptionLayerDto findLayer(String clientId, String layerCode);

    List<ConsumptionExposureDto> findExposures(String clientId, UUID objectId, String structureTypeCode);

    ConsumptionExposureDto promoteExposure(String clientId, Long exposureId, ConsumptionPromotionRequestDto request);
}
