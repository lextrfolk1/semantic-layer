package com.lextr.semanticlayer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AttributePairingResolutionRequestDto(
        @NotBlank
        @Size(max = 40)
        String client_id,
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
        @Size(max = 500)
        String display_value_txt
) {
}
