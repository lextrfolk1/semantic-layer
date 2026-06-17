package com.lextr.semanticlayer.service;

import com.lextr.semanticlayer.dto.ObjectRegistrationRequestDto;
import com.lextr.semanticlayer.dto.ObjectRegistrationResponseDto;

public interface ObjectRegistrationService {

    ObjectRegistrationResponseDto registerObject(ObjectRegistrationRequestDto request);
}
