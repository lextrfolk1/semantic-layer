package com.lextr.semanticlayer.api;

import com.lextr.semanticlayer.dto.ObjectRegistrationRequestDto;
import com.lextr.semanticlayer.dto.ObjectRegistrationResponseDto;
import com.lextr.semanticlayer.service.ObjectRegistrationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/objects")
public class ObjectRegistrationController {

    private final ObjectRegistrationService objectRegistrationService;

    public ObjectRegistrationController(ObjectRegistrationService objectRegistrationService) {
        this.objectRegistrationService = objectRegistrationService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ObjectRegistrationResponseDto registerObject(@Valid @RequestBody ObjectRegistrationRequestDto request) {
        return objectRegistrationService.registerObject(request);
    }
}
