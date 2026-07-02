package com.lextr.semanticlayer.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ConsumptionOutboundGrainRegistrationRequestDto(
        @NotNull(message = "grain_level_nbr is required")
        @Min(value = 1, message = "grain_level_nbr must be 1 or greater")
        Integer grain_level_nbr,

        @NotBlank(message = "logical_attribute_cd is required")
        @Size(max = 32)
        String logical_attribute_cd,

        @Size(max = 20)
        String attribute_role_cd
) {
}
