package com.lextr.semanticlayer.model;

import java.time.OffsetDateTime;

public record FilterLookupMetadataChangeHistoryRecord(
        Long id,
        String entity_type_cd,
        String entity_ref,
        String change_type_cd,
        String changed_by,
        OffsetDateTime changed_ts,
        String old_value_json,
        String new_value_json,
        String change_reason_txt
) {
}
