package com.lextr.semanticlayer.dto;

public record ObjectExposurePolicyDecisionDto(
        boolean allowed,
        String code,
        String message
) {
}
