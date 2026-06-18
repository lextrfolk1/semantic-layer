package com.lextr.semanticlayer.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lextr.semanticlayer.dto.FilterLookupPreviewRequestDto;
import com.lextr.semanticlayer.dto.FilterLookupPreviewResponseDto;
import com.lextr.semanticlayer.dto.FilterLookupPreviewValueDto;
import com.lextr.semanticlayer.dto.FilterLookupEffectiveReviewDto;
import com.lextr.semanticlayer.dto.FilterLookupRegistrationRequestDto;
import com.lextr.semanticlayer.dto.FilterLookupRegistrationResponseDto;
import com.lextr.semanticlayer.exception.FilterLookupPreviewServiceException;
import com.lextr.semanticlayer.exception.PolicyViolationException;
import com.lextr.semanticlayer.exception.RegistryResourceNotFoundException;
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
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FilterLookupPreviewControllerTest {

    @Test
    void routesFilterLookupPreviewEndpointToService() throws Exception {
        RecordingFilterLookupPreviewService service = new RecordingFilterLookupPreviewService();
        service.response = List.of(new FilterLookupPreviewResponseDto(
                "LEDGER_SCOPE",
                "MANUAL_LIST",
                "IN_LIST",
                2,
                false,
                "SUCCESS",
                List.of(new FilterLookupPreviewValueDto(
                        "LEDGER_100",
                        "Ledger 100",
                        "ACTIVE",
                        true,
                        LocalDate.parse("2026-07-01"),
                        "WKFL-100",
                        OffsetDateTime.parse("2026-06-18T10:15:30Z"),
                        14,
                        "Pending activation",
                        "producer",
                        OffsetDateTime.parse("2026-06-18T09:15:30Z"),
                        "reviewer",
                        OffsetDateTime.parse("2026-06-18T11:15:30Z"),
                        OffsetDateTime.parse("2026-06-18T12:15:30Z")
                ))
        ));
        MockMvc mockMvc = mockMvc(service);

        mockMvc.perform(post("/api/filter-lookups/preview")
                        .contentType("application/json")
                        .content(validRequestJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].lookup_cd").value("LEDGER_SCOPE"))
                .andExpect(jsonPath("$[0].construction_type_cd").value("MANUAL_LIST"))
                .andExpect(jsonPath("$[0].phase1_row_count").value(2))
                .andExpect(jsonPath("$[0].result_status_cd").value("SUCCESS"))
                .andExpect(jsonPath("$[0].values[0].value_cd").value("LEDGER_100"))
                .andExpect(jsonPath("$[0].values[0].workflow_ref").value("WKFL-100"));

        assertEquals("client-a", service.lastRequest.client_id());
        assertEquals("preview-user", service.lastRequest.executed_by());
        assertEquals(List.of("LEDGER_SCOPE", "OPEN_PERIODS"), service.lastRequest.lookup_codes());
    }

    @Test
    void rejectsMoreThanThirtyTwoLookupCodes() throws Exception {
        MockMvc mockMvc = mockMvc(new RecordingFilterLookupPreviewService());

        String lookupCodesJson = IntStream.rangeClosed(1, 33)
                .mapToObj(index -> "\"LOOKUP_" + index + "\"")
                .reduce((left, right) -> left + "," + right)
                .orElse("");

        mockMvc.perform(post("/api/filter-lookups/preview")
                        .contentType("application/json")
                        .content("""
                                {
                                  "client_id": "client-a",
                                  "executed_by": "preview-user",
                                  "lookup_codes": [%s]
                                }
                                """.formatted(lookupCodesJson)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void mapsPreviewServiceErrorsToInternalServerError() throws Exception {
        RecordingFilterLookupPreviewService service = new RecordingFilterLookupPreviewService();
        service.error = new FilterLookupPreviewServiceException("preview failed");
        MockMvc mockMvc = mockMvc(service);

        mockMvc.perform(post("/api/filter-lookups/preview")
                        .contentType("application/json")
                        .content(validRequestJson()))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void mapsPolicyViolationToUnprocessableEntityWithCode() throws Exception {
        RecordingFilterLookupPreviewService service = new RecordingFilterLookupPreviewService();
        service.error = new PolicyViolationException("GOV-FL-004", "Anticipated values require approval before preview");
        MockMvc mockMvc = mockMvc(service);

        mockMvc.perform(post("/api/filter-lookups/preview")
                        .contentType("application/json")
                        .content(validRequestJson()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("GOV-FL-004"))
                .andExpect(jsonPath("$.message").value("Anticipated values require approval before preview"));
    }

    @Test
    void mapsMissingLookupToNotFound() throws Exception {
        RecordingFilterLookupPreviewService service = new RecordingFilterLookupPreviewService();
        service.error = new RegistryResourceNotFoundException("filter lookup", "UNKNOWN_LOOKUP");
        MockMvc mockMvc = mockMvc(service);

        mockMvc.perform(post("/api/filter-lookups/preview")
                        .contentType("application/json")
                        .content(validRequestJson()))
                .andExpect(status().isNotFound());
    }

    private static MockMvc mockMvc(FilterLookupPreviewService service) {
        FilterLookupRegistrationController controller = new FilterLookupRegistrationController(
                new NoOpFilterLookupRegistrationService(),
                new NoOpFilterLookupReadService(),
                service
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
                  "executed_by": "preview-user",
                  "lookup_codes": [
                    "LEDGER_SCOPE",
                    "OPEN_PERIODS"
                  ]
                }
                """;
    }

    private static final class RecordingFilterLookupPreviewService implements FilterLookupPreviewService {

        private FilterLookupPreviewRequestDto lastRequest;
        private List<FilterLookupPreviewResponseDto> response = List.of();
        private RuntimeException error;

        @Override
        public List<FilterLookupPreviewResponseDto> previewLookups(FilterLookupPreviewRequestDto request) {
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
            throw new UnsupportedOperationException("Not used in preview tests");
        }
    }

    private static final class NoOpFilterLookupReadService implements FilterLookupReadService {

        @Override
        public List<FilterLookupEffectiveReviewDto> findLookups(String clientId,
                                                                String governanceStatusCode,
                                                                String healthStatusCode,
                                                                String lifecycleStatusCode) {
            throw new UnsupportedOperationException("Not used in preview tests");
        }

        @Override
        public FilterLookupEffectiveReviewDto findLookup(String clientId, String lookupCode) {
            throw new UnsupportedOperationException("Not used in preview tests");
        }
    }
}
