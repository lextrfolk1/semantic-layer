package com.lextr.semanticlayer.service;

import com.lextr.semanticlayer.dto.FilterLookupPreviewValueDto;
import com.lextr.semanticlayer.model.SemanticFilterLookupRecord;

import java.util.List;

public interface FilterLookupSqlPreviewClient {

    List<FilterLookupPreviewValueDto> previewDistinctValues(String clientId, SemanticFilterLookupRecord lookup);
}
