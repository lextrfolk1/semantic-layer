package com.lextr.semanticlayer.service;

import com.lextr.semanticlayer.dto.ObjectExposureAccessPolicyRequestDto;
import com.lextr.semanticlayer.dto.ObjectExposureClassificationPolicyDecisionDto;
import com.lextr.semanticlayer.dto.ObjectExposureClassificationPolicyRequestDto;
import com.lextr.semanticlayer.dto.ObjectExposurePolicyDecisionDto;

public interface ObjectExposurePolicyClient {

    ObjectExposurePolicyDecisionDto evaluateAccess(ObjectExposureAccessPolicyRequestDto request);

    default ObjectExposureClassificationPolicyDecisionDto evaluateClassification(
            ObjectExposureClassificationPolicyRequestDto request
    ) {
        return new ObjectExposureClassificationPolicyDecisionDto(true, false, false, null, java.util.List.of(), null, null);
    }
}
