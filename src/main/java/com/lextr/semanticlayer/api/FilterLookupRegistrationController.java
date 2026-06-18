package com.lextr.semanticlayer.api;

import com.lextr.semanticlayer.dto.FilterLookupRegistrationRequestDto;
import com.lextr.semanticlayer.dto.FilterLookupRegistrationResponseDto;
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

    public FilterLookupRegistrationController(FilterLookupRegistrationService filterLookupRegistrationService) {
        this.filterLookupRegistrationService = filterLookupRegistrationService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FilterLookupRegistrationResponseDto registerFilterLookup(
            @Valid @RequestBody FilterLookupRegistrationRequestDto request) {
        return filterLookupRegistrationService.registerFilterLookup(request);
    }
}
