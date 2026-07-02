package com.lextr.semanticlayer.model;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public record DqRuleRequestWorkflowTaskWriteRequest(
        String client_id,
        String workflow_type_cd,
        String entity_type_cd,
        String rule_cd,
        String task_status_cd,
        String submitted_by,
        OffsetDateTime submitted_ts,
        String assigned_to,
        LocalDate due_dt,
        String description_txt
) {
}
