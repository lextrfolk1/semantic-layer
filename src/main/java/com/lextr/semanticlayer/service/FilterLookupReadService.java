package com.lextr.semanticlayer.service;

import com.lextr.semanticlayer.dto.FilterLookupEffectiveReviewDto;

import java.util.List;

public interface FilterLookupReadService {

    List<FilterLookupEffectiveReviewDto> findLookups(String clientId,
                                                     String governanceStatusCode,
                                                     String healthStatusCode,
                                                     String lifecycleStatusCode);

    FilterLookupEffectiveReviewDto findLookup(String clientId, String lookupCode);
}
