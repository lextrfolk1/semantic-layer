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
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
        return objectRegistrationService.registerObject(request);
    }

    @GetMapping
    @Operation(summary = "List objects", description = "Returns registered objects visible for the supplied client.")
    public List<ObjectExposureSummaryDto> findObjects(
            @Parameter(description = "Tenant identifier.") @RequestParam("client_id") String clientId,
            @Parameter(description = "Optional schema filter.") @RequestParam(value = "schema_cd", required = false) String schemaCode,
            @Parameter(description = "Optional lifecycle status filter.") @RequestParam(value = "lifecycle_status_cd", required = false) String lifecycleStatusCode) {
        return objectExposureReadService.findObjects(clientId, schemaCode, lifecycleStatusCode);
    }

    @GetMapping("/{object_id}")
    @Operation(summary = "Get object", description = "Returns one object with its attributes for the supplied client.")
    public ObjectExposureDetailDto findObject(
            @Parameter(description = "Tenant identifier.") @RequestParam("client_id") String clientId,
            @Parameter(description = "Object identifier.") @PathVariable("object_id") UUID objectId) {
        return objectExposureReadService.findObject(clientId, objectId);
    }
}
