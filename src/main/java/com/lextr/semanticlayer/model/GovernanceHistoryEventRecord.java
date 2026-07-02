package com.lextr.semanticlayer.model;

import java.time.OffsetDateTime;

public record GovernanceHistoryEventRecord(
        Long event_id,
        String client_id,
        String entity_type_cd,
        String entity_ref,
        String change_type_cd,
        String change_summary_txt,
        String actor_id,
        OffsetDateTime event_ts,
        String old_value_json,
        String new_value_json
) {
}
