package com.lextr.semanticlayer.dto;

public record RuleResultPolicyDecisionDto(
        boolean allowed,
        String code,
        String message
) {
}
