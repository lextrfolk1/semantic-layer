package com.lextr.semanticlayer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record DqRuleRequestDto(
        @NotBlank
        @Size(max = 40)
        String client_id,

        @NotEmpty
        @Size(max = 32, message = "rule_names must contain 32 items or fewer")
        List<@NotBlank @Size(max = 80) String> rule_names,

        @NotBlank
        @Size(max = 80)
        String requested_by,

        @Size(max = 2000)
        String request_txt
) {
}
