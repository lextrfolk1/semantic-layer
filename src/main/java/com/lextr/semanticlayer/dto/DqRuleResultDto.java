package com.lextr.semanticlayer.dto;

import java.time.OffsetDateTime;

public record DqRuleResultDto(
        Long id,
        String rule_cd,
        String logical_attribute_cd,
        String client_id,
        String observed_value_txt,
        String expected_value_txt,
        String result_status_cd,
        String result_reason_txt,
        OffsetDateTime observed_ts,
        OffsetDateTime created_ts,
        String created_by,
        OffsetDateTime updated_ts,
        String updated_by
) {
}
