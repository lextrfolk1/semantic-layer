package com.lextr.semanticlayer.dto;

public record ObservabilitySignalAutoTriggerPolicyRequestDto(
        String policy_cd,
        String client_id,
        String signal_type_cd,
        String trigger_cd,
        String severity_cd,
        String threshold_severity_cd
) {
}
