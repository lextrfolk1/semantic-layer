package com.lextr.semanticlayer.model;

import java.time.OffsetDateTime;

public record LogicalHierarchyRecord(
        Long id,
        String hierarchy_cd,
        String hierarchy_nm,
        String tenant_cd,
        String hierarchy_status_cd,
        String created_by,
        OffsetDateTime created_ts,
        String updated_by,
        OffsetDateTime updated_ts
) {
}
