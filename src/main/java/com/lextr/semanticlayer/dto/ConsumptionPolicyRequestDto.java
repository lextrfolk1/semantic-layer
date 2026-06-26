package com.lextr.semanticlayer.dto;

public record ConsumptionPolicyRequestDto(
        String client_id,
        Long exposure_id,
        String source_sdlc_status_cd,
        String target_sdlc_status_cd,
        String promoted_by,
        String promotion_reason_txt
) {
}
