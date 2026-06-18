package com.lextr.semanticlayer.dto;

public record WorkflowPolicyDecisionDto(
        boolean allowed,
        String code,
        String message
) {
}
