package com.lextr.semanticlayer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TenantWorkspaceRequestDto(
        @NotBlank(message = "workspace_cd is required")
        @Size(max = 60)
        String workspace_cd,

        @NotBlank(message = "tenant_cd is required")
        @Size(max = 40)
        String tenant_cd,

        @NotBlank(message = "workspace_nm is required")
        @Size(max = 200, message = "workspace_nm must be 200 characters or less")
        String workspace_nm,

        @Size(max = 1000)
        String workspace_desc,

        @Size(max = 30)
        String workspace_status_cd,

        @Size(max = 100)
        String created_by
) {
}
