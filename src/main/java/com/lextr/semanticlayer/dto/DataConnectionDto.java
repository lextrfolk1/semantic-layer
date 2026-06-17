package com.lextr.semanticlayer.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record DataConnectionDto(
        UUID connection_id,
        String connection_cd,
        String connection_nm,
        String engine_cd,
        String connection_type_cd,
        String source_mode_cd,
        String host_nm,
        Integer port_nbr,
        String database_nm,
        String schema_nm_default,
        boolean is_default_flg,
        boolean is_active_flg,
        OffsetDateTime created_ts,
        String created_by,
        OffsetDateTime updated_ts,
        String updated_by
) {
}
