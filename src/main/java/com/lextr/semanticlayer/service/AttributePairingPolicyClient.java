package com.lextr.semanticlayer.service;

import com.lextr.semanticlayer.dto.AttributePairingPolicyDecisionDto;
import com.lextr.semanticlayer.dto.AttributePairingPolicyRequestDto;

public interface AttributePairingPolicyClient {

    AttributePairingPolicyDecisionDto validateCrossEngine(AttributePairingPolicyRequestDto request);
}
