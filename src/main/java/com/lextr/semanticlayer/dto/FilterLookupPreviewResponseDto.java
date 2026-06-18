package com.lextr.semanticlayer.dto;

import java.util.List;

public record FilterLookupPreviewResponseDto(
        String lookup_cd,
        String construction_type_cd,
        String execution_strategy_used_cd,
        Integer phase1_row_count,
        boolean phase1_cache_hit_flg,
        String result_status_cd,
        List<FilterLookupPreviewValueDto> values
) {
}
