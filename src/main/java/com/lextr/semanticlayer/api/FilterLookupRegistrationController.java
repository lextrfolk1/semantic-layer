package com.lextr.semanticlayer.api;

import com.lextr.semanticlayer.dto.FilterLookupBindingRequestDto;
import com.lextr.semanticlayer.dto.FilterLookupBindingResponseDto;
import com.lextr.semanticlayer.dto.FilterLookupCertificationRequestDto;
import com.lextr.semanticlayer.dto.FilterLookupPreviewRequestDto;
import com.lextr.semanticlayer.dto.FilterLookupPreviewResponseDto;
import com.lextr.semanticlayer.dto.FilterLookupEffectiveReviewDto;
import com.lextr.semanticlayer.dto.FilterLookupRegistrationRequestDto;
import com.lextr.semanticlayer.dto.FilterLookupRegistrationResponseDto;
import com.lextr.semanticlayer.exception.FilterLookupBindingServiceException;
import com.lextr.semanticlayer.exception.FilterLookupCertificationServiceException;
import com.lextr.semanticlayer.exception.FilterLookupPreviewServiceException;
import com.lextr.semanticlayer.service.FilterLookupBindingService;
import com.lextr.semanticlayer.service.FilterLookupCertificationService;
import com.lextr.semanticlayer.service.FilterLookupPreviewService;
import com.lextr.semanticlayer.service.FilterLookupReadService;
import com.lextr.semanticlayer.service.FilterLookupRegistrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
@Tag(name = "Filter Lookups", description = "Filter lookup registration, preview, binding, certification, and read operations.")
public class FilterLookupRegistrationController {

    private static final Logger logger = LoggerFactory.getLogger(FilterLookupRegistrationController.class);

    private final FilterLookupRegistrationService filterLookupRegistrationService;
    private final FilterLookupReadService filterLookupReadService;
    private final FilterLookupBindingService filterLookupBindingService;
    private final FilterLookupCertificationService filterLookupCertificationService;
    private final FilterLookupPreviewService filterLookupPreviewService;

    @Autowired
    public FilterLookupRegistrationController(
            FilterLookupRegistrationService filterLookupRegistrationService,
            FilterLookupReadService filterLookupReadService,
            ObjectProvider<FilterLookupBindingService> filterLookupBindingServiceProvider,
            ObjectProvider<FilterLookupCertificationService> filterLookupCertificationServiceProvider,
            ObjectProvider<FilterLookupPreviewService> filterLookupPreviewServiceProvider
    ) {
        this(
                filterLookupRegistrationService,
                filterLookupReadService,
                filterLookupBindingServiceProvider.getIfAvailable(MissingFilterLookupBindingService::new),
                filterLookupCertificationServiceProvider.getIfAvailable(MissingFilterLookupCertificationService::new),
                filterLookupPreviewServiceProvider.getIfAvailable(MissingFilterLookupPreviewService::new)
        );
    }

    FilterLookupRegistrationController(FilterLookupRegistrationService filterLookupRegistrationService,
                                       FilterLookupReadService filterLookupReadService) {
        this(
                filterLookupRegistrationService,
                filterLookupReadService,
                new MissingFilterLookupBindingService(),
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
                new MissingFilterLookupBindingService(),
                new MissingFilterLookupCertificationService(),
                filterLookupPreviewService
        );
    }

    FilterLookupRegistrationController(FilterLookupRegistrationService filterLookupRegistrationService,
                                       FilterLookupReadService filterLookupReadService,
                                       FilterLookupCertificationService filterLookupCertificationService,
                                       FilterLookupPreviewService filterLookupPreviewService) {
        this(
                filterLookupRegistrationService,
                filterLookupReadService,
                new MissingFilterLookupBindingService(),
                filterLookupCertificationService,
                filterLookupPreviewService
        );
    }

    FilterLookupRegistrationController(FilterLookupRegistrationService filterLookupRegistrationService,
                                       FilterLookupReadService filterLookupReadService,
                                       FilterLookupBindingService filterLookupBindingService,
                                       FilterLookupCertificationService filterLookupCertificationService,
                                       FilterLookupPreviewService filterLookupPreviewService) {
        this.filterLookupRegistrationService = filterLookupRegistrationService;
        this.filterLookupReadService = filterLookupReadService;
        this.filterLookupBindingService = filterLookupBindingService;
        this.filterLookupCertificationService = filterLookupCertificationService;
        this.filterLookupPreviewService = filterLookupPreviewService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register filter lookup", description = "Registers a governed filter lookup.")
    public FilterLookupRegistrationResponseDto registerFilterLookup(
            @Valid @RequestBody FilterLookupRegistrationRequestDto request) {
        logger.debug(
                "Registering filter lookup. clientId={}, lookupCode={}, constructionTypeCode={}, executionStrategyCode={}",
                request.client_id(),
                request.lookup_cd(),
                request.construction_type_cd(),
                request.execution_strategy_cd()
        );
        FilterLookupRegistrationResponseDto response = filterLookupRegistrationService.registerFilterLookup(request);
        logger.debug("Filter lookup registered. clientId={}, lookupCode={}", request.client_id(), request.lookup_cd());
        return response;
    }

    @PostMapping("/preview")
    @Operation(summary = "Preview filter lookups", description = "Previews filter lookup output for one or more lookup codes.")
    public List<FilterLookupPreviewResponseDto> previewLookups(
            @Valid @RequestBody FilterLookupPreviewRequestDto request) {
        logger.debug("Previewing filter lookups. clientId={}, lookupCodeCount={}", request.client_id(), request.lookup_codes().size());
        List<FilterLookupPreviewResponseDto> previews = filterLookupPreviewService.previewLookups(request);
        logger.debug("Filter lookup preview resolved. clientId={}, resultCount={}", request.client_id(), previews.size());
        return previews;
    }

    @PostMapping("/{lookup_code}/bindings")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Bind filter lookup", description = "Binds a lookup to an object or attribute context.")
    public FilterLookupBindingResponseDto bindLookup(
            @Parameter(description = "Lookup code.") @PathVariable("lookup_code") String lookupCode,
            @Valid @RequestBody FilterLookupBindingRequestDto request) {
        logger.debug(
                "Binding filter lookup. clientId={}, lookupCode={}, boundObject={}, bindingContextCode={}",
                request.client_id(),
                lookupCode,
                request.bound_obj(),
                request.binding_context_cd()
        );
        FilterLookupBindingResponseDto response = filterLookupBindingService.bindLookup(lookupCode, request);
        logger.debug("Filter lookup bound. clientId={}, lookupCode={}, bindingContextCode={}",
                request.client_id(), lookupCode, request.binding_context_cd());
        return response;
    }

    @PostMapping("/{lookup_code}/certify")
    @Operation(summary = "Certify filter lookup", description = "Certifies a filter lookup and updates its effective review state.")
    public FilterLookupEffectiveReviewDto certifyLookup(
            @Parameter(description = "Lookup code.") @PathVariable("lookup_code") String lookupCode,
            @Valid @RequestBody FilterLookupCertificationRequestDto request) {
        logger.debug("Certifying filter lookup. clientId={}, lookupCode={}", request.client_id(), lookupCode);
        FilterLookupEffectiveReviewDto review = filterLookupCertificationService.certifyLookup(lookupCode, request);
        logger.debug("Filter lookup certified. clientId={}, lookupCode={}, governanceStatusCode={}",
                request.client_id(), lookupCode, review.governance_status_cd());
        return review;
    }

    @GetMapping
    @Operation(summary = "List filter lookups", description = "Returns governed filter lookups for the supplied client.")
    public List<FilterLookupEffectiveReviewDto> findLookups(
            @Parameter(description = "Tenant identifier.") @RequestParam("client_id") String clientId,
            @Parameter(description = "Optional governance status filter.") @RequestParam(value = "governance_status_cd", required = false) String governanceStatusCode,
            @Parameter(description = "Optional health status filter.") @RequestParam(value = "health_status_cd", required = false) String healthStatusCode,
            @Parameter(description = "Optional lifecycle status filter.") @RequestParam(value = "lifecycle_status_cd", required = false) String lifecycleStatusCode) {
        logger.debug(
                "Listing filter lookups. clientId={}, governanceStatusCode={}, healthStatusCode={}, lifecycleStatusCode={}",
                clientId,
                governanceStatusCode,
                healthStatusCode,
                lifecycleStatusCode
        );
        List<FilterLookupEffectiveReviewDto> lookups =
                filterLookupReadService.findLookups(clientId, governanceStatusCode, healthStatusCode, lifecycleStatusCode);
        logger.debug("Filter lookups resolved. clientId={}, resultCount={}", clientId, lookups.size());
        return lookups;
    }

    @GetMapping("/{lookup_code}")
    @Operation(summary = "Get filter lookup", description = "Returns one governed filter lookup for the supplied client.")
    public FilterLookupEffectiveReviewDto findLookup(
            @Parameter(description = "Tenant identifier.") @RequestParam("client_id") String clientId,
            @Parameter(description = "Lookup code.") @PathVariable("lookup_code") String lookupCode) {
        logger.debug("Fetching filter lookup. clientId={}, lookupCode={}", clientId, lookupCode);
        FilterLookupEffectiveReviewDto lookup = filterLookupReadService.findLookup(clientId, lookupCode);
        logger.debug("Filter lookup resolved. clientId={}, lookupCode={}, governanceStatusCode={}",
                clientId, lookupCode, lookup.governance_status_cd());
        return lookup;
    }

    private static final class MissingFilterLookupCertificationService implements FilterLookupCertificationService {

        @Override
        public FilterLookupEffectiveReviewDto certifyLookup(String lookupCode, FilterLookupCertificationRequestDto request) {
            throw new FilterLookupCertificationServiceException("FilterLookupCertificationService is not configured");
        }
    }

    private static final class MissingFilterLookupBindingService implements FilterLookupBindingService {

        @Override
        public FilterLookupBindingResponseDto bindLookup(String lookupCode, FilterLookupBindingRequestDto request) {
            throw new FilterLookupBindingServiceException("FilterLookupBindingService is not configured");
        }
    }

    private static final class MissingFilterLookupPreviewService implements FilterLookupPreviewService {

        @Override
        public List<FilterLookupPreviewResponseDto> previewLookups(FilterLookupPreviewRequestDto request) {
            throw new FilterLookupPreviewServiceException("FilterLookupPreviewService is not configured");
        }
    }
}
