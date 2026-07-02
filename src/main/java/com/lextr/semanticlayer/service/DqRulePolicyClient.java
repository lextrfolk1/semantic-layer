package com.lextr.semanticlayer.service;

import com.lextr.semanticlayer.dto.DqRulePolicyDecisionDto;
import com.lextr.semanticlayer.dto.DqRuleRequestDto;
import com.lextr.semanticlayer.dto.DqRuleResultIngestRequestDto;

public interface DqRulePolicyClient {

    default DqRulePolicyDecisionDto validateRequest(DqRuleRequestDto request) {
        return new DqRulePolicyDecisionDto(true, null, null);
    }

    default DqRulePolicyDecisionDto validateResultIngest(DqRuleResultIngestRequestDto request, String principalCd) {
        return new DqRulePolicyDecisionDto(true, null, null);
    }
}
