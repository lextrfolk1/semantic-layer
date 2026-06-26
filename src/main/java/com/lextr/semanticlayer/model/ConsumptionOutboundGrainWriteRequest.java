package com.lextr.semanticlayer.model;

import java.time.OffsetDateTime;

public record ConsumptionOutboundGrainWriteRequest(
        String client_id,
        Long outbound_id,
        Integer grain_level_nbr,
        String logical_attribute_cd,
        String attribute_role_cd,
        OffsetDateTime created_ts,
        String created_by,
        OffsetDateTime updated_ts,
        String updated_by
) {
}
