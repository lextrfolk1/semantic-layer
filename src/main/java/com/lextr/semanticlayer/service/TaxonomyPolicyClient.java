package com.lextr.semanticlayer.service;

import com.lextr.semanticlayer.dto.TaxonomyPolicyDecisionDto;
import com.lextr.semanticlayer.dto.TaxonomyPolicyRequestDto;

public interface TaxonomyPolicyClient {

    TaxonomyPolicyDecisionDto validateJurisdiction(TaxonomyPolicyRequestDto request);
}
