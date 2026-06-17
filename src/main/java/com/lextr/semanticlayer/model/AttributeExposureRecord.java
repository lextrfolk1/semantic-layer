package com.lextr.semanticlayer.model;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AttributeExposureRecord(
        UUID attribute_id,
        UUID object_id,
        String client_id,
        String attribute_cd,
        String attribute_nm,
        String effective_attribute_nm,
        String data_type_cd,
        String taxonomy_cd,
        String taxonomy_source_cd,
        String taxonomy_jurisdiction_cd,
        OffsetDateTime created_ts,
        String created_by,
        OffsetDateTime updated_ts,
        String updated_by
) {
}
