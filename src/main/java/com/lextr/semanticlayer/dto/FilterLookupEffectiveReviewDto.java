package com.lextr.semanticlayer.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public record FilterLookupEffectiveReviewDto(
        Long id,
        String lookup_cd,
        String construction_type_cd,
        Integer review_period_days_override,
        Integer effective_review_period_days,
        String effective_review_period_source_cd,
        String governance_status_cd,
        String health_status_cd,
        Long value_count,
        LocalDate next_review_due_dt,
        String lifecycle_status_cd,
        OffsetDateTime last_certified_ts,
        String last_certified_by,
        OffsetDateTime created_ts,
        String created_by,
        OffsetDateTime updated_ts,
        String updated_by
) {
}
