package com.lextr.semanticlayer.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record ConsumptionExposureDto(
        Long id,
        String client_id,
        String layer_cd,
        Long object_id,
        String outbound_cd,
        String outbound_nm,
        String structure_type_cd,
        String description_txt,
        List<String> attributes_jsonb,
        String sdlc_status_cd,
        Integer version_nbr,
        OffsetDateTime created_ts,
        String created_by,
        OffsetDateTime updated_ts,
        String updated_by
) {
}
