package com.lextr.semanticlayer.api;

import com.lextr.semanticlayer.dto.LogicalPhysicalResolutionDto;
import com.lextr.semanticlayer.service.LogicalPhysicalResolutionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/logical-physical-resolutions")
@Tag(name = "Logical Physical Resolutions", description = "Logical-to-physical resolution reads for downstream engines.")
public class LogicalPhysicalResolutionController {

    private static final Logger logger = LoggerFactory.getLogger(LogicalPhysicalResolutionController.class);

    private final LogicalPhysicalResolutionService logicalPhysicalResolutionService;

    public LogicalPhysicalResolutionController(LogicalPhysicalResolutionService logicalPhysicalResolutionService) {
        this.logicalPhysicalResolutionService = logicalPhysicalResolutionService;
    }

    @GetMapping("/attributes")
    @Operation(summary = "Resolve logical attributes", description = "Returns governed logical-to-physical mappings for a set of logical attributes.")
    public List<LogicalPhysicalResolutionDto> resolveAttributes(
            @Parameter(description = "Tenant identifier.") @RequestParam("client_id") String clientId,
            @Parameter(description = "Schema code.") @RequestParam("schema_cd") String schemaCode,
            @Parameter(description = "Object code.") @RequestParam("object_cd") String objectCode,
            @Parameter(description = "Logical attribute codes.") @RequestParam("logical_attribute_cd") List<String> logicalAttributeCodes) {
        logger.debug(
                "Resolving logical attributes. clientId={}, schemaCode={}, objectCode={}, logicalAttributeCount={}",
                clientId,
                schemaCode,
                objectCode,
                logicalAttributeCodes == null ? 0 : logicalAttributeCodes.size()
        );
        List<LogicalPhysicalResolutionDto> resolutions =
                logicalPhysicalResolutionService.resolveAttributes(clientId, schemaCode, objectCode, logicalAttributeCodes);
        logger.debug(
                "Logical attributes resolved. clientId={}, schemaCode={}, objectCode={}, resultCount={}",
                clientId,
                schemaCode,
                objectCode,
                resolutions.size()
        );
        return resolutions;
    }

    @GetMapping("/outbounds/{outbound_id}")
    @Operation(summary = "Resolve outbound grain", description = "Returns governed logical-to-physical mappings for one consumption outbound grain.")
    public List<LogicalPhysicalResolutionDto> resolveOutboundGrain(
            @Parameter(description = "Tenant identifier.") @RequestParam("client_id") String clientId,
            @Parameter(description = "Outbound identifier.") @PathVariable("outbound_id") Long outboundId) {
        logger.debug("Resolving outbound grain. clientId={}, outboundId={}", clientId, outboundId);
        List<LogicalPhysicalResolutionDto> resolutions = logicalPhysicalResolutionService.resolveOutboundGrain(clientId, outboundId);
        logger.debug("Outbound grain resolved. clientId={}, outboundId={}, resultCount={}", clientId, outboundId, resolutions.size());
        return resolutions;
    }
}
