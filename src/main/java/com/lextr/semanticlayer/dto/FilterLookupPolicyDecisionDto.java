package com.lextr.semanticlayer.dto;

public record FilterLookupPolicyDecisionDto(
        boolean allowed,
        String code,
        String message
) {
}
