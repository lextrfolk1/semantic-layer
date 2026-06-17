package com.lextr.semanticlayer.dto;

public record RelationshipPolicyDecisionDto(
        boolean allowed,
        String code,
        String message
) {
}
