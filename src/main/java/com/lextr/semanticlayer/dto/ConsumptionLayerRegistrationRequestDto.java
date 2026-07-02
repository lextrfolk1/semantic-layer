package com.lextr.semanticlayer.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ConsumptionLayerRegistrationRequestDto(
        @NotBlank(message = "client_id is required")
        @Size(max = 40)
        String client_id,

        @NotBlank(message = "layer_cd is required")
        @Size(max = 60)
        String layer_cd,

        @NotBlank(message = "layer_nm is required")
        @Size(max = 200, message = "layer_nm must be 200 characters or less")
        String layer_nm,

        @Size(max = 1000)
        String layer_desc_txt,

        @NotBlank(message = "layer_type_cd is required")
        @Size(max = 30)
        String layer_type_cd,

        @NotEmpty(message = "outbounds is required")
        List<@Valid ConsumptionOutboundRegistrationRequestDto> outbounds,

        @NotBlank(message = "registered_by is required")
        @Size(max = 100)
        String registered_by
) {
}
