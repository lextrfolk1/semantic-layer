package com.lextr.semanticlayer.service;

import com.lextr.semanticlayer.dto.FilterLookupRegistrationRequestDto;
import com.lextr.semanticlayer.dto.FilterLookupRegistrationResponseDto;

public interface FilterLookupRegistrationService {

    FilterLookupRegistrationResponseDto registerFilterLookup(FilterLookupRegistrationRequestDto request);
}
