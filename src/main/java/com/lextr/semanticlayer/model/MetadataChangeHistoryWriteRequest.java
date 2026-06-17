package com.lextr.semanticlayer.model;

import java.time.OffsetDateTime;
import java.util.UUID;

public record MetadataChangeHistoryWriteRequest(
        UUID change_history_id,
        String client_id,
        String entity_type_cd,
        UUID entity_id,
        String change_type_cd,
        String change_summary_txt,
        OffsetDateTime created_ts,
        String created_by
) {
}
