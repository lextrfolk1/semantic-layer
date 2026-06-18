package com.lextr.semanticlayer.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lextr.semanticlayer.dto.FilterLookupBindingRequestDto;
import com.lextr.semanticlayer.dto.FilterLookupBindingResponseDto;
import com.lextr.semanticlayer.dto.FilterLookupCertificationRequestDto;
import com.lextr.semanticlayer.dto.FilterLookupEffectiveReviewDto;
import com.lextr.semanticlayer.dto.FilterLookupPreviewRequestDto;
import com.lextr.semanticlayer.dto.FilterLookupPreviewResponseDto;
import com.lextr.semanticlayer.dto.FilterLookupRegistrationRequestDto;
import com.lextr.semanticlayer.dto.FilterLookupRegistrationResponseDto;
import com.lextr.semanticlayer.exception.FilterLookupBindingServiceException;
import com.lextr.semanticlayer.exception.PolicyViolationException;
import com.lextr.semanticlayer.exception.RegistryResourceNotFoundException;
import com.lextr.semanticlayer.service.FilterLookupBindingService;
import com.lextr.semanticlayer.service.FilterLookupCertificationService;
import com.lextr.semanticlayer.service.FilterLookupPreviewService;
import com.lextr.semanticlayer.service.FilterLookupReadService;
import com.lextr.semanticlayer.service.FilterLookupRegistrationService;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FilterLookupBindingControllerTest {

    @Test
    void routesFilterLookupBindingEndpointToService() throws Exception {
        RecordingFilterLookupBindingService service = new RecordingFilterLookupBindingService();
        service.response = new FilterLookupBindingResponseDto(
                501L,
                "LEDGER_SCOPE",
                "meta.gl_balance",
                "ledger_id",
                "PIPELINE",
                "daily-pipeline",
                "binder",
                OffsetDateTime.parse("2026-06-18T10:15:30Z"),
                true
        );
        MockMvc mockMvc = mockMvc(service);

        mockMvc.perform(post("/api/filter-lookups/{lookup_code}/bindings", "LEDGER_SCOPE")
                        .contentType("application/json")
                        .content(validRequestJson()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(501))
                .andExpect(jsonPath("$.lookup_cd").value("LEDGER_SCOPE"))
                .andExpect(jsonPath("$.bound_obj").value("meta.gl_balance"))
                .andExpect(jsonPath("$.bound_attr_cd").value("ledger_id"))
                .andExpect(jsonPath("$.binding_context_cd").value("PIPELINE"))
                .andExpect(jsonPath("$.binding_ref").value("daily-pipeline"))
                .andExpect(jsonPath("$.is_active_flg").value(true));

        assertEquals("LEDGER_SCOPE", service.lastLookupCode);
        assertEquals("client-a", service.lastRequest.client_id());
        assertEquals("meta.gl_balance", service.lastRequest.bound_obj());
        assertEquals("ledger_id", service.lastRequest.bound_attr_cd());
        assertEquals("PIPELINE", service.lastRequest.binding_context_cd());
    }

    @Test
    void rejectsBoundAttributeCodeLongerThanThirtyTwoCharacters() throws Exception {
        MockMvc mockMvc = mockMvc(new RecordingFilterLookupBindingService());

        mockMvc.perform(post("/api/filter-lookups/{lookup_code}/bindings", "LEDGER_SCOPE")
                        .contentType("application/json")
                        .content("""
                                {
                                  "client_id": "client-a",
                                  "bound_obj": "meta.gl_balance",
                                  "bound_attr_cd": "123456789012345678901234567890123",
                                  "binding_context_cd": "PIPELINE",
                                  "binding_ref": "daily-pipeline",
                                  "bound_by": "binder"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void mapsBindingServiceErrorsToInternalServerError() throws Exception {
        RecordingFilterLookupBindingService service = new RecordingFilterLookupBindingService();
        service.error = new FilterLookupBindingServiceException("binding failed");
        MockMvc mockMvc = mockMvc(service);

        mockMvc.perform(post("/api/filter-lookups/{lookup_code}/bindings", "LEDGER_SCOPE")
                        .contentType("application/json")
                        .content(validRequestJson()))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void mapsPolicyViolationToUnprocessableEntityWithCode() throws Exception {
        RecordingFilterLookupBindingService service = new RecordingFilterLookupBindingService();
        service.error = new PolicyViolationException(
                "POL-SV-002",
                "POL-SV-002: overdue lookup binding is blocked for PIPELINE"
        );
        MockMvc mockMvc = mockMvc(service);

        mockMvc.perform(post("/api/filter-lookups/{lookup_code}/bindings", "LEDGER_SCOPE")
                        .contentType("application/json")
                        .content(validRequestJson()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("POL-SV-002"))
                .andExpect(jsonPath("$.message").value("POL-SV-002: overdue lookup binding is blocked for PIPELINE"));
    }

    @Test
    void mapsMissingLookupToNotFound() throws Exception {
        RecordingFilterLookupBindingService service = new RecordingFilterLookupBindingService();
        service.error = new RegistryResourceNotFoundException("filter lookup", "UNKNOWN_LOOKUP");
        MockMvc mockMvc = mockMvc(service);

        mockMvc.perform(post("/api/filter-lookups/{lookup_code}/bindings", "UNKNOWN_LOOKUP")
                        .contentType("application/json")
                        .content(validRequestJson()))
                .andExpect(status().isNotFound());
    }

    private static MockMvc mockMvc(FilterLookupBindingService service) {
        FilterLookupRegistrationController controller = new FilterLookupRegistrationController(
                new NoOpFilterLookupRegistrationService(),
                new NoOpFilterLookupReadService(),
                service,
                new NoOpFilterLookupCertificationService(),
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
                  "bound_obj": "meta.gl_balance",
                  "bound_attr_cd": "ledger_id",
                  "binding_context_cd": "PIPELINE",
                  "binding_ref": "daily-pipeline",
                  "bound_by": "binder"
                }
                """;
    }

    private static final class RecordingFilterLookupBindingService implements FilterLookupBindingService {

        private String lastLookupCode;
        private FilterLookupBindingRequestDto lastRequest;
        private FilterLookupBindingResponseDto response;
        private RuntimeException error;

        @Override
        public FilterLookupBindingResponseDto bindLookup(String lookupCode, FilterLookupBindingRequestDto request) {
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
            throw new UnsupportedOperationException("Not used in binding tests");
        }
    }

    private static final class NoOpFilterLookupReadService implements FilterLookupReadService {

        @Override
        public List<FilterLookupEffectiveReviewDto> findLookups(String clientId,
                                                                String governanceStatusCode,
                                                                String healthStatusCode,
                                                                String lifecycleStatusCode) {
            throw new UnsupportedOperationException("Not used in binding tests");
        }

        @Override
        public FilterLookupEffectiveReviewDto findLookup(String clientId, String lookupCode) {
            throw new UnsupportedOperationException("Not used in binding tests");
        }
    }

    private static final class NoOpFilterLookupCertificationService implements FilterLookupCertificationService {

        @Override
        public FilterLookupEffectiveReviewDto certifyLookup(String lookupCode, FilterLookupCertificationRequestDto request) {
            throw new UnsupportedOperationException("Not used in binding tests");
        }
    }

    private static final class NoOpFilterLookupPreviewService implements FilterLookupPreviewService {

        @Override
        public List<FilterLookupPreviewResponseDto> previewLookups(FilterLookupPreviewRequestDto request) {
            throw new UnsupportedOperationException("Not used in binding tests");
        }
    }
}
