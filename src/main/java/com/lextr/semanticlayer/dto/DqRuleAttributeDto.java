package com.lextr.semanticlayer.dto;

import java.time.OffsetDateTime;

public record DqRuleAttributeDto(
        Long id,
        String rule_cd,
        String attribute_cd,
        String attribute_role_cd,
        String client_id,
        OffsetDateTime created_ts,
        String created_by,
        OffsetDateTime updated_ts,
        String updated_by
) {
}
