package com.lextr.semanticlayer.model;

import java.time.OffsetDateTime;

public record ObservabilitySignalCorrelationWriteRequest(
        Long id,
        String client_id,
        String signal_status_cd,
        Long workflow_task_id,
        boolean dq_rerun_requested_flg,
        String dq_rerun_reason_txt,
        OffsetDateTime acknowledged_ts,
        OffsetDateTime resolved_ts,
        OffsetDateTime updated_ts,
        String updated_by
) {
}
