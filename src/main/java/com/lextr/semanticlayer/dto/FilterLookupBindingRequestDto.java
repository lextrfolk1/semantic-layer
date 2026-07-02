package com.lextr.semanticlayer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record FilterLookupBindingRequestDto(
        @NotBlank
        @Size(max = 40)
        String client_id,
        @NotBlank
        @Size(max = 120)
        String bound_obj,
        @NotBlank
        @Size(max = 32)
        String bound_attr_cd,
        @NotBlank
        @Size(max = 20)
        String binding_context_cd,
        @Size(max = 100)
        String binding_ref,
        @NotBlank
        @Size(max = 32)
        String bound_by
) {
}
