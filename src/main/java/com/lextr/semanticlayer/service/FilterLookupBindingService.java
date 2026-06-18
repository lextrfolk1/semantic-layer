package com.lextr.semanticlayer.service;

import com.lextr.semanticlayer.dto.FilterLookupBindingRequestDto;
import com.lextr.semanticlayer.dto.FilterLookupBindingResponseDto;

public interface FilterLookupBindingService {

    FilterLookupBindingResponseDto bindLookup(String lookupCode, FilterLookupBindingRequestDto request);
}
