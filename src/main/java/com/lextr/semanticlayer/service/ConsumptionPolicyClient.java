package com.lextr.semanticlayer.service;

import com.lextr.semanticlayer.dto.ConsumptionPolicyDecisionDto;
import com.lextr.semanticlayer.dto.ConsumptionPolicyRequestDto;

public interface ConsumptionPolicyClient {

    default ConsumptionPolicyDecisionDto validatePromotion(ConsumptionPolicyRequestDto request) {
        return new ConsumptionPolicyDecisionDto(true, null, null);
    }
}
