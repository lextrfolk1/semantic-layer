package com.lextr.semanticlayer.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record ConsumptionOutboundRegistrationRequestDto(
        @NotBlank(message = "outbound_cd is required")
        @Size(max = 60)
        String outbound_cd,

        @NotBlank(message = "outbound_nm is required")
        @Size(max = 200, message = "outbound_nm must be 200 characters or less")
        String outbound_nm,

        @NotNull(message = "object_id is required")
        UUID object_id,

        @NotBlank(message = "structure_type_cd is required")
        @Size(max = 30)
        String structure_type_cd,

        @Size(max = 1000)
        String description_txt,

        @NotBlank(message = "sdlc_status_cd is required")
        @Size(max = 20)
        String sdlc_status_cd,

        @NotEmpty(message = "grains is required")
        List<@Valid ConsumptionOutboundGrainRegistrationRequestDto> grains
) {
}
