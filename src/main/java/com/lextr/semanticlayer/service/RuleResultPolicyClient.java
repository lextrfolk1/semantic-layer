package com.lextr.semanticlayer.service;

import com.lextr.semanticlayer.dto.ExternalRuleResultIngestRequestDto;
import com.lextr.semanticlayer.dto.RuleResultPolicyDecisionDto;

public interface RuleResultPolicyClient {

    default RuleResultPolicyDecisionDto validateIngest(ExternalRuleResultIngestRequestDto request, String principalCd) {
        return new RuleResultPolicyDecisionDto(true, null, null);
    }
}
