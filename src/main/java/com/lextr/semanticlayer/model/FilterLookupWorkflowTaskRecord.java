package com.lextr.semanticlayer.model;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public record FilterLookupWorkflowTaskRecord(
        Long id,
        String task_type_cd,
        String entity_type_cd,
        String entity_ref,
        String task_status_cd,
        String submitted_by,
        OffsetDateTime submitted_ts,
        String assigned_to,
        LocalDate due_dt,
        String description_txt,
        String client_id,
        String approved_by,
        OffsetDateTime approved_ts,
        String approval_note_txt
) {
}
