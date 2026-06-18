package com.lextr.semanticlayer.model;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public record FilterLookupCertificationWriteRequest(
        String client_id,
        String lookup_cd,
        String health_status_cd,
        OffsetDateTime last_certified_ts,
        String last_certified_by,
        LocalDate next_review_due_dt,
        OffsetDateTime updated_ts,
        String updated_by
) {
}
