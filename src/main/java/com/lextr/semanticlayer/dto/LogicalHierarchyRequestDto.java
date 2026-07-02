package com.lextr.semanticlayer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LogicalHierarchyRequestDto(
        @NotBlank(message = "hierarchy_cd is required")
        @Size(max = 60)
        String hierarchy_cd,

        @NotBlank(message = "hierarchy_nm is required")
        @Size(max = 200, message = "hierarchy_nm must be 200 characters or less")
        String hierarchy_nm,

        @Size(max = 40)
        String tenant_cd,

        @Size(max = 30)
        String hierarchy_status_cd,

        @Size(max = 100)
        String created_by
) {
}
