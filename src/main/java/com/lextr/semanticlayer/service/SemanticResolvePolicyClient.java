package com.lextr.semanticlayer.service;

import com.lextr.semanticlayer.dto.ObjectExposurePolicyDecisionDto;
import com.lextr.semanticlayer.dto.SemanticResolvePolicyRequestDto;

public interface SemanticResolvePolicyClient {

    ObjectExposurePolicyDecisionDto evaluateAccess(SemanticResolvePolicyRequestDto request);
}
