package com.lextr.semanticlayer.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lextr.semanticlayer.service.opa.OpaPolicyReloadService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OpaPolicyControllerTest {

    @Test
    void reloadPoliciesReturnsListAndSuccessStatus() throws Exception {
        MockOpaPolicyReloadService service = new MockOpaPolicyReloadService();
        service.reloaded.addAll(List.of(
                "taxonomy/jurisdiction_valid.rego",
                "relationship/cross_engine_block.rego"
        ));
        MockMvc mockMvc = mockMvc(service);

        mockMvc.perform(post("/api/governance/policies/reload")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.reloaded_policies[0]").value("taxonomy/jurisdiction_valid.rego"))
                .andExpect(jsonPath("$.reloaded_policies[1]").value("relationship/cross_engine_block.rego"));

        assertEquals(1, service.reloadCallCount);
    }

    private static MockMvc mockMvc(OpaPolicyReloadService service) {
        OpaPolicyController controller = new OpaPolicyController(service);
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        return MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ApiExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    private static class MockOpaPolicyReloadService extends OpaPolicyReloadService {
        private final List<String> reloaded = new ArrayList<>();
        private int reloadCallCount = 0;

        public MockOpaPolicyReloadService() {
            super(null, null);
        }

        @Override
        public List<String> reloadPolicies() {
            reloadCallCount++;
            return reloaded;
        }
    }
}
