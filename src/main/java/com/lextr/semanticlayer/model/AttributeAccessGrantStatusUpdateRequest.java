package com.lextr.semanticlayer.model;

import java.time.OffsetDateTime;

public record AttributeAccessGrantStatusUpdateRequest(
        Long id,
        String client_id,
        String grant_status_cd,
        String approved_by,
        OffsetDateTime approved_ts,
        OffsetDateTime updated_ts,
        String updated_by
) {
}
