package com.lextr.semanticlayer.api;

import com.lextr.semanticlayer.dto.FilterLookupRegistrationRequestDto;
import com.lextr.semanticlayer.dto.FilterLookupRegistrationResponseDto;
import com.lextr.semanticlayer.dto.FilterLookupPreviewRequestDto;
import com.lextr.semanticlayer.dto.FilterLookupPreviewResponseDto;
import com.lextr.semanticlayer.service.FilterLookupPreviewService;
import com.lextr.semanticlayer.service.FilterLookupRegistrationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/filter-lookups")
public class FilterLookupRegistrationController {

    private final FilterLookupRegistrationService filterLookupRegistrationService;
    private final FilterLookupPreviewService filterLookupPreviewService;

    public FilterLookupRegistrationController(FilterLookupRegistrationService filterLookupRegistrationService,
                                              FilterLookupPreviewService filterLookupPreviewService) {
        this.filterLookupRegistrationService = filterLookupRegistrationService;
        this.filterLookupPreviewService = filterLookupPreviewService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FilterLookupRegistrationResponseDto registerFilterLookup(
            @Valid @RequestBody FilterLookupRegistrationRequestDto request) {
        return filterLookupRegistrationService.registerFilterLookup(request);
    }

    @PostMapping("/preview")
    public FilterLookupPreviewResponseDto previewFilterLookup(
            @Valid @RequestBody FilterLookupPreviewRequestDto request) {
        return filterLookupPreviewService.previewFilterLookup(request);
    }
}
