package com.lextr.semanticlayer.dto;

import java.util.UUID;

public record ObjectExposureClassificationPolicyRequestDto(
        String policy_cd,
        String request_type_cd,
        String client_id,
        String actor_id,
        String role_cd,
        String purpose_cd,
        UUID object_id,
        String schema_cd,
        String object_cd,
        String object_data_classification_cd,
        boolean object_pii_flg,
        boolean object_confidential_flg,
        String attribute_cd,
        String attribute_data_classification_cd,
        boolean attribute_pii_flg,
        boolean attribute_confidential_flg,
        String masking_policy_cd,
        boolean mnpi_flg,
        boolean csi_flg,
        String ai_exposure_cd,
        String taxonomy_jurisdiction_cd
) {
}
