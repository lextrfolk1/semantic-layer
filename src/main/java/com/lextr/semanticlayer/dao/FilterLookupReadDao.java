package com.lextr.semanticlayer.dao;

import com.lextr.semanticlayer.model.FilterLookupPreviewValueRecord;
import com.lextr.semanticlayer.model.SemanticFilterLookupRecord;

import java.util.List;
import java.util.Optional;

public interface FilterLookupReadDao {

    List<SemanticFilterLookupRecord> findLookups(String clientId,
                                                 String governanceStatusCode,
                                                 String healthStatusCode,
                                                 String lifecycleStatusCode);

    Optional<SemanticFilterLookupRecord> findLookup(String clientId, String lookupCode);

    List<FilterLookupPreviewValueRecord> findManualValues(String clientId, String lookupCode);

    long countValues(String clientId, String lookupCode);
}
