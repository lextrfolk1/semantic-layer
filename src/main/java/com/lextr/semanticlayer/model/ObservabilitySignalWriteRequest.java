package com.lextr.semanticlayer.model;

import java.time.OffsetDateTime;

public record ObservabilitySignalWriteRequest(
        String client_id,
        String signal_type_cd,
        String severity_cd,
        String signal_status_cd,
        String source_system_cd,
        String source_entity_type_cd,
        String source_entity_ref_txt,
        String correlation_key_txt,
        String finding_summary_txt,
        String finding_detail_txt,
        OffsetDateTime detected_ts,
        boolean dq_rerun_requested_flg,
        String dq_rerun_reason_txt,
        OffsetDateTime created_ts,
        String created_by,
        OffsetDateTime updated_ts,
        String updated_by
) {
}
