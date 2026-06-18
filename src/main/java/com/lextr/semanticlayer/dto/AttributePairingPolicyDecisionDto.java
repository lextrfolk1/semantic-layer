package com.lextr.semanticlayer.dto;

public record AttributePairingPolicyDecisionDto(
        boolean allowed,
        String code,
        String message
) {
}
