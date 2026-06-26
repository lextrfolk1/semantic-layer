package com.lextr.semanticlayer.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lextr.semanticlayer.dto.ConsumptionExposureDto;
import com.lextr.semanticlayer.dto.ConsumptionLayerDto;
import com.lextr.semanticlayer.dto.ConsumptionLayerRegistrationRequestDto;
import com.lextr.semanticlayer.dto.ConsumptionPromotionRequestDto;
import com.lextr.semanticlayer.exception.PolicyViolationException;
import com.lextr.semanticlayer.exception.SemanticLayerException;
import com.lextr.semanticlayer.service.ConsumptionService;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ConsumptionControllerTest {

    @Test
    void routesConsumptionEndpointsToService() throws Exception {
        RecordingConsumptionService service = new RecordingConsumptionService();
        service.layers = List.of(layerDto());
        service.exposures = List.of(exposureDto("DEV"));
        service.promotedExposure = exposureDto("QA");
        MockMvc mockMvc = mockMvc(service);

        mockMvc.perform(get("/api/consumption/layers")
                        .queryParam("client_id", "client-a")
                        .queryParam("lifecycle_status_cd", "ACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].layer_cd").value("CL-01"))
                .andExpect(jsonPath("$[0].layer_nm").value("Finance Layer"));

        mockMvc.perform(get("/api/consumption/layers/CL-01")
                        .queryParam("client_id", "client-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.layer_cd").value("CL-01"));

        mockMvc.perform(get("/api/consumption/exposures")
                        .queryParam("client_id", "client-a")
                        .queryParam("object_id", "00000000-0000-0000-0000-000000000101")
                        .queryParam("structure_type_cd", "TECHNICAL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].outbound_cd").value("OB-01"))
                .andExpect(jsonPath("$[0].structure_type_cd").value("TECHNICAL"))
                .andExpect(jsonPath("$[0].sdlc_status_cd").value("DEV"));

        mockMvc.perform(post("/api/consumption/exposures/101/promote")
                        .queryParam("client_id", "client-a")
                        .contentType("application/json")
                        .content("""
                                {
                                  "target_sdlc_status_cd": "QA",
                                  "promoted_by": "approver",
                                  "promotion_reason_txt": "Promote for QA"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outbound_cd").value("OB-01"))
                .andExpect(jsonPath("$.sdlc_status_cd").value("QA"));

        assertEquals("client-a", service.lastClientId);
        assertEquals("client-a", service.lastPromotionClientId);
        assertEquals(101L, service.lastExposureId);
        assertEquals("QA", service.lastPromotionRequest.target_sdlc_status_cd());
    }

    @Test
    void rejectsPromotionPromoterLongerThanThirtyTwoCharacters() throws Exception {
        MockMvc mockMvc = mockMvc(new RecordingConsumptionService());

        mockMvc.perform(post("/api/consumption/exposures/101/promote")
                        .queryParam("client_id", "client-a")
                        .contentType("application/json")
                        .content("""
                                {
                                  "target_sdlc_status_cd": "QA",
                                  "promoted_by": "123456789012345678901234567890123",
                                  "promotion_reason_txt": "Promote for QA"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("promoted_by: promoted_by must be 32 characters or less"));
    }

    @Test
    void mapsServiceErrorsToHttpStatuses() throws Exception {
        RecordingConsumptionService service = new RecordingConsumptionService();
        service.error = new SemanticLayerException("consumption failed");
        MockMvc mockMvc = mockMvc(service);

        mockMvc.perform(get("/api/consumption/layers")
                        .queryParam("client_id", "client-a"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"))
                .andExpect(jsonPath("$.message").value("consumption failed"));
    }

    @Test
    void mapsPolicyViolationsToUnprocessableEntity() throws Exception {
        RecordingConsumptionService service = new RecordingConsumptionService();
        service.error = new PolicyViolationException("POL-CL-001", "Consumption layer blocked");
        MockMvc mockMvc = mockMvc(service);

        mockMvc.perform(post("/api/consumption/exposures/101/promote")
                        .queryParam("client_id", "client-a")
                        .contentType("application/json")
                        .content("""
                                {
                                  "target_sdlc_status_cd": "QA",
                                  "promoted_by": "approver",
                                  "promotion_reason_txt": "Promote for QA"
                                }
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("POL-CL-001"))
                .andExpect(jsonPath("$.message").value("Consumption layer blocked"));
    }

    private static MockMvc mockMvc(ConsumptionService service) {
        ConsumptionController controller = new ConsumptionController(service);
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        return MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ApiExceptionHandler())
                .setValidator(validator)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    private static ConsumptionLayerDto layerDto() {
        return new ConsumptionLayerDto(
                11L,
                "client-a",
                "CL-01",
                "Finance Layer",
                "Finance outbound descriptor",
                "DATA_ASSET",
                "ACTIVE",
                OffsetDateTime.parse("2026-06-20T10:15:30Z"),
                "owner",
                OffsetDateTime.parse("2026-06-20T10:15:30Z"),
                "owner"
        );
    }

    private static ConsumptionExposureDto exposureDto(String sdlcStatusCode) {
        return new ConsumptionExposureDto(
                101L,
                "client-a",
                "CL-01",
                UUID.fromString("00000000-0000-0000-0000-000000000101").getMostSignificantBits() & Long.MAX_VALUE,
                "OB-01",
                "Outbound 01",
                "TECHNICAL",
                "Technical exposure",
                List.of("ledger_id", "company_id"),
                sdlcStatusCode,
                1,
                OffsetDateTime.parse("2026-06-20T10:15:30Z"),
                "owner",
                OffsetDateTime.parse("2026-06-20T10:15:30Z"),
                "owner"
        );
    }

    private static final class RecordingConsumptionService implements ConsumptionService {

        private List<ConsumptionLayerDto> layers = List.of();
        private ConsumptionLayerDto layer;
        private List<ConsumptionExposureDto> exposures = List.of();
        private ConsumptionExposureDto promotedExposure;
        private String lastClientId;
        private Long lastExposureId;
        private ConsumptionPromotionRequestDto lastPromotionRequest;
        private String lastPromotionClientId;
        private RuntimeException error;

        @Override
        public List<ConsumptionLayerDto> findLayers(String clientId, String lifecycleStatusCode) {
            lastClientId = clientId;
            if (error != null) {
                throw error;
            }
            return layers;
        }

        @Override
        public ConsumptionLayerDto findLayer(String clientId, String layerCode) {
            lastClientId = clientId;
            if (error != null) {
                throw error;
            }
            return layer == null ? layerDto() : layer;
        }

        @Override
        public List<ConsumptionExposureDto> findExposures(String clientId, UUID objectId, String structureTypeCode) {
            lastClientId = clientId;
            if (error != null) {
                throw error;
            }
            return exposures;
        }

        @Override
        public ConsumptionLayerDto registerConsumptionLayer(ConsumptionLayerRegistrationRequestDto request) {
            if (error != null) {
                throw error;
            }
            return layer == null ? layerDto() : layer;
        }

        @Override
        public ConsumptionExposureDto promoteExposure(String clientId, Long exposureId, ConsumptionPromotionRequestDto request) {
            lastPromotionClientId = clientId;
            lastExposureId = exposureId;
            lastPromotionRequest = request;
            if (error != null) {
                throw error;
            }
            return promotedExposure == null ? exposureDto(request.target_sdlc_status_cd()) : promotedExposure;
        }
    }
}
