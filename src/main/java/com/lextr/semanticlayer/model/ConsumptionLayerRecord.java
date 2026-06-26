package com.lextr.semanticlayer.model;

import java.time.OffsetDateTime;

public record ConsumptionLayerRecord(
        Long id,
        String client_id,
        String layer_cd,
        String layer_nm,
        String layer_desc_txt,
        String layer_type_cd,
        String lifecycle_status_cd,
        OffsetDateTime created_ts,
        String created_by,
        OffsetDateTime updated_ts,
        String updated_by
) {
}
