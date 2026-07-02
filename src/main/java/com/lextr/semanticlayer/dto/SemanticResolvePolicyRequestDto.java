package com.lextr.semanticlayer.dto;

public record SemanticResolvePolicyRequestDto(
        String policy_cd,
        String request_type_cd,
        String client_id,
        String actor_id,
        String role_cd,
        String purpose_cd,
        String resource_client_id,
        String resource_ref_txt
) {
}
