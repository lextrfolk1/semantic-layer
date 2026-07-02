package com.lextr.semanticlayer.model;

import java.time.OffsetDateTime;

public record ObjectExposureAccessAuditWriteRequest(
        String entity_type_cd,
        String entity_ref,
        String change_type_cd,
        String changed_by,
        OffsetDateTime changed_ts,
        String change_reason_txt
) {
}
