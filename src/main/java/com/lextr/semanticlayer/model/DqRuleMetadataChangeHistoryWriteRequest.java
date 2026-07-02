package com.lextr.semanticlayer.model;

import java.time.OffsetDateTime;
import java.util.UUID;

public record DqRuleMetadataChangeHistoryWriteRequest(
        UUID change_history_id,
        String client_id,
        String entity_type_cd,
        String entity_ref,
        String change_type_cd,
        String change_summary_txt,
        OffsetDateTime created_ts,
        String created_by
) {
}
