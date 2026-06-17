package com.lextr.semanticlayer.dto;

import java.time.OffsetDateTime;

public record SchemaCatalogDto(
        String schema_cd,
        String schema_nm,
        String schema_purpose_txt,
        String lifecycle_status_cd,
        OffsetDateTime created_ts,
        String created_by,
        OffsetDateTime updated_ts,
        String updated_by
) {
}
