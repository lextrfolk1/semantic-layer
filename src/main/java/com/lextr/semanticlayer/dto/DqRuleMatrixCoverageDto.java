package com.lextr.semanticlayer.dto;

public record DqRuleMatrixCoverageDto(
        String rule_cd,
        String logical_attribute_cd,
        int attribute_count,
        int result_count,
        int coverage_pct,
        boolean fully_covered_flg
) {
}
