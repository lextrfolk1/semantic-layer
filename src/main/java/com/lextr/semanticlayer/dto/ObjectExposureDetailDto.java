package com.lextr.semanticlayer.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record ObjectExposureDetailDto(
        UUID object_id,
        String object_cd,
        String object_nm,
        String object_type_cd,
        String schema_cd,
        UUID connection_id,
        String lifecycle_status_cd,
        OffsetDateTime created_ts,
        String created_by,
        OffsetDateTime updated_ts,
        String updated_by,
        List<AttributeExposureDto> attributes
) {
}
