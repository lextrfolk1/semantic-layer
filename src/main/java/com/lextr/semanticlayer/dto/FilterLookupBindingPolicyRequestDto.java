package com.lextr.semanticlayer.dto;

public record FilterLookupBindingPolicyRequestDto(
        String client_id,
        String lookup_cd,
        String binding_context_cd,
        boolean is_overdue
) {
}
