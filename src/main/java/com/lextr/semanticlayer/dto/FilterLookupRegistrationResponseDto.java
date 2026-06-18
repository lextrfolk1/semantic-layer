package com.lextr.semanticlayer.dto;

import java.time.LocalDate;

public record FilterLookupRegistrationResponseDto(
        Long id,
        String lookup_cd,
        String construction_type_cd,
        Integer review_period_days_override,
        String governance_status_cd,
        String health_status_cd,
        LocalDate next_review_due_dt,
        String lifecycle_status_cd,
        Long workflow_task_id,
        String workflow_status_cd
) {
}
