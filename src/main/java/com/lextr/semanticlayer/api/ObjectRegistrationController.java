package com.lextr.semanticlayer.api;

import com.lextr.semanticlayer.dto.ObjectExposureDetailDto;
import com.lextr.semanticlayer.dto.ObjectExposureSummaryDto;
import com.lextr.semanticlayer.dto.ObjectRegistrationRequestDto;
import com.lextr.semanticlayer.dto.ObjectRegistrationResponseDto;
import com.lextr.semanticlayer.service.ObjectExposureReadService;
import com.lextr.semanticlayer.service.ObjectRegistrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/objects")
@Tag(name = "Objects", description = "Object registration and object exposure operations.")
public class ObjectRegistrationController {

    private static final Logger logger = LoggerFactory.getLogger(ObjectRegistrationController.class);

    private final ObjectRegistrationService objectRegistrationService;
    private final ObjectExposureReadService objectExposureReadService;

    public ObjectRegistrationController(ObjectRegistrationService objectRegistrationService,
                                        ObjectExposureReadService objectExposureReadService) {
        this.objectRegistrationService = objectRegistrationService;
        this.objectExposureReadService = objectExposureReadService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register object", description = "Registers an object and its attributes for the semantic layer.")
    public ObjectRegistrationResponseDto registerObject(@Valid @RequestBody ObjectRegistrationRequestDto request) {
        logger.debug(
                "Registering object. clientId={}, objectCode={}, objectTypeCode={}, schemaCode={}, attributeCount={}",
                request.client_id(),
                request.object_cd(),
                request.object_type_cd(),
                request.schema_cd(),
                request.attributes().size()
        );
        ObjectRegistrationResponseDto response = objectRegistrationService.registerObject(request);
        logger.debug("Object registered. clientId={}, objectId={}", request.client_id(), response.object_id());
        return response;
    }

    @GetMapping
    @Operation(summary = "List objects", description = "Returns registered objects visible for the supplied client.")
    public List<ObjectExposureSummaryDto> findObjects(
            @Parameter(description = "Tenant identifier.") @RequestParam("client_id") String clientId,
            @Parameter(description = "Optional actor identifier propagated by the gateway.")
            @RequestHeader(value = "X-Actor-Id", required = false) String actorId,
            @Parameter(description = "Optional role code propagated by the gateway.")
            @RequestHeader(value = "X-Role-Cd", required = false) String roleCode,
            @Parameter(description = "Optional purpose code propagated by the gateway.")
            @RequestHeader(value = "X-Purpose-Cd", required = false) String purposeCode,
            @Parameter(description = "Optional schema filter.") @RequestParam(value = "schema_cd", required = false) String schemaCode,
            @Parameter(description = "Optional lifecycle status filter.") @RequestParam(value = "lifecycle_status_cd", required = false) String lifecycleStatusCode) {
        logger.debug(
                "Listing objects. clientId={}, actorPresent={}, roleCode={}, purposeCode={}, schemaCode={}, lifecycleStatusCode={}",
                clientId,
                actorId != null && !actorId.isBlank(),
                roleCode,
                purposeCode,
                schemaCode,
                lifecycleStatusCode
        );
        List<ObjectExposureSummaryDto> objects =
                objectExposureReadService.findObjects(clientId, actorId, roleCode, purposeCode, schemaCode, lifecycleStatusCode);
        logger.debug("Objects resolved. clientId={}, resultCount={}", clientId, objects.size());
        return objects;
    }

    @GetMapping("/{object_id}")
    @Operation(summary = "Get object", description = "Returns one object with its attributes for the supplied client.")
    public ObjectExposureDetailDto findObject(
            @Parameter(description = "Tenant identifier.") @RequestParam("client_id") String clientId,
            @Parameter(description = "Optional actor identifier propagated by the gateway.")
            @RequestHeader(value = "X-Actor-Id", required = false) String actorId,
            @Parameter(description = "Optional role code propagated by the gateway.")
            @RequestHeader(value = "X-Role-Cd", required = false) String roleCode,
            @Parameter(description = "Optional purpose code propagated by the gateway.")
            @RequestHeader(value = "X-Purpose-Cd", required = false) String purposeCode,
            @Parameter(description = "Object identifier.") @PathVariable("object_id") UUID objectId) {
        logger.debug(
                "Fetching object. clientId={}, objectId={}, actorPresent={}, roleCode={}, purposeCode={}",
                clientId,
                objectId,
                actorId != null && !actorId.isBlank(),
                roleCode,
                purposeCode
        );
        ObjectExposureDetailDto object = objectExposureReadService.findObject(clientId, actorId, roleCode, purposeCode, objectId);
        logger.debug("Object resolved. clientId={}, objectId={}", clientId, objectId);
        return object;
    }
}
