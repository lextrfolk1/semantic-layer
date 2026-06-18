package com.lextr.semanticlayer.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record FilterLookupPreviewResponseDto(
        Long execution_log_id,
        String lookup_cd,
        String construction_type_cd,
        String client_id,
        String execution_strategy_used_cd,
        String result_status_cd,
        OffsetDateTime executed_ts,
        Integer phase1_duration_ms,
        Integer phase1_row_count,
        boolean phase1_cache_hit_flg,
        long value_count,
        List<FilterLookupPreviewValueDto> preview_values
) {
}
