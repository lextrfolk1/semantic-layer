package com.lextr.semanticlayer.service;

import com.lextr.semanticlayer.dto.FilterLookupPolicyDecisionDto;
import com.lextr.semanticlayer.dto.FilterLookupPolicyRequestDto;

public interface FilterLookupPolicyClient {

    FilterLookupPolicyDecisionDto validateReviewPeriodFloor(FilterLookupPolicyRequestDto request);
}
