package com.lextr.semanticlayer.dto;

public record TaxonomyPolicyRequestDto(
        String client_id,
        String taxonomy_cd,
        String taxonomy_source_cd,
        String taxonomy_jurisdiction_cd
) {
}
