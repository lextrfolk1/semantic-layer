package com.lextr.semanticlayer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record FilterLookupRegistrationRequestDto(
        @NotBlank
        @Size(max = 40)
        String client_id,
        @NotBlank
        @Size(max = 60)
        String lookup_cd,
        @NotBlank
        @Size(max = 20)
        String construction_type_cd,
        @Size(max = 30)
        String manual_subtype_cd,
        @Size(max = 120)
        String filter_obj,
        String filter_condition_txt,
        @Size(max = 32)
        String filter_attr_cd,
        @Size(max = 120)
        String validation_obj,
        @Size(max = 32)
        String validation_attr_cd,
        @Size(max = 32)
        String suggested_target_attr_cd,
        @NotBlank
        @Size(max = 20)
        String execution_strategy_cd,
        Integer max_input_set_size,
        Integer max_output_rows,
        Integer cache_ttl_min,
        Integer review_period_days_override,
        Boolean rules_eligible_flg,
        Boolean qs_eligible_flg,
        Boolean ai_eligible_flg,
        Boolean replicate_to_ch_flg,
        String description_txt,
        @NotBlank
        @Size(max = 100)
        String registered_by
) {
}
