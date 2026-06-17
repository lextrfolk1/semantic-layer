package com.lextr.semanticlayer.api;

import com.lextr.semanticlayer.dto.RelationshipRegistrationRequestDto;
import com.lextr.semanticlayer.dto.RelationshipRegistrationResponseDto;
import com.lextr.semanticlayer.service.RelationshipRegistrationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/relationships")
public class RelationshipRegistrationController {

    private final RelationshipRegistrationService relationshipRegistrationService;

    public RelationshipRegistrationController(RelationshipRegistrationService relationshipRegistrationService) {
        this.relationshipRegistrationService = relationshipRegistrationService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RelationshipRegistrationResponseDto registerRelationship(
            @Valid @RequestBody RelationshipRegistrationRequestDto request) {
        return relationshipRegistrationService.registerRelationship(request);
    }
}
