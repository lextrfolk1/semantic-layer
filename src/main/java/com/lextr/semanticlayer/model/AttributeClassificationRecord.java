package com.lextr.semanticlayer.model;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AttributeClassificationRecord(
        UUID attribute_id,
        UUID object_id,
        String client_id,
        String attribute_cd,
        String attribute_nm,
        String data_type_cd,
        String taxonomy_cd,
        String taxonomy_source_cd,
        String taxonomy_jurisdiction_cd,
        String data_classification_cd,
        boolean pii_flg,
        boolean confidential_flg,
        String masking_policy_cd,
        boolean mnpi_flg,
        boolean csi_flg,
        String ai_exposure_cd,
        boolean pk_flg,
        boolean fk_flg,
        boolean nullable_flg,
        OffsetDateTime created_ts,
        String created_by,
        OffsetDateTime updated_ts,
        String updated_by
) {
}
