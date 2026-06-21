package com.lextr.semanticlayer.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lextr.semanticlayer.dto.AttributePairingRegistrationRequestDto;
import com.lextr.semanticlayer.dto.AttributePairingRegistrationResponseDto;
import com.lextr.semanticlayer.dto.AttributePairingResolutionRequestDto;
import com.lextr.semanticlayer.dto.AttributePairingResolutionResponseDto;
import com.lextr.semanticlayer.exception.AttributePairingRegistrationServiceException;
import com.lextr.semanticlayer.exception.AttributePairingResolutionServiceException;
import com.lextr.semanticlayer.exception.PolicyViolationException;
import com.lextr.semanticlayer.exception.RegistryResourceNotFoundException;
import com.lextr.semanticlayer.service.AttributePairingRegistrationService;
import com.lextr.semanticlayer.service.AttributePairingResolutionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AttributePairingControllerTest {

    @Test
    void routesAttributePairingRegistrationEndpointToService() throws Exception {
        RecordingAttributePairingRegistrationService registrationService = new RecordingAttributePairingRegistrationService();
        registrationService.response = new AttributePairingRegistrationResponseDto(
                101L,
                "CUSTOMER_NAME_TO_ID",
                "Customer Name To Id",
                "meta",
                "customer",
                "customer_nm",
                "customer_id",
                "DRAFT",
                "PENDING"
        );
        MockMvc mockMvc = mockMvc(registrationService, new RecordingAttributePairingResolutionService());

        mockMvc.perform(post("/api/attribute-pairings")
                        .contentType("application/json")
                        .content(validRegistrationRequestJson()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(101))
                .andExpect(jsonPath("$.pairing_cd").value("CUSTOMER_NAME_TO_ID"))
                .andExpect(jsonPath("$.display_attribute_cd").value("customer_nm"))
                .andExpect(jsonPath("$.filter_attribute_cd").value("customer_id"))
                .andExpect(jsonPath("$.governance_review_status_cd").value("PENDING"));

        assertEquals("CUSTOMER_NAME_TO_ID", registrationService.lastRequest.pairing_cd());
        assertEquals("customer_nm", registrationService.lastRequest.display_attribute_cd());
        assertEquals("customer_id", registrationService.lastRequest.filter_attribute_cd());
        assertEquals("producer", registrationService.lastRequest.registered_by());
    }

    @Test
    void routesAttributePairingResolutionEndpointToService() throws Exception {
        RecordingAttributePairingResolutionService resolutionService = new RecordingAttributePairingResolutionService();
        resolutionService.response = new AttributePairingResolutionResponseDto(
                "CUSTOMER_NAME_TO_ID",
                "meta",
                "customer",
                "customer_nm",
                "customer_id",
                "Acme Corp",
                "CUST-100",
                false,
                true
        );
        MockMvc mockMvc = mockMvc(new RecordingAttributePairingRegistrationService(), resolutionService);

        mockMvc.perform(post("/api/attribute-pairings/resolve")
                        .contentType("application/json")
                        .content(validResolutionRequestJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pairing_cd").value("CUSTOMER_NAME_TO_ID"))
                .andExpect(jsonPath("$.display_value_txt").value("Acme Corp"))
                .andExpect(jsonPath("$.filter_value_txt").value("CUST-100"))
                .andExpect(jsonPath("$.cache_hit_flg").value(true));

        assertEquals("client-a", resolutionService.lastRequest.client_id());
        assertEquals("meta", resolutionService.lastRequest.schema_cd());
        assertEquals("customer", resolutionService.lastRequest.object_cd());
        assertEquals("customer_nm", resolutionService.lastRequest.display_attribute_cd());
        assertEquals("Acme Corp", resolutionService.lastRequest.display_value_txt());
    }

    @Test
    void rejectsMatchingDisplayAndFilterAttributes() throws Exception {
        MockMvc mockMvc = mockMvc(new RecordingAttributePairingRegistrationService(), new RecordingAttributePairingResolutionService());

        mockMvc.perform(post("/api/attribute-pairings")
                        .contentType("application/json")
                        .content("""
                                {
                                  "client_id": "client-a",
                                  "pairing_cd": "CUSTOMER_NAME_TO_ID",
                                  "pairing_nm": "Customer Name To Id",
                                  "schema_cd": "meta",
                                  "object_cd": "customer",
                                  "display_attribute_cd": "customer_id",
                                  "filter_attribute_cd": "customer_id",
                                  "pairing_type_cd": "DISPLAY_TO_FILTER",
                                  "lookup_strategy_cd": "CACHED_LOOKUP",
                                  "cardinality_cd": "ONE_TO_ONE",
                                  "filter_attribute_indexed_flg": true,
                                  "registered_by": "producer"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsDisplayAttributeCodeLongerThanThirtyTwoCharacters() throws Exception {
        MockMvc mockMvc = mockMvc(new RecordingAttributePairingRegistrationService(), new RecordingAttributePairingResolutionService());

        mockMvc.perform(post("/api/attribute-pairings")
                        .contentType("application/json")
                        .content("""
                                {
                                  "client_id": "client-a",
                                  "pairing_cd": "CUSTOMER_NAME_TO_ID",
                                  "pairing_nm": "Customer Name To Id",
                                  "schema_cd": "meta",
                                  "object_cd": "customer",
                                  "display_attribute_cd": "123456789012345678901234567890123",
                                  "filter_attribute_cd": "customer_id",
                                  "pairing_type_cd": "DISPLAY_TO_FILTER",
                                  "lookup_strategy_cd": "CACHED_LOOKUP",
                                  "cardinality_cd": "ONE_TO_ONE",
                                  "filter_attribute_indexed_flg": true,
                                  "registered_by": "producer"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void mapsRegistrationServiceErrorsToInternalServerError() throws Exception {
        RecordingAttributePairingRegistrationService registrationService = new RecordingAttributePairingRegistrationService();
        registrationService.error = new AttributePairingRegistrationServiceException("registration failed");
        MockMvc mockMvc = mockMvc(registrationService, new RecordingAttributePairingResolutionService());

        mockMvc.perform(post("/api/attribute-pairings")
                        .contentType("application/json")
                        .content(validRegistrationRequestJson()))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void mapsResolutionServiceErrorsToInternalServerError() throws Exception {
        RecordingAttributePairingResolutionService resolutionService = new RecordingAttributePairingResolutionService();
        resolutionService.error = new AttributePairingResolutionServiceException("resolution failed");
        MockMvc mockMvc = mockMvc(new RecordingAttributePairingRegistrationService(), resolutionService);

        mockMvc.perform(post("/api/attribute-pairings/resolve")
                        .contentType("application/json")
                        .content(validResolutionRequestJson()))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void mapsPolicyViolationToUnprocessableEntityWithCode() throws Exception {
        RecordingAttributePairingRegistrationService registrationService = new RecordingAttributePairingRegistrationService();
        registrationService.error = new PolicyViolationException("POL-CE-002", "POL-CE-002: cross-engine pairing is not allowed");
        MockMvc mockMvc = mockMvc(registrationService, new RecordingAttributePairingResolutionService());

        mockMvc.perform(post("/api/attribute-pairings")
                        .contentType("application/json")
                        .content(validRegistrationRequestJson()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("POL-CE-002"))
                .andExpect(jsonPath("$.message").value("POL-CE-002: cross-engine pairing is not allowed"));
    }

    @Test
    void mapsMissingPairingToNotFound() throws Exception {
        RecordingAttributePairingResolutionService resolutionService = new RecordingAttributePairingResolutionService();
        resolutionService.error = new RegistryResourceNotFoundException("attribute pairing", "UNKNOWN_PAIRING");
        MockMvc mockMvc = mockMvc(new RecordingAttributePairingRegistrationService(), resolutionService);

        mockMvc.perform(post("/api/attribute-pairings/resolve")
                        .contentType("application/json")
                        .content(validResolutionRequestJson()))
                .andExpect(status().isNotFound());
    }

    private static MockMvc mockMvc(AttributePairingRegistrationService registrationService,
                                   AttributePairingResolutionService resolutionService) {
        AttributePairingController controller = new AttributePairingController(
                providerOf(registrationService),
                providerOf(resolutionService),
                providerOf(null),
                null
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

    private static <T> ObjectProvider<T> providerOf(T instance) {
        return new ObjectProvider<>() {
            @Override
            public T getObject(Object... args) {
                return instance;
            }

            @Override
            public T getIfAvailable() {
                return instance;
            }

            @Override
            public T getIfUnique() {
                return instance;
            }

            @Override
            public T getObject() {
                return instance;
            }

            @Override
            public Iterator<T> iterator() {
                return instance == null ? Collections.emptyIterator() : List.of(instance).iterator();
            }
        };
    }

    private static String validRegistrationRequestJson() {
        return """
                {
                  "client_id": "client-a",
                  "pairing_cd": "CUSTOMER_NAME_TO_ID",
                  "pairing_nm": "Customer Name To Id",
                  "schema_cd": "meta",
                  "object_cd": "customer",
                  "display_attribute_cd": "customer_nm",
                  "filter_attribute_cd": "customer_id",
                  "pairing_type_cd": "DISPLAY_TO_FILTER",
                  "lookup_strategy_cd": "CACHED_LOOKUP",
                  "lookup_cache_enabled_flg": true,
                  "lookup_cache_ttl_seconds_nbr": 3600,
                  "cardinality_cd": "ONE_TO_ONE",
                  "is_bidirectional_flg": false,
                  "is_cross_engine_flg": false,
                  "filter_attribute_indexed_flg": true,
                  "filter_attribute_index_type_cd": "BTREE",
                  "performance_gain_pct_est_nbr": 20,
                  "ai_context_txt": "Resolve customer name to indexed customer id",
                  "registered_by": "producer"
                }
                """;
    }

    private static String validResolutionRequestJson() {
        return """
                {
                  "client_id": "client-a",
                  "schema_cd": "meta",
                  "object_cd": "customer",
                  "display_attribute_cd": "customer_nm",
                  "display_value_txt": "Acme Corp"
                }
                """;
    }

    private static final class RecordingAttributePairingRegistrationService implements AttributePairingRegistrationService {

        private AttributePairingRegistrationRequestDto lastRequest;
        private AttributePairingRegistrationResponseDto response;
        private RuntimeException error;

        @Override
        public AttributePairingRegistrationResponseDto registerPairing(AttributePairingRegistrationRequestDto request) {
            lastRequest = request;
            if (error != null) {
                throw error;
            }
            return response;
        }
    }

    private static final class RecordingAttributePairingResolutionService implements AttributePairingResolutionService {

        private AttributePairingResolutionRequestDto lastRequest;
        private AttributePairingResolutionResponseDto response;
        private RuntimeException error;

        @Override
        public AttributePairingResolutionResponseDto resolvePairing(AttributePairingResolutionRequestDto request) {
            lastRequest = request;
            if (error != null) {
                throw error;
            }
            return response;
        }
    }
}
