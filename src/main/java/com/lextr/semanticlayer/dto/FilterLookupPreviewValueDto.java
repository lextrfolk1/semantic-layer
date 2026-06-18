package com.lextr.semanticlayer.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public record FilterLookupPreviewValueDto(
        String value_cd,
        String value_desc,
        String lifecycle_status_cd,
        boolean validated_flg,
        LocalDate anticipated_dt,
        String workflow_ref,
        OffsetDateTime last_seen_in_source_ts,
        Integer auto_expire_after_days,
        String alert_txt,
        String added_by,
        OffsetDateTime added_ts,
        String certified_by,
        OffsetDateTime certified_ts,
        OffsetDateTime updated_ts
) {
}
