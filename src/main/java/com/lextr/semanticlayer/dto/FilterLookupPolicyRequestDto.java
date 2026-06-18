package com.lextr.semanticlayer.dto;

public record FilterLookupPolicyRequestDto(
        String client_id,
        String lookup_cd,
        String policy_cd,
        Integer review_period_floor_days,
        Integer review_period_days_override
) {
}
