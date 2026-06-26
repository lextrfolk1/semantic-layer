package com.lextr.semanticlayer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

public record DqRuleResultIngestRequestDto(
        @NotBlank
        @Size(max = 40)
        String client_id,

        @NotBlank
        @Size(max = 80)
        String rule_cd,

        @NotBlank
        @Size(max = 80)
        String logical_attribute_cd,

        @NotBlank
        @Size(max = 2000)
        String observed_value_txt,

        @Size(max = 2000)
        String expected_value_txt,

        @NotBlank
        @Size(max = 20)
        String result_status_cd,

        @Size(max = 2000)
        String result_reason_txt,

        OffsetDateTime observed_ts,

        @NotBlank
        @Size(max = 80)
        String ingested_by
) {
}
