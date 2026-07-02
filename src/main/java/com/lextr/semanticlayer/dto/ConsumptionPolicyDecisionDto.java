package com.lextr.semanticlayer.dto;

public record ConsumptionPolicyDecisionDto(
        boolean allowed,
        String code,
        String message
) {
}
