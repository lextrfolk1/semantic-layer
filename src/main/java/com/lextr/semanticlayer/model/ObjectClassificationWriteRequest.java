package com.lextr.semanticlayer.model;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ObjectClassificationWriteRequest(
        UUID object_id,
        String client_id,
        String data_classification_cd,
        boolean pii_flg,
        boolean confidential_flg,
        OffsetDateTime updated_ts,
        String updated_by
) {
}
