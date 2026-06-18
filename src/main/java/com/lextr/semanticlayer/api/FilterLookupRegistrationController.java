package com.lextr.semanticlayer.api;

import com.lextr.semanticlayer.dto.FilterLookupCertificationRequestDto;
import com.lextr.semanticlayer.dto.FilterLookupPreviewRequestDto;
import com.lextr.semanticlayer.dto.FilterLookupPreviewResponseDto;
import com.lextr.semanticlayer.dto.FilterLookupEffectiveReviewDto;
import com.lextr.semanticlayer.dto.FilterLookupRegistrationRequestDto;
import com.lextr.semanticlayer.dto.FilterLookupRegistrationResponseDto;
import com.lextr.semanticlayer.exception.FilterLookupCertificationServiceException;
import com.lextr.semanticlayer.exception.FilterLookupPreviewServiceException;
import com.lextr.semanticlayer.service.FilterLookupCertificationService;
import com.lextr.semanticlayer.service.FilterLookupPreviewService;
import com.lextr.semanticlayer.service.FilterLookupReadService;
import com.lextr.semanticlayer.service.FilterLookupRegistrationService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/filter-lookups")
public class FilterLookupRegistrationController {

    private final FilterLookupRegistrationService filterLookupRegistrationService;
    private final FilterLookupReadService filterLookupReadService;
    private final FilterLookupCertificationService filterLookupCertificationService;
    private final FilterLookupPreviewService filterLookupPreviewService;

    @Autowired
    public FilterLookupRegistrationController(
            FilterLookupRegistrationService filterLookupRegistrationService,
            FilterLookupReadService filterLookupReadService,
            ObjectProvider<FilterLookupCertificationService> filterLookupCertificationServiceProvider,
            ObjectProvider<FilterLookupPreviewService> filterLookupPreviewServiceProvider
    ) {
        this(
                filterLookupRegistrationService,
                filterLookupReadService,
                filterLookupCertificationServiceProvider.getIfAvailable(MissingFilterLookupCertificationService::new),
                filterLookupPreviewServiceProvider.getIfAvailable(MissingFilterLookupPreviewService::new)
        );
    }

    FilterLookupRegistrationController(FilterLookupRegistrationService filterLookupRegistrationService,
                                       FilterLookupReadService filterLookupReadService) {
        this(
                filterLookupRegistrationService,
                filterLookupReadService,
                new MissingFilterLookupCertificationService(),
                new MissingFilterLookupPreviewService()
        );
    }

    FilterLookupRegistrationController(FilterLookupRegistrationService filterLookupRegistrationService,
                                       FilterLookupReadService filterLookupReadService,
                                       FilterLookupPreviewService filterLookupPreviewService) {
        this(
                filterLookupRegistrationService,
                filterLookupReadService,
                new MissingFilterLookupCertificationService(),
                filterLookupPreviewService
        );
    }

    FilterLookupRegistrationController(FilterLookupRegistrationService filterLookupRegistrationService,
                                       FilterLookupReadService filterLookupReadService,
                                       FilterLookupCertificationService filterLookupCertificationService,
                                       FilterLookupPreviewService filterLookupPreviewService) {
        this.filterLookupRegistrationService = filterLookupRegistrationService;
        this.filterLookupReadService = filterLookupReadService;
        this.filterLookupCertificationService = filterLookupCertificationService;
        this.filterLookupPreviewService = filterLookupPreviewService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FilterLookupRegistrationResponseDto registerFilterLookup(
            @Valid @RequestBody FilterLookupRegistrationRequestDto request) {
        return filterLookupRegistrationService.registerFilterLookup(request);
    }

    @PostMapping("/preview")
    public List<FilterLookupPreviewResponseDto> previewLookups(
            @Valid @RequestBody FilterLookupPreviewRequestDto request) {
        return filterLookupPreviewService.previewLookups(request);
    }

    @PostMapping("/{lookup_code}/certify")
    public FilterLookupEffectiveReviewDto certifyLookup(
            @PathVariable("lookup_code") String lookupCode,
            @Valid @RequestBody FilterLookupCertificationRequestDto request) {
        return filterLookupCertificationService.certifyLookup(lookupCode, request);
    }

    @GetMapping
    public List<FilterLookupEffectiveReviewDto> findLookups(
            @RequestParam("client_id") String clientId,
            @RequestParam(value = "governance_status_cd", required = false) String governanceStatusCode,
            @RequestParam(value = "health_status_cd", required = false) String healthStatusCode,
            @RequestParam(value = "lifecycle_status_cd", required = false) String lifecycleStatusCode) {
        return filterLookupReadService.findLookups(clientId, governanceStatusCode, healthStatusCode, lifecycleStatusCode);
    }

    @GetMapping("/{lookup_code}")
    public FilterLookupEffectiveReviewDto findLookup(
            @RequestParam("client_id") String clientId,
            @PathVariable("lookup_code") String lookupCode) {
        return filterLookupReadService.findLookup(clientId, lookupCode);
    }

    private static final class MissingFilterLookupCertificationService implements FilterLookupCertificationService {

        @Override
        public FilterLookupEffectiveReviewDto certifyLookup(String lookupCode, FilterLookupCertificationRequestDto request) {
            throw new FilterLookupCertificationServiceException("FilterLookupCertificationService is not configured");
        }
    }

    private static final class MissingFilterLookupPreviewService implements FilterLookupPreviewService {

        @Override
        public List<FilterLookupPreviewResponseDto> previewLookups(FilterLookupPreviewRequestDto request) {
            throw new FilterLookupPreviewServiceException("FilterLookupPreviewService is not configured");
        }
    }
}
