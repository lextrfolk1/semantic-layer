package com.lextr.semanticlayer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record WorkflowApprovalRequestDto(
        @NotBlank(message = "client_id is required")
        String client_id,

        @NotBlank(message = "approved_by is required")
        @Size(max = 32, message = "approved_by must be 32 characters or less")
        String approved_by,

        String approval_note_txt
) {
}
