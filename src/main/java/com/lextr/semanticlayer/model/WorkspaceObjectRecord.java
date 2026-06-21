package com.lextr.semanticlayer.model;

import java.time.OffsetDateTime;

public record WorkspaceObjectRecord(
        Long id,
        String workspace_cd,
        String schema_cd,
        String object_cd,
        String added_by,
        OffsetDateTime added_ts
) {
}
