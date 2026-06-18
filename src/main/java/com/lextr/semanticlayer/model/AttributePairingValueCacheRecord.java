package com.lextr.semanticlayer.model;

import java.time.OffsetDateTime;

public record AttributePairingValueCacheRecord(
        Long id,
        String pairing_cd,
        String client_id,
        String display_value_txt,
        String filter_value_txt,
        boolean is_one_to_many_flg,
        Long hit_count_nbr,
        OffsetDateTime last_hit_ts,
        OffsetDateTime cached_ts,
        OffsetDateTime expires_ts
) {
}
