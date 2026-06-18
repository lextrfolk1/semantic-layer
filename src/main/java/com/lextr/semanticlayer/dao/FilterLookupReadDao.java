package com.lextr.semanticlayer.dao;

import com.lextr.semanticlayer.exception.SemanticLayerException;
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

    default List<FilterLookupPreviewValueRecord> findSqlValues(String clientId, SemanticFilterLookupRecord lookup) {
        return List.of();
    }

    default long countStaleValues(String clientId, String lookupCode) {
        throw new SemanticLayerException("FilterLookupReadDao does not support stale value counting");
    }

    long countValues(String clientId, String lookupCode);
}
