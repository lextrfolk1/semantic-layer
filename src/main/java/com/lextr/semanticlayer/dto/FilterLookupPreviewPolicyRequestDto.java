package com.lextr.semanticlayer.dto;

public record FilterLookupPreviewPolicyRequestDto(
        String client_id,
        String executed_by,
        String lookup_cd,
        String construction_type_cd,
        String execution_strategy_cd
) {
}
