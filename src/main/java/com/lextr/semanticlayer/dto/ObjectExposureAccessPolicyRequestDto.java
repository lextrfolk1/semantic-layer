package com.lextr.semanticlayer.dto;

import java.util.List;
import java.util.UUID;

public record ObjectExposureAccessPolicyRequestDto(
        String policy_cd,
        String request_type_cd,
        String client_id,
        String actor_id,
        String role_cd,
        String purpose_cd,
        UUID object_id,
        String schema_cd,
        String object_cd,
        String object_type_cd,
        String object_client_id,
        String object_data_classification_cd,
        String attribute_cd,
        List<String> grant_scope_cds,
        List<String> grant_status_cds
) {
}
