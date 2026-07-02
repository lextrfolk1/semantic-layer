package com.lextr.semanticlayer.dto;

public record ObservabilitySignalPolicyDecisionDto(
        boolean allowed,
        String code,
        String reason
) {
}
