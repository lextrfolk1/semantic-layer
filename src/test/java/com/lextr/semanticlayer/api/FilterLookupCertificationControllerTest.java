package com.lextr.semanticlayer.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lextr.semanticlayer.dto.FilterLookupCertificationRequestDto;
import com.lextr.semanticlayer.dto.FilterLookupEffectiveReviewDto;
import com.lextr.semanticlayer.dto.FilterLookupPreviewRequestDto;
import com.lextr.semanticlayer.dto.FilterLookupPreviewResponseDto;
import com.lextr.semanticlayer.dto.FilterLookupRegistrationRequestDto;
import com.lextr.semanticlayer.dto.FilterLookupRegistrationResponseDto;
import com.lextr.semanticlayer.exception.FilterLookupCertificationServiceException;
import com.lextr.semanticlayer.exception.PolicyViolationException;
import com.lextr.semanticlayer.exception.RegistryResourceNotFoundException;
import com.lextr.semanticlayer.service.FilterLookupCertificationService;
import com.lextr.semanticlayer.service.FilterLookupPreviewService;
import com.lextr.semanticlayer.service.FilterLookupReadService;
import com.lextr.semanticlayer.service.FilterLookupRegistrationService;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FilterLookupCertificationControllerTest {

    @Test
    void routesFilterLookupCertificationEndpointToService() throws Exception {
        RecordingFilterLookupCertificationService service = new RecordingFilterLookupCertificationService();
        service.response = new FilterLookupEffectiveReviewDto(
                101L,
                "LEDGER_SCOPE",
                "MANUAL_LIST",
                90,
                90,
                "GOV_DEFAULT",
                "ACTIVE",
                "HEALTHY",
                2L,
                LocalDate.parse("2026-09-16"),
                "ACTIVE",
                OffsetDateTime.parse("2026-06-18T10:15:30Z"),
                "certifier",
                OffsetDateTime.parse("2026-06-16T10:15:30Z"),
                "producer",
                OffsetDateTime.parse("2026-06-18T10:15:30Z"),
                "certifier"
        );
        MockMvc mockMvc = mockMvc(service);

        mockMvc.perform(post("/api/filter-lookups/{lookup_code}/certify", "LEDGER_SCOPE")
                        .contentType("application/json")
                        .content(validRequestJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lookup_cd").value("LEDGER_SCOPE"))
                .andExpect(jsonPath("$.health_status_cd").value("HEALTHY"))
                .andExpect(jsonPath("$.last_certified_by").value("certifier"))
                .andExpect(jsonPath("$.effective_review_period_days").value(90));

        assertEquals("LEDGER_SCOPE", service.lastLookupCode);
        assertEquals("client-a", service.lastRequest.client_id());
        assertEquals("certifier", service.lastRequest.certified_by());
    }

    @Test
    void rejectsCertifiedByLongerThanThirtyTwoCharacters() throws Exception {
        MockMvc mockMvc = mockMvc(new RecordingFilterLookupCertificationService());

        mockMvc.perform(post("/api/filter-lookups/{lookup_code}/certify", "LEDGER_SCOPE")
                        .contentType("application/json")
                        .content("""
                                {
                                  "client_id": "client-a",
                                  "certified_by": "123456789012345678901234567890123"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void mapsCertificationServiceErrorsToInternalServerError() throws Exception {
        RecordingFilterLookupCertificationService service = new RecordingFilterLookupCertificationService();
        service.error = new FilterLookupCertificationServiceException("certification failed");
        MockMvc mockMvc = mockMvc(service);

        mockMvc.perform(post("/api/filter-lookups/{lookup_code}/certify", "LEDGER_SCOPE")
                        .contentType("application/json")
                        .content(validRequestJson()))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void mapsPolicyViolationToUnprocessableEntityWithCode() throws Exception {
        RecordingFilterLookupCertificationService service = new RecordingFilterLookupCertificationService();
        service.error = new PolicyViolationException("POL-SV-001", "Stale values block certification");
        MockMvc mockMvc = mockMvc(service);

        mockMvc.perform(post("/api/filter-lookups/{lookup_code}/certify", "LEDGER_SCOPE")
                        .contentType("application/json")
                        .content(validRequestJson()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("POL-SV-001"))
                .andExpect(jsonPath("$.message").value("Stale values block certification"));
    }

    @Test
    void mapsMissingLookupToNotFound() throws Exception {
        RecordingFilterLookupCertificationService service = new RecordingFilterLookupCertificationService();
        service.error = new RegistryResourceNotFoundException("filter lookup", "UNKNOWN_LOOKUP");
        MockMvc mockMvc = mockMvc(service);

        mockMvc.perform(post("/api/filter-lookups/{lookup_code}/certify", "UNKNOWN_LOOKUP")
                        .contentType("application/json")
                        .content(validRequestJson()))
                .andExpect(status().isNotFound());
    }

    private static MockMvc mockMvc(FilterLookupCertificationService service) {
        FilterLookupRegistrationController controller = new FilterLookupRegistrationController(
                new NoOpFilterLookupRegistrationService(),
                new NoOpFilterLookupReadService(),
                service,
                new NoOpFilterLookupPreviewService()
        );
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        return MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ApiExceptionHandler())
                .setValidator(validator)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    private static String validRequestJson() {
        return """
                {
                  "client_id": "client-a",
                  "certified_by": "certifier"
                }
                """;
    }

    private static final class RecordingFilterLookupCertificationService implements FilterLookupCertificationService {

        private String lastLookupCode;
        private FilterLookupCertificationRequestDto lastRequest;
        private FilterLookupEffectiveReviewDto response;
        private RuntimeException error;

        @Override
        public FilterLookupEffectiveReviewDto certifyLookup(String lookupCode, FilterLookupCertificationRequestDto request) {
            lastLookupCode = lookupCode;
            lastRequest = request;
            if (error != null) {
                throw error;
            }
            return response;
        }
    }

    private static final class NoOpFilterLookupRegistrationService implements FilterLookupRegistrationService {

        @Override
        public FilterLookupRegistrationResponseDto registerFilterLookup(FilterLookupRegistrationRequestDto request) {
            throw new UnsupportedOperationException("Not used in certification tests");
        }
    }

    private static final class NoOpFilterLookupReadService implements FilterLookupReadService {

        @Override
        public List<FilterLookupEffectiveReviewDto> findLookups(String clientId,
                                                                String governanceStatusCode,
                                                                String healthStatusCode,
                                                                String lifecycleStatusCode) {
            throw new UnsupportedOperationException("Not used in certification tests");
        }

        @Override
        public FilterLookupEffectiveReviewDto findLookup(String clientId, String lookupCode) {
            throw new UnsupportedOperationException("Not used in certification tests");
        }
    }

    private static final class NoOpFilterLookupPreviewService implements FilterLookupPreviewService {

        @Override
        public List<FilterLookupPreviewResponseDto> previewLookups(FilterLookupPreviewRequestDto request) {
            throw new UnsupportedOperationException("Not used in certification tests");
        }
    }
}
