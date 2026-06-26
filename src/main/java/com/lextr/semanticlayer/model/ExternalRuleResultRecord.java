package com.lextr.semanticlayer.model;

import java.time.OffsetDateTime;

public record ExternalRuleResultRecord(
        Long id,
        String client_id,
        Long outbound_id,
        String rule_ref_cd,
        String output_kind_cd,
        String output_payload_jsonb,
        OffsetDateTime observed_ts,
        OffsetDateTime created_ts,
        String created_by,
        OffsetDateTime updated_ts,
        String updated_by
) {
}
