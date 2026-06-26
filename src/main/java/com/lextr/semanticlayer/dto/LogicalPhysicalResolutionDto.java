package com.lextr.semanticlayer.dto;

public record LogicalPhysicalResolutionDto(
        Long outbound_id,
        String outbound_cd,
        Integer grain_level_nbr,
        String client_id,
        String schema_cd,
        String object_cd,
        String logical_attribute_cd,
        String effective_logical_attribute_nm,
        String physical_attribute_nm,
        String source_object_nm,
        String engine_cd,
        String data_type_cd
) {
}
