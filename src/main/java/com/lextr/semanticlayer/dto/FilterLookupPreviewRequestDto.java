package com.lextr.semanticlayer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record FilterLookupPreviewRequestDto(
        @NotBlank
        @Size(max = 40)
        String client_id,
        @NotBlank
        @Size(max = 60)
        String lookup_cd,
        @NotBlank
        @Size(max = 100)
        String executed_by
) {
}
