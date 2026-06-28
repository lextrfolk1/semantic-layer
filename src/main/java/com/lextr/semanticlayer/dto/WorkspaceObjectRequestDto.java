package com.lextr.semanticlayer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record WorkspaceObjectRequestDto(
        @NotBlank(message = "schema_cd is required")
        @Size(max = 100, message = "schema_cd must be 100 characters or less")
        String schema_cd,

        @NotBlank(message = "object_cd is required")
        @Size(max = 100, message = "object_cd must be 100 characters or less")
        String object_cd,

        @Size(max = 100, message = "added_by must be 100 characters or less")
        String added_by
) {
}
