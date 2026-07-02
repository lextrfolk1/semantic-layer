package com.lextr.semanticlayer.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lextr.semanticlayer.dto.ExternalRuleResultIngestRequestDto;
import com.lextr.semanticlayer.dto.RuleResultIngestResponseDto;
import com.lextr.semanticlayer.exception.RuleResultServiceException;
import com.lextr.semanticlayer.service.RuleResultService;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RuleResultControllerTest {

    @Test
    void routesIngestEndpointToService() throws Exception {
        RecordingRuleResultService service = new RecordingRuleResultService();
        service.response = new RuleResultIngestResponseDto(
                901L,
                801L,
                "client-a",
                101L,
                "EDT-001",
                "EDITCHECK",
                "DQ",
                new ObjectMapper().readTree("{\"severity\":\"HIGH\"}"),
                OffsetDateTime.parse("2026-06-18T10:15:30Z"),
                OffsetDateTime.parse("2026-06-18T10:15:31Z"),
                "loader",
                OffsetDateTime.parse("2026-06-18T10:15:32Z"),
                "loader"
        );
        MockMvc mockMvc = mockMvc(service);

        mockMvc.perform(post("/api/rule-results")
                        .header("X-Principal-Cd", "engine-user")
                        .contentType("application/json")
                        .content("""
                                {
                                  "client_id": "client-a",
                                  "outbound_id": 101,
                                  "rule_ref_cd": "EDT-001",
                                  "output_kind_cd": "EDITCHECK",
                                  "output_payload_jsonb": {"severity":"HIGH"},
                                  "observed_ts": "2026-06-18T10:15:30Z"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.external_rule_result_id").value(901))
                .andExpect(jsonPath("$.dq_result_id").value(801))
                .andExpect(jsonPath("$.client_id").value("client-a"))
                .andExpect(jsonPath("$.route_target_cd").value("DQ"))
                .andExpect(jsonPath("$.output_payload_jsonb.severity").value("HIGH"));

        assertEquals("client-a", service.lastRequest.client_id());
        assertEquals(101L, service.lastRequest.outbound_id());
        assertEquals("EDT-001", service.lastRequest.rule_ref_cd());
        assertEquals("EDITCHECK", service.lastRequest.output_kind_cd());
        assertEquals("engine-user", service.lastPrincipalCd);
    }

    @Test
    void rejectsRequestsMissingRequiredFields() throws Exception {
        MockMvc mockMvc = mockMvc(new RecordingRuleResultService());

        mockMvc.perform(post("/api/rule-results")
                        .contentType("application/json")
                        .content("""
                                {
                                  "client_id": "client-a",
                                  "rule_ref_cd": "EDT-001",
                                  "output_kind_cd": "EDITCHECK",
                                  "output_payload_jsonb": {}
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void mapsServiceErrorsToInternalServerError() throws Exception {
        RecordingRuleResultService service = new RecordingRuleResultService();
        service.error = new RuleResultServiceException("rule result ingest failed");
        MockMvc mockMvc = mockMvc(service);

        mockMvc.perform(post("/api/rule-results")
                        .contentType("application/json")
                        .content("""
                                {
                                  "client_id": "client-a",
                                  "outbound_id": 101,
                                  "rule_ref_cd": "EDT-001",
                                  "output_kind_cd": "EDITCHECK",
                                  "output_payload_jsonb": {},
                                  "observed_ts": "2026-06-18T10:15:30Z"
                                }
                                """))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"))
                .andExpect(jsonPath("$.message").value("rule result ingest failed"));
    }

    private static MockMvc mockMvc(RuleResultService service) {
        RuleResultController controller = new RuleResultController(service);
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        return MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ApiExceptionHandler())
                .setValidator(validator)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    private static final class RecordingRuleResultService implements RuleResultService {

        private ExternalRuleResultIngestRequestDto lastRequest;
        private String lastPrincipalCd;
        private RuleResultIngestResponseDto response;
        private RuntimeException error;

        @Override
        public RuleResultIngestResponseDto ingestRuleResult(ExternalRuleResultIngestRequestDto request, String principalCd) {
            lastRequest = request;
            lastPrincipalCd = principalCd;
            if (error != null) {
                throw error;
            }
            return response;
        }
    }
}
