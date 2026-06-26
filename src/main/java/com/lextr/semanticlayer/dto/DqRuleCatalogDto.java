package com.lextr.semanticlayer.dto;

import java.time.OffsetDateTime;

public record DqRuleCatalogDto(
        String rule_cd,
        String rule_nm,
        String rule_dimension_cd,
        String logical_attribute_cd,
        String rule_scope_cd,
        String rule_expression_txt,
        String severity_cd,
        String lifecycle_status_cd,
        String client_id,
        OffsetDateTime created_ts,
        String created_by,
        OffsetDateTime updated_ts,
        String updated_by
) {
}
