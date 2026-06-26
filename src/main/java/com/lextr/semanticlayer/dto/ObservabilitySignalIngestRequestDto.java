package com.lextr.semanticlayer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

public record ObservabilitySignalIngestRequestDto(
        @NotBlank
        @Size(max = 40)
        String client_id,
        @NotBlank
        @Size(max = 40)
        String signal_type_cd,
        @Size(max = 20)
        String severity_cd,
        @Size(max = 20)
        String signal_status_cd,
        @NotBlank
        @Size(max = 60)
        String source_system_cd,
        @Size(max = 40)
        String source_entity_type_cd,
        @Size(max = 120)
        String source_entity_ref_txt,
        @Size(max = 200)
        String correlation_key_txt,
        @Size(max = 2000)
        String finding_summary_txt,
        @Size(max = 4000)
        String finding_detail_txt,
        boolean dq_rerun_requested_flg,
        @Size(max = 2000)
        String dq_rerun_reason_txt,
        @NotNull
        OffsetDateTime detected_ts,
        @NotBlank
        @Size(max = 32)
        String reported_by
) {
}
