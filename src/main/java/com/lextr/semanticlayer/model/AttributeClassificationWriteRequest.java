package com.lextr.semanticlayer.model;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AttributeClassificationWriteRequest(
        UUID object_id,
        String client_id,
        String attribute_cd,
        String data_classification_cd,
        boolean pii_flg,
        boolean confidential_flg,
        String masking_policy_cd,
        boolean mnpi_flg,
        boolean csi_flg,
        String ai_exposure_cd,
        OffsetDateTime updated_ts,
        String updated_by
) {
}
