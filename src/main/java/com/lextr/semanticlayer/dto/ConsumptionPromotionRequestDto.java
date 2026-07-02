package com.lextr.semanticlayer.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ConsumptionPromotionRequestDto(
        @NotBlank(message = "target_sdlc_status_cd is required")
        @Pattern(regexp = "DEV|QA|UAT|PROD", message = "target_sdlc_status_cd must be one of DEV, QA, UAT, PROD")
        @JsonAlias("targetEnv")
        String target_sdlc_status_cd,

        @NotBlank(message = "promoted_by is required")
        @Size(max = 32, message = "promoted_by must be 32 characters or less")
        String promoted_by,

        @Size(max = 2000)
        String promotion_reason_txt
) {
}
