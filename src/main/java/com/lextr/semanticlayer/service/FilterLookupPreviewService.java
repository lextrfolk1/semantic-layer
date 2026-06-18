package com.lextr.semanticlayer.service;

import com.lextr.semanticlayer.dto.FilterLookupPreviewRequestDto;
import com.lextr.semanticlayer.dto.FilterLookupPreviewResponseDto;

import java.util.List;

public interface FilterLookupPreviewService {

    List<FilterLookupPreviewResponseDto> previewLookups(FilterLookupPreviewRequestDto request);
}
