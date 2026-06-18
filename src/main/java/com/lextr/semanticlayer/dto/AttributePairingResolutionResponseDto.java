package com.lextr.semanticlayer.dto;

public record AttributePairingResolutionResponseDto(
        String pairing_cd,
        String schema_cd,
        String object_cd,
        String display_attribute_cd,
        String filter_attribute_cd,
        String display_value_txt,
        String filter_value_txt,
        boolean is_one_to_many_flg,
        boolean cache_hit_flg
) {
}
