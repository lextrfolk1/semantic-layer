package com.lextr.semanticlayer.service;

import com.lextr.semanticlayer.dto.FilterLookupCertificationRequestDto;
import com.lextr.semanticlayer.dto.FilterLookupEffectiveReviewDto;

public interface FilterLookupCertificationService {

    FilterLookupEffectiveReviewDto certifyLookup(String lookupCode, FilterLookupCertificationRequestDto request);
}
