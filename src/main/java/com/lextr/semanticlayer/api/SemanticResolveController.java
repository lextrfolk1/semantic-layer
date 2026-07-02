package com.lextr.semanticlayer.api;

import com.lextr.semanticlayer.dto.LogicalPhysicalResolutionDto;
import com.lextr.semanticlayer.dto.SemanticResolveRequestDto;
import com.lextr.semanticlayer.exception.SemanticLayerException;
import com.lextr.semanticlayer.service.SemanticResolveService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/semantic")
@Tag(name = "Semantic Resolve", description = "Governed logical-to-physical resolution for downstream engines.")
public class SemanticResolveController {

    private static final Logger logger = LoggerFactory.getLogger(SemanticResolveController.class);

    private final SemanticResolveService semanticResolveService;

    public SemanticResolveController(SemanticResolveService semanticResolveService) {
        this.semanticResolveService = semanticResolveService;
    }

    @Autowired
    SemanticResolveController(ObjectProvider<SemanticResolveService> semanticResolveServiceProvider) {
        this(semanticResolveServiceProvider.getIfAvailable(MissingSemanticResolveService::new));
    }

    @PostMapping("/resolve")
    @Operation(summary = "Resolve semantic attributes", description = "Returns governed logical-to-physical mappings for a set of logical attributes.")
    public List<LogicalPhysicalResolutionDto> resolveAttributes(
            @Parameter(description = "Optional actor identifier propagated by the gateway.")
            @RequestHeader(value = "X-Actor-Id", required = false) String actorId,
            @Parameter(description = "Optional role code propagated by the gateway.")
            @RequestHeader(value = "X-Role-Cd", required = false) String roleCode,
            @Parameter(description = "Optional purpose code propagated by the gateway.")
            @RequestHeader(value = "X-Purpose-Cd", required = false) String purposeCode,
            @Valid @RequestBody SemanticResolveRequestDto request) {
        logger.debug(
                "Resolving semantic attributes. clientId={}, schemaCode={}, objectCode={}, logicalAttributeCount={}, actorSupplied={}, roleCode={}, purposeCode={}",
                request.client_id(),
                request.schema_cd(),
                request.object_cd(),
                request.logical_attribute_cd() == null ? 0 : request.logical_attribute_cd().size(),
                actorId != null && !actorId.isBlank(),
                roleCode,
                purposeCode
        );
        List<LogicalPhysicalResolutionDto> resolutions =
                semanticResolveService.resolveAttributes(request, actorId, roleCode, purposeCode);
        logger.debug(
                "Semantic attributes resolved. clientId={}, schemaCode={}, objectCode={}, resultCount={}",
                request.client_id(),
                request.schema_cd(),
                request.object_cd(),
                resolutions.size()
        );
        return resolutions;
    }

    private static final class MissingSemanticResolveService implements SemanticResolveService {

        @Override
        public List<LogicalPhysicalResolutionDto> resolveAttributes(SemanticResolveRequestDto request,
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
