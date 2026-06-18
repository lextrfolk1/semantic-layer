package com.lextr.semanticlayer.model;

import java.time.OffsetDateTime;

public record FilterLookupBindingWriteRequest(
        String client_id,
        String lookup_cd,
        String bound_obj,
        String bound_attr_cd,
        String binding_context_cd,
        String binding_ref,
        String bound_by,
        OffsetDateTime bound_ts,
        boolean is_active_flg
) {
}
