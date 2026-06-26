package com.lextr.semanticlayer.dto;

import java.time.OffsetDateTime;

public record ProfilingResultDto(
        Long id,
        String client_id,
        String schema_cd,
        String object_cd,
        String attribute_name,
        String inferred_role,
        Integer null_percentage,
        Integer distinct_percentage,
        String profiling_status,
        OffsetDateTime last_profiled_at,
        OffsetDateTime created_ts,
        String created_by,
        OffsetDateTime updated_ts,
        String updated_by
) {
}
