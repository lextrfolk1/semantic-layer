package com.lextr.semanticlayer.model;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ConsumptionOutboundWriteRequest(
        String client_id,
        String layer_cd,
        UUID object_id,
        String outbound_cd,
        String outbound_nm,
        String structure_type_cd,
        String description_txt,
        String sdlc_status_cd,
        Integer version_nbr,
        OffsetDateTime created_ts,
        String created_by,
        OffsetDateTime updated_ts,
        String updated_by
) {
}
