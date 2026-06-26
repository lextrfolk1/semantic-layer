package com.lextr.semanticlayer.service;

import com.lextr.semanticlayer.dto.ExternalRuleResultIngestRequestDto;
import com.lextr.semanticlayer.dto.RuleResultIngestResponseDto;

public interface RuleResultService {

    RuleResultIngestResponseDto ingestRuleResult(ExternalRuleResultIngestRequestDto request, String principalCd);
}
