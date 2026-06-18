package com.lextr.semanticlayer.service;

import com.lextr.semanticlayer.dto.FilterLookupCertificationPolicyRequestDto;
import com.lextr.semanticlayer.dto.FilterLookupPolicyDecisionDto;
import com.lextr.semanticlayer.dto.FilterLookupPreviewPolicyRequestDto;
import com.lextr.semanticlayer.dto.FilterLookupPolicyRequestDto;

public interface FilterLookupPolicyClient {

    FilterLookupPolicyDecisionDto validateReviewPeriodFloor(FilterLookupPolicyRequestDto request);

    default FilterLookupPolicyDecisionDto validatePreviewExecution(FilterLookupPreviewPolicyRequestDto request) {
        return new FilterLookupPolicyDecisionDto(true, null, null);
    }

    default FilterLookupPolicyDecisionDto validateCertification(FilterLookupCertificationPolicyRequestDto request) {
        return new FilterLookupPolicyDecisionDto(true, null, null);
    }
}
