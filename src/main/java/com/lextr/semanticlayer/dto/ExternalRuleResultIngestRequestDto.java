package com.lextr.semanticlayer.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

public record ExternalRuleResultIngestRequestDto(
        @NotBlank
        @Size(max = 40)
        String client_id,

        @NotNull
        Long outbound_id,

        @NotBlank
        @Size(max = 120)
        String rule_ref_cd,

        @NotBlank
        @Size(max = 40)
        String output_kind_cd,

        @NotNull
        JsonNode output_payload_jsonb,

        OffsetDateTime observed_ts
) {
}
