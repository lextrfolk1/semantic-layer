package com.lextr.semanticlayer.model;

public record LogicalHierarchyLevelRecord(
        Long id,
        String hierarchy_cd,
        Integer level_nbr,
        String level_label,
        String attribute_cd,
        String code_cd,
        String object_ref
) {
}
