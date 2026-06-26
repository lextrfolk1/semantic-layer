package com.lextr.semanticlayer.model;

import java.time.OffsetDateTime;

public record AttributeAccessGrantRecord(
        Long id,
        String client_id,
        String schema_cd,
        String object_cd,
        String attribute_cd,
        String role_cd,
        String purpose_cd,
        String grant_scope_cd,
        String grant_status_cd,
        String approved_by,
        OffsetDateTime approved_ts,
        OffsetDateTime created_ts,
        String created_by,
        OffsetDateTime updated_ts,
        String updated_by
) {
}
