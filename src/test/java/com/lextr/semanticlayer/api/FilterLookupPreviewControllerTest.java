package com.lextr.semanticlayer.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lextr.semanticlayer.dto.FilterLookupPreviewRequestDto;
import com.lextr.semanticlayer.dto.FilterLookupPreviewResponseDto;
import com.lextr.semanticlayer.dto.FilterLookupPreviewValueDto;
import com.lextr.semanticlayer.dto.FilterLookupRegistrationRequestDto;
import com.lextr.semanticlayer.dto.FilterLookupRegistrationResponseDto;
import com.lextr.semanticlayer.exception.FilterLookupPreviewServiceException;
import com.lextr.semanticlayer.exception.RegistryResourceNotFoundException;
import com.lextr.semanticlayer.service.FilterLookupPreviewService;
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

class FilterLookupPreviewControllerTest {

    @Test
    void routesFilterLookupPreviewEndpointToService() throws Exception {
        RecordingFilterLookupPreviewService service = new RecordingFilterLookupPreviewService();
        service.response = new FilterLookupPreviewResponseDto(
                801L,
                "LEDGER_SCOPE",
                "MANUAL_LIST",
                "client-a",
                "IN_LIST",
                "SUCCESS",
                OffsetDateTime.parse("2026-06-18T10:15:30Z"),
                18,
                2,
                false,
                2L,
                List.of(new FilterLookupPreviewValueDto(
                        "LEDGER_100",
                        "Ledger 100",
                        "ACTIVE",
                        true,
                        LocalDate.parse("2026-07-01"),
                        "WKFL-100",
                        OffsetDateTime.parse("2026-06-18T10:00:00Z"),
                        14,
                        "Pending activation",
                        "producer",
                        OffsetDateTime.parse("2026-06-18T09:00:00Z"),
                        "reviewer",
                        OffsetDateTime.parse("2026-06-18T11:00:00Z"),
                        OffsetDateTime.parse("2026-06-18T12:00:00Z")
                ))
        );
        MockMvc mockMvc = mockMvc(service);

        mockMvc.perform(post("/api/filter-lookups/preview")
                        .contentType("application/json")
                        .content(validRequestJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.execution_log_id").value(801))
                .andExpect(jsonPath("$.lookup_cd").value("LEDGER_SCOPE"))
                .andExpect(jsonPath("$.construction_type_cd").value("MANUAL_LIST"))
                .andExpect(jsonPath("$.execution_strategy_used_cd").value("IN_LIST"))
                .andExpect(jsonPath("$.result_status_cd").value("SUCCESS"))
                .andExpect(jsonPath("$.value_count").value(2))
                .andExpect(jsonPath("$.preview_values[0].value_cd").value("LEDGER_100"))
                .andExpect(jsonPath("$.preview_values[0].lifecycle_status_cd").value("ACTIVE"));

        assertEquals("client-a", service.lastRequest.client_id());
        assertEquals("LEDGER_SCOPE", service.lastRequest.lookup_cd());
        assertEquals("preview-user", service.lastRequest.executed_by());
    }

    @Test
    void rejectsLookupCodeLongerThanSixtyCharacters() throws Exception {
        MockMvc mockMvc = mockMvc(new RecordingFilterLookupPreviewService());

        mockMvc.perform(post("/api/filter-lookups/preview")
                        .contentType("application/json")
                        .content("""
                                {
                                  "client_id": "client-a",
                                  "lookup_cd": "1234567890123456789012345678901234567890123456789012345678901",
                                  "executed_by": "preview-user"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void mapsMissingLookupToNotFound() throws Exception {
        RecordingFilterLookupPreviewService service = new RecordingFilterLookupPreviewService();
        service.error = new RegistryResourceNotFoundException("filter lookup", "MISSING");
        MockMvc mockMvc = mockMvc(service);

        mockMvc.perform(post("/api/filter-lookups/preview")
                        .contentType("application/json")
                        .content(validRequestJson()))
                .andExpect(status().isNotFound());
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

    private static MockMvc mockMvc(FilterLookupPreviewService service) {
        FilterLookupRegistrationController controller = new FilterLookupRegistrationController(
                new NoOpFilterLookupRegistrationService(),
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
                  "lookup_cd": "LEDGER_SCOPE",
                  "executed_by": "preview-user"
                }
                """;
    }

    private static final class RecordingFilterLookupPreviewService implements FilterLookupPreviewService {

        private FilterLookupPreviewRequestDto lastRequest;
        private FilterLookupPreviewResponseDto response;
        private RuntimeException error;

        @Override
        public FilterLookupPreviewResponseDto previewFilterLookup(FilterLookupPreviewRequestDto request) {
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
}
