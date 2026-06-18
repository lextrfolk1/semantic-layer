package com.lextr.semanticlayer.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lextr.semanticlayer.dto.FilterLookupRegistrationRequestDto;
import com.lextr.semanticlayer.dto.FilterLookupRegistrationResponseDto;
import com.lextr.semanticlayer.exception.FilterLookupRegistrationServiceException;
import com.lextr.semanticlayer.exception.PolicyViolationException;
import com.lextr.semanticlayer.service.FilterLookupRegistrationService;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FilterLookupRegistrationControllerTest {

    @Test
    void routesFilterLookupRegistrationEndpointToService() throws Exception {
        RecordingFilterLookupRegistrationService service = new RecordingFilterLookupRegistrationService();
        service.response = new FilterLookupRegistrationResponseDto(
                101L,
                "LEDGER_SCOPE",
                "MANUAL_LIST",
                120,
                "REVIEW",
                "PENDING",
                LocalDate.parse("2026-09-16"),
                "ACTIVE",
                301L,
                "PENDING"
        );
        MockMvc mockMvc = mockMvc(service);

        mockMvc.perform(post("/api/filter-lookups")
                        .contentType("application/json")
                        .content(validRequestJson()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(101))
                .andExpect(jsonPath("$.lookup_cd").value("LEDGER_SCOPE"))
                .andExpect(jsonPath("$.review_period_days_override").value(120))
                .andExpect(jsonPath("$.governance_status_cd").value("REVIEW"))
                .andExpect(jsonPath("$.workflow_task_id").value(301))
                .andExpect(jsonPath("$.workflow_status_cd").value("PENDING"));

        assertEquals("client-a", service.lastRequest.client_id());
        assertEquals("LEDGER_SCOPE", service.lastRequest.lookup_cd());
        assertEquals("ledger_id", service.lastRequest.filter_attr_cd());
        assertEquals("producer", service.lastRequest.registered_by());
    }

    @Test
    void rejectsFilterAttributeCodeLongerThanThirtyTwoCharacters() throws Exception {
        MockMvc mockMvc = mockMvc(new RecordingFilterLookupRegistrationService());

        mockMvc.perform(post("/api/filter-lookups")
                        .contentType("application/json")
                        .content("""
                                {
                                  "client_id": "client-a",
                                  "lookup_cd": "LEDGER_SCOPE",
                                  "construction_type_cd": "MANUAL_LIST",
                                  "manual_subtype_cd": "HAND_TYPED",
                                  "filter_attr_cd": "123456789012345678901234567890123",
                                  "execution_strategy_cd": "IN_LIST",
                                  "review_period_days_override": 120,
                                  "registered_by": "producer"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void mapsServiceErrorsToInternalServerError() throws Exception {
        RecordingFilterLookupRegistrationService service = new RecordingFilterLookupRegistrationService();
        service.error = new FilterLookupRegistrationServiceException("registration failed");
        MockMvc mockMvc = mockMvc(service);

        mockMvc.perform(post("/api/filter-lookups")
                        .contentType("application/json")
                        .content(validRequestJson()))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void mapsPolicyViolationToUnprocessableEntityWithCode() throws Exception {
        RecordingFilterLookupRegistrationService service = new RecordingFilterLookupRegistrationService();
        service.error = new PolicyViolationException("GOV-FL-001", "Review period override cannot be looser than the governance floor");
        MockMvc mockMvc = mockMvc(service);

        mockMvc.perform(post("/api/filter-lookups")
                        .contentType("application/json")
                        .content(validRequestJson()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("GOV-FL-001"))
                .andExpect(jsonPath("$.message").value("Review period override cannot be looser than the governance floor"));
    }

    private static MockMvc mockMvc(FilterLookupRegistrationService service) {
        FilterLookupRegistrationController controller = new FilterLookupRegistrationController(service);
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
                  "construction_type_cd": "MANUAL_LIST",
                  "manual_subtype_cd": "HAND_TYPED",
                  "filter_obj": "meta.gl_balance",
                  "filter_condition_txt": "ledger_status = 'ACTIVE'",
                  "filter_attr_cd": "ledger_id",
                  "validation_obj": "meta.ledger",
                  "validation_attr_cd": "ledger_id",
                  "suggested_target_attr_cd": "ledger_id",
                  "execution_strategy_cd": "IN_LIST",
                  "max_input_set_size": 500,
                  "max_output_rows": 10000,
                  "cache_ttl_min": 60,
                  "review_period_days_override": 120,
                  "rules_eligible_flg": true,
                  "qs_eligible_flg": true,
                  "ai_eligible_flg": false,
                  "replicate_to_ch_flg": false,
                  "description_txt": "Ledger scope values for governance testing",
                  "registered_by": "producer"
                }
                """;
    }

    private static final class RecordingFilterLookupRegistrationService implements FilterLookupRegistrationService {

        private FilterLookupRegistrationRequestDto lastRequest;
        private FilterLookupRegistrationResponseDto response;
        private RuntimeException error;

        @Override
        public FilterLookupRegistrationResponseDto registerFilterLookup(FilterLookupRegistrationRequestDto request) {
            lastRequest = request;
            if (error != null) {
                throw error;
            }
            return response;
        }
    }
}
