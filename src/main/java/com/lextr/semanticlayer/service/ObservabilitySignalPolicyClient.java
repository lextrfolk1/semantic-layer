package com.lextr.semanticlayer.service;

import com.lextr.semanticlayer.dto.ObservabilitySignalAutoTriggerPolicyRequestDto;
import com.lextr.semanticlayer.dto.ObservabilitySignalPolicyDecisionDto;

public interface ObservabilitySignalPolicyClient {

    ObservabilitySignalPolicyDecisionDto validateAutoTrigger(ObservabilitySignalAutoTriggerPolicyRequestDto request);
}
