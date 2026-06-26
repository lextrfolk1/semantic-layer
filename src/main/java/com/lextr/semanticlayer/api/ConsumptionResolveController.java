package com.lextr.semanticlayer.api;

import com.lextr.semanticlayer.dto.LogicalPhysicalResolutionDto;
import com.lextr.semanticlayer.exception.SemanticLayerException;
import com.lextr.semanticlayer.service.SemanticResolveService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/consumption")
@Tag(name = "Consumption Resolve", description = "Governed logical-to-physical resolution for consumption outbounds.")
public class ConsumptionResolveController {

    private final SemanticResolveService semanticResolveService;

    public ConsumptionResolveController(SemanticResolveService semanticResolveService) {
        this.semanticResolveService = semanticResolveService;
    }

    @Autowired
    ConsumptionResolveController(ObjectProvider<SemanticResolveService> semanticResolveServiceProvider) {
        this(semanticResolveServiceProvider.getIfAvailable(MissingSemanticResolveService::new));
    }

    @GetMapping("/{outbound_id}/resolve")
    @Operation(summary = "Resolve consumption outbound", description = "Returns governed logical-to-physical mappings for one consumption outbound.")
    public List<LogicalPhysicalResolutionDto> resolveOutboundGrain(
            @Parameter(description = "Tenant identifier.") @RequestParam("client_id") String clientId,
            @Parameter(description = "Optional actor identifier propagated by the gateway.")
            @RequestHeader(value = "X-Actor-Id", required = false) String actorId,
            @Parameter(description = "Optional role code propagated by the gateway.")
            @RequestHeader(value = "X-Role-Cd", required = false) String roleCode,
            @Parameter(description = "Optional purpose code propagated by the gateway.")
            @RequestHeader(value = "X-Purpose-Cd", required = false) String purposeCode,
            @Parameter(description = "Outbound identifier.") @PathVariable("outbound_id") Long outboundId) {
        return semanticResolveService.resolveOutboundGrain(clientId, actorId, roleCode, purposeCode, outboundId);
    }

    private static final class MissingSemanticResolveService implements SemanticResolveService {

        @Override
        public List<LogicalPhysicalResolutionDto> resolveAttributes(com.lextr.semanticlayer.dto.SemanticResolveRequestDto request,
                                                                    String actorId,
                                                                    String roleCode,
                                                                    String purposeCode) {
            throw new SemanticLayerException("SemanticResolveService is not configured");
        }

        @Override
        public List<LogicalPhysicalResolutionDto> resolveOutboundGrain(String clientId,
                                                                       String actorId,
                                                                       String roleCode,
                                                                       String purposeCode,
                                                                       Long outboundId) {
            throw new SemanticLayerException("SemanticResolveService is not configured");
        }
    }
}
