package com.lextr.semanticlayer.api;

import com.lextr.semanticlayer.dto.ConsumptionExposureDto;
import com.lextr.semanticlayer.dto.ConsumptionLayerDto;
import com.lextr.semanticlayer.dto.ConsumptionPromotionRequestDto;
import com.lextr.semanticlayer.service.ConsumptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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

    private final ConsumptionService consumptionService;

    public ConsumptionController(ConsumptionService consumptionService) {
        this.consumptionService = consumptionService;
    }

    @GetMapping("/layers")
    @Operation(summary = "List consumption layers", description = "Returns consumption layers for the supplied client.")
    public List<ConsumptionLayerDto> findLayers(
            @Parameter(description = "Tenant identifier.") @RequestParam("client_id") String clientId,
            @Parameter(description = "Optional lifecycle status filter.") @RequestParam(value = "lifecycle_status_cd", required = false) String lifecycleStatusCode) {
        return consumptionService.findLayers(clientId, lifecycleStatusCode);
    }

    @GetMapping("/layers/{layer_cd}")
    @Operation(summary = "Get consumption layer", description = "Returns one consumption layer for the supplied client.")
    public ConsumptionLayerDto findLayer(
            @Parameter(description = "Tenant identifier.") @RequestParam("client_id") String clientId,
            @Parameter(description = "Layer code.") @PathVariable("layer_cd") String layerCode) {
        return consumptionService.findLayer(clientId, layerCode);
    }

    @GetMapping("/exposures")
    @Operation(summary = "List consumption exposures", description = "Returns consumption exposures for the supplied object and client.")
    public List<ConsumptionExposureDto> findExposures(
            @Parameter(description = "Tenant identifier.") @RequestParam("client_id") String clientId,
            @Parameter(description = "Object identifier.") @RequestParam("object_id") UUID objectId,
            @Parameter(description = "Optional structure type filter.") @RequestParam(value = "structure_type_cd", required = false) String structureTypeCode) {
        return consumptionService.findExposures(clientId, objectId, structureTypeCode);
    }

    @PostMapping("/exposures/{exposure_id}/promote")
    @Operation(summary = "Promote consumption exposure", description = "Submits a promotion request for one outbound exposure.")
    public ConsumptionExposureDto promoteExposure(
            @Parameter(description = "Tenant identifier.") @RequestParam("client_id") String clientId,
            @Parameter(description = "Exposure identifier.") @PathVariable("exposure_id") Long exposureId,
            @Valid @RequestBody ConsumptionPromotionRequestDto request) {
        return consumptionService.promoteExposure(clientId, exposureId, request);
    }
}
