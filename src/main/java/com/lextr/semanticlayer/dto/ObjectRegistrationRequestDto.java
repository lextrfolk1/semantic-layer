package com.lextr.semanticlayer.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record ObjectRegistrationRequestDto(
        @NotBlank String client_id,
        @NotBlank String object_cd,
        @NotBlank
        @Size(max = 32, message = "object_nm must be 32 characters or less")
        String object_nm,
        @NotBlank String object_type_cd,
        @NotBlank String schema_cd,
        @NotNull UUID connection_id,
        @NotBlank String registered_by,
        @NotEmpty List<@Valid AttributeRegistrationRequestDto> attributes
) {
}
