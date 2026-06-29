package com.lextr.semanticlayer.api;

import com.lextr.semanticlayer.dto.ConsumptionExposureDto;
import com.lextr.semanticlayer.dto.ConsumptionLayerDto;
import com.lextr.semanticlayer.dto.ConsumptionPromotionRequestDto;
import com.lextr.semanticlayer.service.ConsumptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/consumption")
@Tag(name = "Consumption", description = "Consumption-layer exposure and SDLC promotion operations.")
public class ConsumptionController {

    private static final Logger logger = LoggerFactory.getLogger(ConsumptionController.class);

    private final ConsumptionService consumptionService;

    public ConsumptionController(ConsumptionService consumptionService) {
        this.consumptionService = consumptionService;
    }

    @GetMapping("/layers")
    @Operation(summary = "List consumption layers", description = "Returns consumption layers for the supplied client.")
    public List<ConsumptionLayerDto> findLayers(
            @Parameter(description = "Tenant identifier.") @RequestParam("client_id") String clientId,
            @Parameter(description = "Optional lifecycle status filter.") @RequestParam(value = "lifecycle_status_cd", required = false) String lifecycleStatusCode) {
        logger.debug("Listing consumption layers. clientId={}, lifecycleStatusCode={}", clientId, lifecycleStatusCode);
        List<ConsumptionLayerDto> layers = consumptionService.findLayers(clientId, lifecycleStatusCode);
        logger.debug("Consumption layers resolved. clientId={}, resultCount={}", clientId, layers.size());
        return layers;
    }

    @GetMapping("/layers/{layer_cd}")
    @Operation(summary = "Get consumption layer", description = "Returns one consumption layer for the supplied client.")
    public ConsumptionLayerDto findLayer(
            @Parameter(description = "Tenant identifier.") @RequestParam("client_id") String clientId,
            @Parameter(description = "Layer code.") @PathVariable("layer_cd") String layerCode) {
        logger.debug("Fetching consumption layer. clientId={}, layerCode={}", clientId, layerCode);
        ConsumptionLayerDto layer = consumptionService.findLayer(clientId, layerCode);
        logger.debug("Consumption layer resolved. clientId={}, layerCode={}", clientId, layerCode);
        return layer;
    }

    @GetMapping("/exposures")
    @Operation(summary = "List consumption exposures", description = "Returns consumption exposures for the supplied object and client.")
    public List<ConsumptionExposureDto> findExposures(
            @Parameter(description = "Tenant identifier.") @RequestParam("client_id") String clientId,
            @Parameter(description = "Object identifier.") @RequestParam("object_id") UUID objectId,
            @Parameter(description = "Optional structure type filter.") @RequestParam(value = "structure_type_cd", required = false) String structureTypeCode) {
        logger.debug(
                "Listing consumption exposures. clientId={}, objectId={}, structureTypeCode={}",
                clientId,
                objectId,
                structureTypeCode
        );
        List<ConsumptionExposureDto> exposures = consumptionService.findExposures(clientId, objectId, structureTypeCode);
        logger.debug("Consumption exposures resolved. clientId={}, objectId={}, resultCount={}", clientId, objectId, exposures.size());
        return exposures;
    }

    @PostMapping("/exposures/{exposure_id}/promote")
    @Operation(summary = "Promote consumption exposure", description = "Submits a promotion request for one outbound exposure.")
    public ConsumptionExposureDto promoteExposure(
            @Parameter(description = "Tenant identifier.") @RequestParam("client_id") String clientId,
            @Parameter(description = "Exposure identifier.") @PathVariable("exposure_id") Long exposureId,
            @Valid @RequestBody ConsumptionPromotionRequestDto request) {
        logger.debug(
                "Promoting consumption exposure. clientId={}, exposureId={}, targetSdlcStatusCode={}",
                clientId,
                exposureId,
                request.target_sdlc_status_cd()
        );
        ConsumptionExposureDto exposure = consumptionService.promoteExposure(clientId, exposureId, request);
        logger.info(
                "Consumption exposure promoted. clientId={}, exposureId={}, targetSdlcStatusCode={}",
                clientId,
                exposureId,
                request.target_sdlc_status_cd()
        );
        return exposure;
    }
}
