package com.lextr.semanticlayer.dto;

public record FilterLookupCertificationPolicyRequestDto(
        String client_id,
        String lookup_cd,
        String certified_by,
        String current_health_status_cd,
        long stale_value_count
) {
}
