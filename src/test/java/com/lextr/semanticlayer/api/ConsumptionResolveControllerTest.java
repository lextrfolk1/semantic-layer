package com.lextr.semanticlayer.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lextr.semanticlayer.dto.LogicalPhysicalResolutionDto;
import com.lextr.semanticlayer.service.SemanticResolveService;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ConsumptionResolveControllerTest {

    @Test
    void routesConsumptionResolveEndpointToService() throws Exception {
        RecordingSemanticResolveService service = new RecordingSemanticResolveService();
        service.response = List.of(new LogicalPhysicalResolutionDto(
                101L,
                "OB-101",
                1,
                "client-a",
                "meta",
                "ledger",
                "LEDGER_ID",
                "Ledger Identifier Override",
                "ledger_id",
                "ledger_source",
                "POSTGRES",
                "STRING",
                false
        ));
        MockMvc mockMvc = mockMvc(service);

        mockMvc.perform(get("/api/consumption/101/resolve")
                        .queryParam("client_id", "client-a")
                        .header("X-Actor-Id", "engine-1")
                        .header("X-Role-Cd", "ENGINE")
                        .header("X-Purpose-Cd", "RESOLUTION"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].outbound_id").value(101))
                .andExpect(jsonPath("$[0].outbound_cd").value("OB-101"))
                .andExpect(jsonPath("$[0].masked_flg").value(false));

        assertEquals("client-a", service.lastClientId);
        assertEquals(101L, service.lastOutboundId);
        assertEquals("engine-1", service.lastActorId);
        assertEquals("ENGINE", service.lastRoleCode);
        assertEquals("RESOLUTION", service.lastPurposeCode);
        assertTrue(service.resolveOutboundCalled);
    }

    private static MockMvc mockMvc(SemanticResolveService service) {
        ConsumptionResolveController controller = new ConsumptionResolveController(service);
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        return MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ApiExceptionHandler())
                .setValidator(validator)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    private static final class RecordingSemanticResolveService implements SemanticResolveService {

        private String lastClientId;
        private Long lastOutboundId;
        private String lastActorId;
        private String lastRoleCode;
        private String lastPurposeCode;
        private boolean resolveOutboundCalled;
        private List<LogicalPhysicalResolutionDto> response = List.of();

        @Override
        public List<LogicalPhysicalResolutionDto> resolveAttributes(com.lextr.semanticlayer.dto.SemanticResolveRequestDto request, String actorId, String roleCode, String purposeCode) {
            throw new UnsupportedOperationException("Not used");
        }

        @Override
        public List<LogicalPhysicalResolutionDto> resolveOutboundGrain(String clientId, String actorId, String roleCode, String purposeCode, Long outboundId) {
            resolveOutboundCalled = true;
            lastClientId = clientId;
            lastOutboundId = outboundId;
            lastActorId = actorId;
            lastRoleCode = roleCode;
            lastPurposeCode = purposeCode;
            return response;
        }
    }
}
