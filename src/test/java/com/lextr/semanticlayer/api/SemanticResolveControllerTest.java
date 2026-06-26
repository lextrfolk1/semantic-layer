package com.lextr.semanticlayer.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lextr.semanticlayer.dto.LogicalPhysicalResolutionDto;
import com.lextr.semanticlayer.dto.SemanticResolveRequestDto;
import com.lextr.semanticlayer.service.SemanticResolveService;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SemanticResolveControllerTest {

    @Test
    void routesSemanticResolveEndpointToService() throws Exception {
        RecordingSemanticResolveService service = new RecordingSemanticResolveService();
        service.response = List.of(new LogicalPhysicalResolutionDto(
                null,
                null,
                null,
                "client-a",
                "meta",
                "ledger",
                "LEDGER_ID",
                "REDACTED",
                "ledger_id",
                "ledger_source",
                "POSTGRES",
                "STRING",
                true
        ));
        MockMvc mockMvc = mockMvc(service);

        mockMvc.perform(post("/api/semantic/resolve")
                        .header("X-Actor-Id", "engine-1")
                        .header("X-Role-Cd", "ENGINE")
                        .header("X-Purpose-Cd", "RESOLUTION")
                        .contentType("application/json")
                        .content("""
                                {
                                  "client_id": "client-a",
                                  "schema_cd": "meta",
                                  "object_cd": "ledger",
                                  "logical_attribute_cd": ["LEDGER_ID", "ACCOUNT_NO"]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].logical_attribute_cd").value("LEDGER_ID"))
                .andExpect(jsonPath("$[0].effective_logical_attribute_nm").value("REDACTED"))
                .andExpect(jsonPath("$[0].masked_flg").value(true));

        assertEquals("client-a", service.lastRequest.client_id());
        assertEquals("meta", service.lastRequest.schema_cd());
        assertEquals("ledger", service.lastRequest.object_cd());
        assertEquals(List.of("LEDGER_ID", "ACCOUNT_NO"), service.lastRequest.logical_attribute_cd());
        assertEquals("engine-1", service.lastActorId);
        assertEquals("ENGINE", service.lastRoleCode);
        assertEquals("RESOLUTION", service.lastPurposeCode);
        assertTrue(service.resolveAttributesCalled);
    }

    private static MockMvc mockMvc(SemanticResolveService service) {
        SemanticResolveController controller = new SemanticResolveController(service);
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

        private SemanticResolveRequestDto lastRequest;
        private String lastActorId;
        private String lastRoleCode;
        private String lastPurposeCode;
        private boolean resolveAttributesCalled;
        private List<LogicalPhysicalResolutionDto> response = List.of();

        @Override
        public List<LogicalPhysicalResolutionDto> resolveAttributes(SemanticResolveRequestDto request, String actorId, String roleCode, String purposeCode) {
            resolveAttributesCalled = true;
            lastRequest = request;
            lastActorId = actorId;
            lastRoleCode = roleCode;
            lastPurposeCode = purposeCode;
            return response;
        }

        @Override
        public List<LogicalPhysicalResolutionDto> resolveOutboundGrain(String clientId, String actorId, String roleCode, String purposeCode, Long outboundId) {
            throw new UnsupportedOperationException("Not used");
        }
    }
}
