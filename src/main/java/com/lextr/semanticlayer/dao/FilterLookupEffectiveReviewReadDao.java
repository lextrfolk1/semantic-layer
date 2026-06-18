package com.lextr.semanticlayer.dao;

import com.lextr.semanticlayer.model.FilterLookupPreviewValueRecord;
import com.lextr.semanticlayer.model.FilterLookupValueCountRecord;
import com.lextr.semanticlayer.model.SemanticFilterLookupRecord;

import java.util.List;
import java.util.Optional;

public interface FilterLookupEffectiveReviewReadDao {

    Optional<SemanticFilterLookupRecord> findLookupByCode(String clientId, String lookupCode);

    List<FilterLookupPreviewValueRecord> findManualValuesByLookup(String clientId, String lookupCode);

    FilterLookupValueCountRecord countValuesByLookup(String clientId, String lookupCode);
}
