package com.lextr.semanticlayer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RelationshipRegistrationRequestDto(
        @NotBlank
        @Size(max = 100)
        String relationship_cd,
        @NotBlank
        @Size(max = 30)
        String parent_schema_cd,
        @NotBlank
        @Size(max = 50)
        String parent_object_cd,
        @NotBlank
        @Size(max = 32)
        String parent_attribute_cd,
        @NotBlank
        @Size(max = 30)
        String child_schema_cd,
        @NotBlank
        @Size(max = 50)
        String child_object_cd,
        @NotBlank
        @Size(max = 32)
        String child_attribute_cd,
        @NotBlank
        @Size(max = 30)
        String relationship_type_cd,
        @NotBlank
        @Size(max = 20)
        String cardinality_cd,
        @NotBlank
        @Size(max = 20)
        String join_type_cd,
        boolean is_enforced_flg,
        boolean is_nullable_flg,
        boolean is_cross_engine_flg,
        String relationship_desc,
        String ai_join_guidance_txt,
        @NotBlank
        @Size(max = 100)
        String registered_by
) {
}
