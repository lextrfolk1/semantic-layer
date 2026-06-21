package com.lextr.semanticlayer.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record TenantWorkspaceDto(
        Long id,
        String workspace_cd,
        String tenant_cd,
        String workspace_nm,
        String workspace_desc,
        String workspace_status_cd,
        String created_by,
        OffsetDateTime created_ts,
        String updated_by,
        OffsetDateTime updated_ts,
        List<WorkspaceObjectDto> objects
) {
    public record WorkspaceObjectDto(
            String schema_cd,
            String object_cd,
            String added_by,
            OffsetDateTime added_ts
    ) {
    }
}
