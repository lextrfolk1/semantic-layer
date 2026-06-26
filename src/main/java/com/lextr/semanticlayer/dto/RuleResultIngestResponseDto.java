package com.lextr.semanticlayer.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.OffsetDateTime;

public record RuleResultIngestResponseDto(
        Long external_rule_result_id,
        Long dq_result_id,
        String client_id,
        Long outbound_id,
        String rule_ref_cd,
        String output_kind_cd,
        String route_target_cd,
        JsonNode output_payload_jsonb,
        OffsetDateTime observed_ts,
        OffsetDateTime created_ts,
        String created_by,
        OffsetDateTime updated_ts,
        String updated_by
) {
}
