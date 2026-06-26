package com.lextr.semanticlayer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

public record ObservabilitySignalCorrelationRequestDto(
        @NotBlank
        @Size(max = 40)
        String client_id,
        @NotBlank
        @Size(max = 20)
        String signal_status_cd,
        Long workflow_task_id,
        boolean dq_rerun_requested_flg,
        @Size(max = 2000)
        String dq_rerun_reason_txt,
        OffsetDateTime acknowledged_ts,
        OffsetDateTime resolved_ts,
        @NotBlank
        @Size(max = 32)
        String correlated_by
) {
}
