package com.lextr.semanticlayer.service;

import com.lextr.semanticlayer.dto.AttributePairingRegistrationRequestDto;
import com.lextr.semanticlayer.dto.AttributePairingRegistrationResponseDto;

public interface AttributePairingRegistrationService {

    AttributePairingRegistrationResponseDto registerPairing(AttributePairingRegistrationRequestDto request);
}
