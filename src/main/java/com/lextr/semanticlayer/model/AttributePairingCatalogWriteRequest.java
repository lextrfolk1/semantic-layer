package com.lextr.semanticlayer.model;

import java.time.OffsetDateTime;

public record AttributePairingCatalogWriteRequest(
        String pairing_cd,
        String pairing_nm,
        String schema_cd,
        String object_cd,
        String display_attribute_cd,
        String filter_attribute_cd,
        String pairing_type_cd,
        String lookup_strategy_cd,
        String lookup_inline_map_jsonb,
        String lookup_sql_template_txt,
        boolean lookup_cache_enabled_flg,
        int lookup_cache_ttl_seconds_nbr,
        String cardinality_cd,
        boolean is_bidirectional_flg,
        boolean is_cross_engine_flg,
        boolean filter_attribute_indexed_flg,
        String filter_attribute_index_type_cd,
        Integer performance_gain_pct_est_nbr,
        String ai_context_txt,
        String client_id,
        String lifecycle_status_cd,
        String governance_review_status_cd,
        int version_nbr,
        OffsetDateTime created_ts,
        String created_by,
        OffsetDateTime updated_ts,
        String updated_by
) {
}
