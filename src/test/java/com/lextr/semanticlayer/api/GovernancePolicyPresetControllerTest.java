package com.lextr.semanticlayer.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lextr.semanticlayer.dto.GovernancePolicyPresetDto;
import com.lextr.semanticlayer.exception.GovernancePolicyPresetNotFoundException;
import com.lextr.semanticlayer.exception.PolicyViolationException;
import com.lextr.semanticlayer.service.GovernancePolicyPresetReadService;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GovernancePolicyPresetControllerTest {

    @Test
    void findPolicyPresetsAppliesFiltersAndClientScoping() throws Exception {
        MockGovernancePolicyPresetReadService service = new MockGovernancePolicyPresetReadService();
        service.records.add(presetDto("GOV-FL-001", "FILTER_LOOKUP"));
        MockMvc mockMvc = mockMvc(service);

        mockMvc.perform(get("/api/governance/policy-presets")
                        .queryParam("client_id", "client-a")
                        .queryParam("policy_scope_cd", "FILTER_LOOKUP")
                        .queryParam("as_of_dt", "2026-06-18"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].policy_cd").value("GOV-FL-001"))
                .andExpect(jsonPath("$[0].policy_scope_cd").value("FILTER_LOOKUP"));

        assertEquals("client-a", service.lastClientId);
        assertEquals("FILTER_LOOKUP", service.lastScopeCode);
        assertEquals(LocalDate.parse("2026-06-18"), service.lastAsOfDate);
    }

    @Test
    void findPolicyPresetReturnsSinglePreset() throws Exception {
        MockGovernancePolicyPresetReadService service = new MockGovernancePolicyPresetReadService();
        service.records.add(presetDto("GOV-FL-001", "FILTER_LOOKUP"));
        MockMvc mockMvc = mockMvc(service);

        mockMvc.perform(get("/api/governance/policy-presets/GOV-FL-001")
                        .queryParam("client_id", "client-a")
                        .queryParam("policy_scope_cd", "FILTER_LOOKUP")
                        .queryParam("as_of_dt", "2026-06-18"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.policy_cd").value("GOV-FL-001"))
                .andExpect(jsonPath("$.policy_scope_cd").value("FILTER_LOOKUP"));

        assertEquals("client-a", service.lastClientId);
        assertEquals("GOV-FL-001", service.lastPolicyCode);
        assertEquals("FILTER_LOOKUP", service.lastScopeCode);
        assertEquals(LocalDate.parse("2026-06-18"), service.lastAsOfDate);
    }

    @Test
    void findPolicyPresetReturns404ForUnknownId() throws Exception {
        MockGovernancePolicyPresetReadService service = new MockGovernancePolicyPresetReadService();
        MockMvc mockMvc = mockMvc(service);

        mockMvc.perform(get("/api/governance/policy-presets/unknown")
                        .queryParam("client_id", "client-a")
                        .queryParam("policy_scope_cd", "FILTER_LOOKUP"))
                .andExpect(status().isNotFound());
    }

    @Test
    void findPolicyPresetReturns422WhenClientIdBlank() throws Exception {
        MockGovernancePolicyPresetReadService service = new MockGovernancePolicyPresetReadService() {
            @Override
            public GovernancePolicyPresetDto findPolicyPreset(String clientId, String policyCode, String policyScopeCode, LocalDate asOfDate) {
                if (clientId == null || clientId.isBlank()) {
                    throw new PolicyViolationException("CLIENT_ID_REQUIRED", "client_id is required");
                }
                return super.findPolicyPreset(clientId, policyCode, policyScopeCode, asOfDate);
            }
        };
        service.records.add(presetDto("GOV-FL-001", "FILTER_LOOKUP"));
        MockMvc mockMvc = mockMvc(service);

        mockMvc.perform(get("/api/governance/policy-presets/GOV-FL-001")
                        .queryParam("client_id", "")
                        .queryParam("policy_scope_cd", "FILTER_LOOKUP"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("CLIENT_ID_REQUIRED"));
    }

    @Test
    void findPolicyPresetsReturns400WhenClientIdMissing() throws Exception {
        MockGovernancePolicyPresetReadService service = new MockGovernancePolicyPresetReadService();
        MockMvc mockMvc = mockMvc(service);

        mockMvc.perform(get("/api/governance/policy-presets")
                        .queryParam("policy_scope_cd", "FILTER_LOOKUP"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void findPolicyPresetsReturns422WhenClientIdBlank() throws Exception {
        MockGovernancePolicyPresetReadService service = new MockGovernancePolicyPresetReadService() {
            @Override
            public List<GovernancePolicyPresetDto> findPolicyPresets(String clientId, String policyScopeCode, LocalDate asOfDate) {
                if (clientId == null || clientId.isBlank()) {
                    throw new PolicyViolationException("CLIENT_ID_REQUIRED", "client_id is required");
                }
                return super.findPolicyPresets(clientId, policyScopeCode, asOfDate);
            }
        };
        MockMvc mockMvc = mockMvc(service);

        mockMvc.perform(get("/api/governance/policy-presets")
                        .queryParam("client_id", "")
                        .queryParam("policy_scope_cd", "FILTER_LOOKUP"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("CLIENT_ID_REQUIRED"));
    }

    private static MockMvc mockMvc(GovernancePolicyPresetReadService service) {
        GovernancePolicyPresetController controller = new GovernancePolicyPresetController(service);
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        return MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ApiExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    private static GovernancePolicyPresetDto presetDto(String policyCode, String scope) {
        return new GovernancePolicyPresetDto(
                policyCode,
                "Policy Name",
                scope,
                "90",
                "INTEGER",
                true,
                true,
                LocalDate.parse("2026-01-01"),
                null,
                "admin",
                OffsetDateTime.parse("2026-01-01T00:00:00Z"),
                OffsetDateTime.parse("2026-01-01T00:00:00Z"),
                "admin"
        );
    }

    private static class MockGovernancePolicyPresetReadService implements GovernancePolicyPresetReadService {
        private final List<GovernancePolicyPresetDto> records = new ArrayList<>();
        private String lastClientId;
        private String lastPolicyCode;
        private String lastScopeCode;
        private LocalDate lastAsOfDate;

        @Override
        public List<GovernancePolicyPresetDto> findPolicyPresets(String clientId, String policyScopeCode, LocalDate asOfDate) {
            this.lastClientId = clientId;
            this.lastScopeCode = policyScopeCode;
            this.lastAsOfDate = asOfDate;
            return records.stream().filter(r -> policyScopeCode == null || r.policy_scope_cd().equals(policyScopeCode)).toList();
        }

        @Override
        public GovernancePolicyPresetDto findPolicyPreset(String clientId, String policyCode, String policyScopeCode, LocalDate asOfDate) {
            this.lastClientId = clientId;
            this.lastPolicyCode = policyCode;
            this.lastScopeCode = policyScopeCode;
            this.lastAsOfDate = asOfDate;
            return records.stream()
                    .filter(r -> r.policy_cd().equals(policyCode))
                    .findFirst()
                    .orElseThrow(() -> new GovernancePolicyPresetNotFoundException(policyCode));
        }
    }
}
