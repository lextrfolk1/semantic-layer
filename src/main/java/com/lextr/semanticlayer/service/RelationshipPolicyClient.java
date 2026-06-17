package com.lextr.semanticlayer.service;

import com.lextr.semanticlayer.dto.RelationshipPolicyDecisionDto;
import com.lextr.semanticlayer.dto.RelationshipPolicyRequestDto;

public interface RelationshipPolicyClient {

    RelationshipPolicyDecisionDto validateCrossEngine(RelationshipPolicyRequestDto request);
}
