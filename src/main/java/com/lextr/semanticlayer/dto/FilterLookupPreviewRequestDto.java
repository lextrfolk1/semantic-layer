package com.lextr.semanticlayer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record FilterLookupPreviewRequestDto(
        @NotBlank
        @Size(max = 40)
        String client_id,
        @NotBlank
        @Size(max = 100)
        String executed_by,
        @NotEmpty
        @Size(max = 32)
        List<@NotBlank @Size(max = 60) String> lookup_codes
) {
}
