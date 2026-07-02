package com.lextr.semanticlayer.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lextr.semanticlayer.dto.GovernanceHistoryEventDto;
import com.lextr.semanticlayer.exception.PolicyViolationException;
import com.lextr.semanticlayer.exception.RegistryResourceNotFoundException;
import com.lextr.semanticlayer.service.GovernanceHistoryReadService;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GovernanceHistoryControllerTest {

    @Test
    void appliesFiltersAndClientScoping() throws Exception {
        MockGovernanceHistoryReadService service = new MockGovernanceHistoryReadService();
        service.records.add(historyDto("APPROVED"));
        MockMvc mockMvc = mockMvc(service);

        mockMvc.perform(get("/api/governance/history")
                        .queryParam("client_id", "client-a")
                        .queryParam("entity_type_cd", "OBJECT")
                        .queryParam("entity_ref", "meta.gl_balance")
                        .queryParam("change_type_cd", "APPROVED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].entity_type_cd").value("OBJECT"))
                .andExpect(jsonPath("$[0].entity_ref").value("meta.gl_balance"))
                .andExpect(jsonPath("$[0].change_type_cd").value("APPROVED"));

        assertEquals("client-a", service.lastClientId);
        assertEquals("OBJECT", service.lastEntityTypeCode);
        assertEquals("meta.gl_balance", service.lastEntityRef);
        assertEquals("APPROVED", service.lastChangeTypeCode);
    }

    @Test
    void returns404ForUnknownEntity() throws Exception {
        MockMvc mockMvc = mockMvc(new MockGovernanceHistoryReadService());

        mockMvc.perform(get("/api/governance/history")
                        .queryParam("client_id", "client-a")
                        .queryParam("entity_type_cd", "OBJECT")
                        .queryParam("entity_ref", "missing"))
                .andExpect(status().isNotFound());
    }

    @Test
    void returns422WhenClientIdBlank() throws Exception {
        MockGovernanceHistoryReadService service = new MockGovernanceHistoryReadService() {
            @Override
            public List<GovernanceHistoryEventDto> findHistory(String clientId,
                                                               String entityTypeCode,
                                                               String entityRef,
                                                               String changeTypeCode) {
                if (clientId == null || clientId.isBlank()) {
                    throw new PolicyViolationException("CLIENT_ID_REQUIRED", "client_id is required");
                }
                return super.findHistory(clientId, entityTypeCode, entityRef, changeTypeCode);
            }
        };
        MockMvc mockMvc = mockMvc(service);

        mockMvc.perform(get("/api/governance/history")
                        .queryParam("client_id", "")
                        .queryParam("entity_type_cd", "OBJECT")
                        .queryParam("entity_ref", "meta.gl_balance"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("CLIENT_ID_REQUIRED"));
    }

    private static MockMvc mockMvc(GovernanceHistoryReadService service) {
        GovernanceHistoryController controller = new GovernanceHistoryController(service);
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        return MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ApiExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    private static GovernanceHistoryEventDto historyDto(String changeTypeCode) {
        return new GovernanceHistoryEventDto(
                501L,
                "client-a",
                "OBJECT",
                "meta.gl_balance",
                changeTypeCode,
                "Object registered",
                "producer",
                OffsetDateTime.parse("2026-06-18T00:00:00Z"),
                "{\"status\":\"DRAFT\"}",
                "{\"status\":\"ACTIVE\"}"
        );
    }

    private static class MockGovernanceHistoryReadService implements GovernanceHistoryReadService {
        private final List<GovernanceHistoryEventDto> records = new ArrayList<>();
        private String lastClientId;
        private String lastEntityTypeCode;
        private String lastEntityRef;
        private String lastChangeTypeCode;

        @Override
        public List<GovernanceHistoryEventDto> findHistory(String clientId,
                                                           String entityTypeCode,
                                                           String entityRef,
                                                           String changeTypeCode) {
            this.lastClientId = clientId;
            this.lastEntityTypeCode = entityTypeCode;
            this.lastEntityRef = entityRef;
            this.lastChangeTypeCode = changeTypeCode;
            List<GovernanceHistoryEventDto> filtered = records.stream()
                    .filter(record -> changeTypeCode == null || changeTypeCode.equals(record.change_type_cd()))
                    .toList();
            if (filtered.isEmpty()) {
                throw new RegistryResourceNotFoundException("governance history", entityTypeCode + "/" + entityRef);
            }
            return filtered;
        }
    }
}
