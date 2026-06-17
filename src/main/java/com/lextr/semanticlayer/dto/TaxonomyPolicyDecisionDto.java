package com.lextr.semanticlayer.dto;

public record TaxonomyPolicyDecisionDto(
        boolean allowed,
        String code,
        String message
) {
}
