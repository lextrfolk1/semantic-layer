package com.lextr.semanticlayer.service;

import com.lextr.semanticlayer.dto.FilterLookupPreviewRequestDto;
import com.lextr.semanticlayer.dto.FilterLookupPreviewResponseDto;

public interface FilterLookupPreviewService {

    FilterLookupPreviewResponseDto previewFilterLookup(FilterLookupPreviewRequestDto request);
}
