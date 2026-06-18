package com.lextr.semanticlayer.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AttributePairingRegistrationRequestDto(
        @NotBlank
        @Size(max = 40)
        String client_id,
        @NotBlank
        @Size(max = 80)
        String pairing_cd,
        @NotBlank
        @Size(max = 200)
        String pairing_nm,
        @NotBlank
        @Size(max = 30)
        String schema_cd,
        @NotBlank
        @Size(max = 50)
        String object_cd,
        @NotBlank
        @Size(max = 32)
        String display_attribute_cd,
        @NotBlank
        @Size(max = 32)
        String filter_attribute_cd,
        @NotBlank
        @Size(max = 30)
        String pairing_type_cd,
        @NotBlank
        @Size(max = 30)
        String lookup_strategy_cd,
        String lookup_inline_map_jsonb,
        String lookup_sql_template_txt,
        Boolean lookup_cache_enabled_flg,
        Integer lookup_cache_ttl_seconds_nbr,
        @NotBlank
        @Size(max = 20)
        String cardinality_cd,
        boolean is_bidirectional_flg,
        boolean is_cross_engine_flg,
        Boolean filter_attribute_indexed_flg,
        @Size(max = 20)
        String filter_attribute_index_type_cd,
        Integer performance_gain_pct_est_nbr,
        String ai_context_txt,
        @NotBlank
        @Size(max = 100)
        String registered_by
) {
    @AssertTrue(message = "display_attribute_cd and filter_attribute_cd must differ")
    public boolean isDisplayAttributeDistinctFromFilterAttribute() {
        if (display_attribute_cd == null || filter_attribute_cd == null) {
            return true;
        }
        return !display_attribute_cd.equals(filter_attribute_cd);
    }
}
