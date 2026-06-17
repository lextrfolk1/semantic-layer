package com.lextr.semanticlayer.model;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ObjectCatalogWriteRequest(
        UUID object_id,
        String client_id,
        String object_cd,
        String object_nm,
        String object_type_cd,
        String schema_cd,
        UUID connection_id,
        OffsetDateTime created_ts,
        String created_by,
        OffsetDateTime updated_ts,
        String updated_by
) {
}
