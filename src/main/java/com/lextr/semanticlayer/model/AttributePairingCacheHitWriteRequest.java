package com.lextr.semanticlayer.model;

import java.time.OffsetDateTime;

public record AttributePairingCacheHitWriteRequest(
        String pairing_cd,
        String display_value_txt,
        String client_id,
        OffsetDateTime last_hit_ts
) {
}
