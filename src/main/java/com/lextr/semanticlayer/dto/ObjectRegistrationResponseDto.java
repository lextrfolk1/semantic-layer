package com.lextr.semanticlayer.dto;

import java.util.List;
import java.util.UUID;

public record ObjectRegistrationResponseDto(
        UUID object_id,
        String object_cd,
        String object_nm,
        String lifecycle_status_cd,
        UUID workflow_task_id,
        String workflow_status_cd,
        UUID change_history_id,
        List<AttributeRegistrationResponseDto> attributes
) {
}
