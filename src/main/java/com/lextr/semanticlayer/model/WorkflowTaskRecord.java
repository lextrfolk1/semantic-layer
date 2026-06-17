package com.lextr.semanticlayer.model;

import java.time.OffsetDateTime;
import java.util.UUID;

public record WorkflowTaskRecord(
        UUID workflow_task_id,
        String client_id,
        String workflow_type_cd,
        String entity_type_cd,
        UUID entity_id,
        String task_status_cd,
        OffsetDateTime created_ts,
        String created_by,
        OffsetDateTime updated_ts,
        String updated_by
) {
}
