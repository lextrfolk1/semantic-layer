package com.lextr.semanticlayer.model;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public record SemanticFilterLookupRecord(
        Long id,
        String lookup_cd,
        String construction_type_cd,
        String manual_subtype_cd,
        String filter_obj,
        String filter_condition_txt,
        String filter_attr_cd,
        String validation_obj,
        String validation_attr_cd,
        String suggested_target_attr_cd,
        String execution_strategy_cd,
        Integer max_input_set_size,
        Integer max_output_rows,
        Integer cache_ttl_min,
        Integer review_period_days_override,
        boolean rules_eligible_flg,
        boolean qs_eligible_flg,
        boolean ai_eligible_flg,
        boolean replicate_to_ch_flg,
        String description_txt,
        String client_id,
        String governance_status_cd,
        String health_status_cd,
        OffsetDateTime last_certified_ts,
        String last_certified_by,
        LocalDate next_review_due_dt,
        String lifecycle_status_cd,
        OffsetDateTime created_ts,
        String created_by,
        OffsetDateTime updated_ts,
        String updated_by
) {
}
