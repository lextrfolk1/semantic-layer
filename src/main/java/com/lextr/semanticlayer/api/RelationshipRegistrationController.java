package com.lextr.semanticlayer.api;

import com.lextr.semanticlayer.dto.RelationshipRegistrationRequestDto;
import com.lextr.semanticlayer.dto.RelationshipRegistrationResponseDto;
import com.lextr.semanticlayer.service.RelationshipRegistrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/relationships")
@Tag(name = "Relationships", description = "Relationship registration operations.")
public class RelationshipRegistrationController {

    private final RelationshipRegistrationService relationshipRegistrationService;

    public RelationshipRegistrationController(RelationshipRegistrationService relationshipRegistrationService) {
        this.relationshipRegistrationService = relationshipRegistrationService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register relationship", description = "Registers a semantic relationship between two objects.")
    public RelationshipRegistrationResponseDto registerRelationship(
            @Valid @RequestBody RelationshipRegistrationRequestDto request) {
        return relationshipRegistrationService.registerRelationship(request);
    }
}
