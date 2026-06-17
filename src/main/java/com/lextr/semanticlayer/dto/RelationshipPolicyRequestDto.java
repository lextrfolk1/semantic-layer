package com.lextr.semanticlayer.dto;

public record RelationshipPolicyRequestDto(
        String client_id,
        String relationship_cd,
        String parent_engine_cd,
        String child_engine_cd,
        boolean is_cross_engine_flg
) {
}
