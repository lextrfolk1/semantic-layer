package com.lextr.semanticlayer.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record SemanticResolveRequestDto(
        @NotBlank String client_id,
        @NotBlank String schema_cd,
        @NotBlank String object_cd,
        List<String> logical_attribute_cd
) {
}
