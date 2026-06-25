package com.lextr.semanticlayer.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AttributeExposureDto(
        UUID attribute_id,
        String attribute_cd,
        String attribute_nm,
        String data_type_cd,
        String taxonomy_cd,
        String taxonomy_source_cd,
        String taxonomy_jurisdiction_cd,
        boolean pk_flg,
        boolean fk_flg,
        boolean nullable_flg,
        OffsetDateTime created_ts,
        String created_by,
        OffsetDateTime updated_ts,
        String updated_by
) {
}
