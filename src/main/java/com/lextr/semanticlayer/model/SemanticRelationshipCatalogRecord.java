package com.lextr.semanticlayer.model;

import java.time.OffsetDateTime;

public record SemanticRelationshipCatalogRecord(
        Long id,
        String relationship_cd,
        String parent_schema_cd,
        String parent_object_cd,
        String parent_attribute_cd,
        String child_schema_cd,
        String child_object_cd,
        String child_attribute_cd,
        String relationship_type_cd,
        String cardinality_cd,
        String join_type_cd,
        boolean is_enforced_flg,
        boolean is_nullable_flg,
        boolean is_cross_engine_flg,
        String relationship_desc,
        String ai_join_guidance_txt,
        OffsetDateTime neo4j_synced_ts,
        String lifecycle_status_cd,
        OffsetDateTime created_ts,
        String created_by,
        OffsetDateTime updated_ts,
        String updated_by
) {
}
