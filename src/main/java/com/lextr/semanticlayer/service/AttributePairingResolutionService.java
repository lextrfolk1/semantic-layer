package com.lextr.semanticlayer.service;

import com.lextr.semanticlayer.dto.AttributePairingResolutionRequestDto;
import com.lextr.semanticlayer.dto.AttributePairingResolutionResponseDto;

public interface AttributePairingResolutionService {

    AttributePairingResolutionResponseDto resolvePairing(AttributePairingResolutionRequestDto request);
}
