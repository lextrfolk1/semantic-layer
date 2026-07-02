package com.lextr.semanticlayer.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lextr.semanticlayer.dto.LogicalPhysicalResolutionDto;
import com.lextr.semanticlayer.exception.SemanticLayerException;
import com.lextr.semanticlayer.service.LogicalPhysicalResolutionService;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LogicalPhysicalResolutionControllerTest {

    @Test
    void routesAttributeResolutionEndpointToService() throws Exception {
        RecordingLogicalPhysicalResolutionService service = new RecordingLogicalPhysicalResolutionService();
        service.attributeResponses = List.of(new LogicalPhysicalResolutionDto(
                101L,
                "OB-01",
                1,
                "client-a",
                "meta",
                "gl_balance",
                "ledger_id",
                "Ledger ID",
                "ledger_id",
                "GL Balance",
                "POSTGRES",
                "NUMERIC",
                false
        ));
        MockMvc mockMvc = mockMvc(service);

        mockMvc.perform(get("/api/logical-physical-resolutions/attributes")
                        .queryParam("client_id", "client-a")
                        .queryParam("schema_cd", "meta")
                        .queryParam("object_cd", "gl_balance")
                        .queryParam("logical_attribute_cd", "ledger_id")
                        .queryParam("logical_attribute_cd", "company_id"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].outbound_id").value(101))
                .andExpect(jsonPath("$[0].logical_attribute_cd").value("ledger_id"))
                .andExpect(jsonPath("$[0].engine_cd").value("POSTGRES"));

        assertEquals("client-a", service.lastClientId);
        assertEquals("meta", service.lastSchemaCode);
        assertEquals("gl_balance", service.lastObjectCode);
        assertEquals(List.of("ledger_id", "company_id"), service.lastLogicalAttributeCodes);
    }

    @Test
    void routesOutboundResolutionEndpointToService() throws Exception {
        RecordingLogicalPhysicalResolutionService service = new RecordingLogicalPhysicalResolutionService();
        service.outboundResponses = List.of(new LogicalPhysicalResolutionDto(
                101L,
                "OB-01",
                2,
                "client-a",
                "meta",
                "gl_balance",
                "company_id",
                "Company ID",
                "company_id",
                "GL Balance",
                "SPARK",
                "STRING",
                true
        ));
        MockMvc mockMvc = mockMvc(service);

        mockMvc.perform(get("/api/logical-physical-resolutions/outbounds/{outbound_id}", 101L)
                        .queryParam("client_id", "client-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].outbound_cd").value("OB-01"))
                .andExpect(jsonPath("$[0].masked_flg").value(true));

        assertEquals("client-a", service.lastClientId);
        assertEquals(101L, service.lastOutboundId);
    }

    @Test
    void mapsServiceErrorsToInternalServerError() throws Exception {
        RecordingLogicalPhysicalResolutionService service = new RecordingLogicalPhysicalResolutionService();
        service.error = new SemanticLayerException("logical resolution failed");
        MockMvc mockMvc = mockMvc(service);

        mockMvc.perform(get("/api/logical-physical-resolutions/attributes")
                        .queryParam("client_id", "client-a")
                        .queryParam("schema_cd", "meta")
                        .queryParam("object_cd", "gl_balance")
                        .queryParam("logical_attribute_cd", "ledger_id"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"))
                .andExpect(jsonPath("$.message").value("logical resolution failed"));
    }

    private static MockMvc mockMvc(LogicalPhysicalResolutionService service) {
        LogicalPhysicalResolutionController controller = new LogicalPhysicalResolutionController(service);
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        return MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ApiExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    private static final class RecordingLogicalPhysicalResolutionService implements LogicalPhysicalResolutionService {

        private List<LogicalPhysicalResolutionDto> attributeResponses = List.of();
        private List<LogicalPhysicalResolutionDto> outboundResponses = List.of();
        private String lastClientId;
        private String lastSchemaCode;
        private String lastObjectCode;
        private List<String> lastLogicalAttributeCodes = List.of();
        private Long lastOutboundId;
        private RuntimeException error;

        @Override
        public List<LogicalPhysicalResolutionDto> resolveAttributes(String clientId, String schemaCode, String objectCode, List<String> logicalAttributeCodes) {
            lastClientId = clientId;
            lastSchemaCode = schemaCode;
            lastObjectCode = objectCode;
            lastLogicalAttributeCodes = logicalAttributeCodes;
            if (error != null) {
                throw error;
            }
            return attributeResponses;
        }

        @Override
        public List<LogicalPhysicalResolutionDto> resolveOutboundGrain(String clientId, Long outboundId) {
            lastClientId = clientId;
            lastOutboundId = outboundId;
            if (error != null) {
                throw error;
            }
            return outboundResponses;
        }
    }
}
