package com.lextr.semanticlayer.dto;

public record DqRulePolicyDecisionDto(
        boolean allowed,
        String code,
        String message
) {
}
