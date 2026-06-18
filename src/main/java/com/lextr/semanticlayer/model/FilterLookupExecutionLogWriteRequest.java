package com.lextr.semanticlayer.model;

import java.time.OffsetDateTime;

public record FilterLookupExecutionLogWriteRequest(
        String lookup_cd,
        String executed_by,
        OffsetDateTime executed_ts,
        Integer phase1_duration_ms,
        Integer phase1_row_count,
        boolean phase1_cache_hit_flg,
        String execution_strategy_used_cd,
        Integer phase2_duration_ms,
        String result_status_cd,
        String error_txt,
        String blocked_by_policy_cd
) {
}
