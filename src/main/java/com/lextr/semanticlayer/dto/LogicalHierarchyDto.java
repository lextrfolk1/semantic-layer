package com.lextr.semanticlayer.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record LogicalHierarchyDto(
        Long id,
        String hierarchy_cd,
        String hierarchy_nm,
        String tenant_cd,
        String hierarchy_status_cd,
        String created_by,
        OffsetDateTime created_ts,
        String updated_by,
        OffsetDateTime updated_ts,
        List<HierarchyLevelDto> levels
) {
    public record HierarchyLevelDto(
            Integer level_nbr,
            String level_label,
            String attribute_cd,
            String code_cd,
            String object_ref
    ) {
    }
}
