package com.lextr.semanticlayer.model;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ObjectClassificationRecord(
        UUID object_id,
        String client_id,
        String object_cd,
        String object_nm,
        String object_type_cd,
        String schema_cd,
        UUID connection_id,
        String data_classification_cd,
        boolean pii_flg,
        boolean confidential_flg,
        String lifecycle_status_cd,
        OffsetDateTime created_ts,
        String created_by,
        OffsetDateTime updated_ts,
        String updated_by
) {
}
