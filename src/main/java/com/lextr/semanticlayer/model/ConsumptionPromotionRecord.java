package com.lextr.semanticlayer.model;

import java.time.OffsetDateTime;

public record ConsumptionPromotionRecord(
        Long id,
        String client_id,
        Long outbound_id,
        String source_sdlc_status_cd,
        String target_sdlc_status_cd,
        String validation_status_cd,
        String opa_decision_cd,
        Long workflow_task_id,
        String promotion_status_cd,
        Integer version_nbr,
        OffsetDateTime applied_ts,
        String applied_by,
        OffsetDateTime created_ts,
        String created_by,
        OffsetDateTime updated_ts,
        String updated_by
) {
}
