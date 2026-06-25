package com.lextr.semanticlayer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AttributeRegistrationRequestDto(
        @NotBlank String attribute_cd,
        @NotBlank
        @Size(max = 32)
        String attribute_nm,
        @NotBlank String data_type_cd,
        @NotBlank String taxonomy_cd,
        @NotBlank String taxonomy_source_cd,
        @NotBlank String taxonomy_jurisdiction_cd,
        boolean pk_flg,
        boolean fk_flg,
        boolean nullable_flg
) {
}
