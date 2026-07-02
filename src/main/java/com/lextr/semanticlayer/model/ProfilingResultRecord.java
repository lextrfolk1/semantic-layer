package com.lextr.semanticlayer.model;

import java.time.OffsetDateTime;

public record ProfilingResultRecord(
        Long id,
        String client_id,
        String schema_cd,
        String object_cd,
        String logical_attribute_cd,
        String attribute_role_cd,
        Integer null_pct_nbr,
        Integer distinct_pct_nbr,
        String profiling_status_cd,
        OffsetDateTime last_profiled_ts,
        OffsetDateTime created_ts,
        String created_by,
        OffsetDateTime updated_ts,
        String updated_by
) {
}
