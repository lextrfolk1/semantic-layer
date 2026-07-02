package com.lextr.semanticlayer.dto;

import java.util.List;

public record ObjectExposureClassificationPolicyDecisionDto(
        boolean allowed,
        boolean masked,
        boolean withheld,
        String mask_value_txt,
        List<String> masked_fields,
        String code,
        String message
) {
}
