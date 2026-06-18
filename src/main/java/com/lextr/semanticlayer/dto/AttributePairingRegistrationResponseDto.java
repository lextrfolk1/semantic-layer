package com.lextr.semanticlayer.dto;

public record AttributePairingRegistrationResponseDto(
        Long id,
        String pairing_cd,
        String pairing_nm,
        String schema_cd,
        String object_cd,
        String display_attribute_cd,
        String filter_attribute_cd,
        String lifecycle_status_cd,
        String governance_review_status_cd
) {
}
