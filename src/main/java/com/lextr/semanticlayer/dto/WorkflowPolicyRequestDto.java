package com.lextr.semanticlayer.dto;

public record WorkflowPolicyRequestDto(
        String client_id,
        Long task_id,
        String task_type_cd,
        String entity_type_cd,
        String entity_ref,
        String submitted_by,
        String approved_by
) {
}
