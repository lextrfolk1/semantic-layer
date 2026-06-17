package com.lextr.semanticlayer.dto;

import java.util.UUID;

public record AttributeRegistrationResponseDto(
        UUID attribute_id,
        String attribute_cd,
        String attribute_nm,
        String taxonomy_cd,
        String taxonomy_source_cd,
        String taxonomy_jurisdiction_cd
) {
}
