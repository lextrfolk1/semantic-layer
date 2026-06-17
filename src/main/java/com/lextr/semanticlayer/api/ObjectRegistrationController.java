package com.lextr.semanticlayer.api;

import com.lextr.semanticlayer.dto.ObjectExposureDetailDto;
import com.lextr.semanticlayer.dto.ObjectExposureSummaryDto;
import com.lextr.semanticlayer.dto.ObjectRegistrationRequestDto;
import com.lextr.semanticlayer.dto.ObjectRegistrationResponseDto;
import com.lextr.semanticlayer.service.ObjectExposureReadService;
import com.lextr.semanticlayer.service.ObjectRegistrationService;
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
    public ObjectRegistrationResponseDto registerObject(@Valid @RequestBody ObjectRegistrationRequestDto request) {
        return objectRegistrationService.registerObject(request);
    }

    @GetMapping
    public List<ObjectExposureSummaryDto> findObjects(
            @RequestParam("client_id") String clientId,
            @RequestParam(value = "schema_cd", required = false) String schemaCode,
            @RequestParam(value = "lifecycle_status_cd", required = false) String lifecycleStatusCode) {
        return objectExposureReadService.findObjects(clientId, schemaCode, lifecycleStatusCode);
    }

    @GetMapping("/{object_id}")
    public ObjectExposureDetailDto findObject(
            @RequestParam("client_id") String clientId,
            @PathVariable("object_id") UUID objectId) {
        return objectExposureReadService.findObject(clientId, objectId);
    }
}
