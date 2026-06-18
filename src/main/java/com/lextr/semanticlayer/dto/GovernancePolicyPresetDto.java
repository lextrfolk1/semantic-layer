package com.lextr.semanticlayer.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public record GovernancePolicyPresetDto(
        String policy_cd,
        String policy_nm,
        String policy_scope_cd,
        String default_value_txt,
        String data_type_cd,
        boolean is_overrideable_flg,
        boolean override_requires_approval_flg,
        LocalDate effective_from_dt,
        LocalDate effective_to_dt,
        String approved_by,
        OffsetDateTime approved_ts,
        OffsetDateTime created_ts,
        String created_by
) {
}
