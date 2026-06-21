package com.lextr.semanticlayer.model;

import java.time.OffsetDateTime;

public record TenantWorkspaceRecord(
        Long id,
        String workspace_cd,
        String tenant_cd,
        String workspace_nm,
        String workspace_desc,
        String workspace_status_cd,
        String created_by,
        OffsetDateTime created_ts,
        String updated_by,
        OffsetDateTime updated_ts
) {
}
