package com.lextr.semanticlayer.service;

import com.lextr.semanticlayer.dto.RelationshipRegistrationRequestDto;
import com.lextr.semanticlayer.dto.RelationshipRegistrationResponseDto;

public interface RelationshipRegistrationService {

    RelationshipRegistrationResponseDto registerRelationship(RelationshipRegistrationRequestDto request);
}
