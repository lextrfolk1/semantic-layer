package com.lextr.semanticlayer.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lextr.semanticlayer.dto.DqRuleAttributeDto;
import com.lextr.semanticlayer.dto.DqRuleCatalogDto;
import com.lextr.semanticlayer.dto.DqRuleRequestDto;
import com.lextr.semanticlayer.dto.DqRuleResultDto;
import com.lextr.semanticlayer.dto.DqRuleResultIngestRequestDto;
import com.lextr.semanticlayer.dto.WorkflowTaskResponseDto;
import com.lextr.semanticlayer.exception.DqRuleServiceException;
import com.lextr.semanticlayer.service.DqRuleService;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DqRuleControllerTest {

    @Test
    void routesCatalogEndpointToService() throws Exception {
        RecordingDqRuleService service = new RecordingDqRuleService();
        service.rulesResponse = List.of(new DqRuleCatalogDto(
                "LEDGER_COMPLETENESS",
                "Ledger Completeness",
                "COMPLETENESS",
                "ledger_id",
                "RULESET",
                "ledger_id is present",
                "HIGH",
                "ACTIVE",
                "client-a",
                OffsetDateTime.parse("2026-06-01T10:15:30Z"),
                "producer",
                OffsetDateTime.parse("2026-06-02T10:15:30Z"),
                "reviewer"
        ));
        MockMvc mockMvc = mockMvc(service);

        mockMvc.perform(get("/api/dq-rules")
                        .param("client_id", "client-a")
                        .param("rule_dimension_cd", "COMPLETENESS")
                        .param("lifecycle_status_cd", "ACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].rule_cd").value("LEDGER_COMPLETENESS"))
                .andExpect(jsonPath("$[0].client_id").value("client-a"));

        assertEquals("client-a", service.lastClientId);
        assertEquals("COMPLETENESS", service.lastRuleDimensionCode);
        assertEquals("ACTIVE", service.lastLifecycleStatusCode);
    }

    @Test
    void routesRuleDetailEndpointToService() throws Exception {
        RecordingDqRuleService service = new RecordingDqRuleService();
        service.rulesResponse = List.of(new DqRuleCatalogDto(
                "LEDGER_COMPLETENESS",
                "Ledger Completeness",
                "COMPLETENESS",
                "ledger_id",
                "RULESET",
                "ledger_id is present",
                "HIGH",
                "ACTIVE",
                "client-a",
                OffsetDateTime.parse("2026-06-01T10:15:30Z"),
                "producer",
                OffsetDateTime.parse("2026-06-02T10:15:30Z"),
                "reviewer"
        ));
        MockMvc mockMvc = mockMvc(service);

        mockMvc.perform(get("/api/dq-rules/{rule_cd}", "LEDGER_COMPLETENESS")
                        .param("client_id", "client-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rule_cd").value("LEDGER_COMPLETENESS"))
                .andExpect(jsonPath("$.client_id").value("client-a"));

        assertEquals("client-a", service.lastClientId);
        assertEquals("LEDGER_COMPLETENESS", service.lastRuleCode);
    }

    @Test
    void routesRequestEndpointToService() throws Exception {
        RecordingDqRuleService service = new RecordingDqRuleService();
        service.requestResponse = List.of(new WorkflowTaskResponseDto(
                301L,
                "DQ_RULE_REQUEST",
                "DQ_RULE",
                "LEDGER_COMPLETENESS",
                "PENDING",
                "steward",
                OffsetDateTime.parse("2026-06-10T10:15:30Z"),
                "steward",
                LocalDate.parse("2026-06-17"),
                "Request for ledger completeness",
                "client-a",
                null,
                null,
                null
        ));
        MockMvc mockMvc = mockMvc(service);

        mockMvc.perform(post("/api/dq-rules/requests")
                        .contentType("application/json")
                        .content(validRequestJson()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$[0].task_type_cd").value("DQ_RULE_REQUEST"))
                .andExpect(jsonPath("$[0].entity_ref").value("LEDGER_COMPLETENESS"));

        assertEquals("client-a", service.lastRequest.client_id());
        assertEquals(2, service.lastRequest.rule_names().size());
        assertEquals("steward", service.lastRequest.requested_by());
    }

    @Test
    void routesObserveEndpointToService() throws Exception {
        RecordingDqRuleService service = new RecordingDqRuleService();
        service.requestObservationResponse = new WorkflowTaskResponseDto(
                302L,
                "DQ_RULE_REQUEST",
                "DQ_RULE",
                "LEDGER_COMPLETENESS",
                "APPROVED",
                "steward",
                OffsetDateTime.parse("2026-06-10T10:15:30Z"),
                "steward",
                LocalDate.parse("2026-06-17"),
                "Request for ledger completeness",
                "client-a",
                "approver",
                OffsetDateTime.parse("2026-06-11T10:15:30Z"),
                "Approved"
        );
        MockMvc mockMvc = mockMvc(service);

        mockMvc.perform(get("/api/dq-rules/requests/{workflow_task_id}", UUID.fromString("11111111-1111-1111-1111-111111111111"))
                        .param("client_id", "client-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(302))
                .andExpect(jsonPath("$.task_status_cd").value("APPROVED"));

        assertEquals("client-a", service.lastClientId);
        assertEquals(UUID.fromString("11111111-1111-1111-1111-111111111111"), service.lastWorkflowTaskId);
    }

    @Test
    void rejectsRequestsWithMoreThanThirtyTwoRuleNames() throws Exception {
        MockMvc mockMvc = mockMvc(new RecordingDqRuleService());

        mockMvc.perform(post("/api/dq-rules/requests")
                        .contentType("application/json")
                        .content(oversizedRequestJson()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void mapsServiceErrorsToInternalServerError() throws Exception {
        RecordingDqRuleService service = new RecordingDqRuleService();
        service.error = new DqRuleServiceException("dq rule lookup failed");
        MockMvc mockMvc = mockMvc(service);

        mockMvc.perform(get("/api/dq-rules")
                        .param("client_id", "client-a"))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void routesRuleAttributesEndpointToService() throws Exception {
        RecordingDqRuleService service = new RecordingDqRuleService();
        service.attributesResponse = List.of(new DqRuleAttributeDto(
                401L,
                "LEDGER_COMPLETENESS",
                "ledger_id",
                "SOURCE",
                "client-a",
                OffsetDateTime.parse("2026-06-01T10:15:30Z"),
                "producer",
                OffsetDateTime.parse("2026-06-02T10:15:30Z"),
                "reviewer"
        ));
        MockMvc mockMvc = mockMvc(service);

        mockMvc.perform(get("/api/dq-rules/{rule_cd}/attributes", "LEDGER_COMPLETENESS")
                        .param("client_id", "client-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].attribute_cd").value("ledger_id"));

        assertEquals("client-a", service.lastClientId);
        assertEquals("LEDGER_COMPLETENESS", service.lastRuleCode);
    }

    @Test
    void routesResultsEndpointToService() throws Exception {
        RecordingDqRuleService service = new RecordingDqRuleService();
        service.resultsResponse = List.of(new DqRuleResultDto(
                501L,
                "LEDGER_COMPLETENESS",
                "ledger_id",
                "client-a",
                "123",
                "123",
                "PASS",
                null,
                OffsetDateTime.parse("2026-06-05T10:15:30Z"),
                OffsetDateTime.parse("2026-06-05T10:15:30Z"),
                "sensor",
                OffsetDateTime.parse("2026-06-05T10:15:31Z"),
                "sensor"
        ));
        MockMvc mockMvc = mockMvc(service);

        mockMvc.perform(get("/api/dq-rules/results")
                        .param("client_id", "client-a")
                        .param("logical_attribute_cd", "ledger_id"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].result_status_cd").value("PASS"));

        assertEquals("client-a", service.lastClientId);
        assertEquals("ledger_id", service.lastLogicalAttributeCode);
    }

    private static MockMvc mockMvc(RecordingDqRuleService service) {
        DqRuleController controller = new DqRuleController(service);
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
                  "rule_names": ["LEDGER_COMPLETENESS", "LEDGER_CONFORMITY"],
                  "requested_by": "steward",
                  "request_txt": "Please request these DQ rules for approval"
                }
                """;
    }

    private static String oversizedRequestJson() {
        StringBuilder builder = new StringBuilder();
        builder.append("{\n");
        builder.append("  \"client_id\": \"client-a\",\n");
        builder.append("  \"rule_names\": [");
        for (int i = 1; i <= 33; i++) {
            if (i > 1) {
                builder.append(", ");
            }
            builder.append("\"RULE_").append(i).append("\"");
        }
        builder.append("],\n");
        builder.append("  \"requested_by\": \"steward\",\n");
        builder.append("  \"request_txt\": \"Please request these DQ rules for approval\"\n");
        builder.append("}");
        return builder.toString();
    }

    private static final class RecordingDqRuleService implements DqRuleService {

        private String lastClientId;
        private String lastRuleDimensionCode;
        private String lastLifecycleStatusCode;
        private String lastRuleCode;
        private String lastLogicalAttributeCode;
        private UUID lastWorkflowTaskId;
        private DqRuleRequestDto lastRequest;
        private List<DqRuleCatalogDto> rulesResponse;
        private List<DqRuleAttributeDto> attributesResponse;
        private List<DqRuleResultDto> resultsResponse;
        private List<WorkflowTaskResponseDto> requestResponse;
        private WorkflowTaskResponseDto requestObservationResponse;
        private RuntimeException error;

        @Override
        public List<DqRuleCatalogDto> findRules(String clientId, String ruleDimensionCode, String lifecycleStatusCode) {
            lastClientId = clientId;
            lastRuleDimensionCode = ruleDimensionCode;
            lastLifecycleStatusCode = lifecycleStatusCode;
            if (error != null) {
                throw error;
            }
            return rulesResponse;
        }

        @Override
        public DqRuleCatalogDto findRule(String clientId, String ruleCode) {
            lastClientId = clientId;
            lastRuleCode = ruleCode;
            if (error != null) {
                throw error;
            }
            return rulesResponse == null || rulesResponse.isEmpty() ? null : rulesResponse.get(0);
        }

        @Override
        public List<DqRuleAttributeDto> findRuleAttributes(String clientId, String ruleCode) {
            lastClientId = clientId;
            lastRuleCode = ruleCode;
            if (error != null) {
                throw error;
            }
            return attributesResponse;
        }

        @Override
        public List<DqRuleResultDto> findRuleResults(String clientId, String logicalAttributeCode) {
            lastClientId = clientId;
            lastLogicalAttributeCode = logicalAttributeCode;
            if (error != null) {
                throw error;
            }
            return resultsResponse;
        }

        @Override
        public DqRuleResultDto ingestResult(DqRuleResultIngestRequestDto request, String principalCd) {
            if (error != null) {
                throw error;
            }
            throw new UnsupportedOperationException("Not used in tests");
        }

        @Override
        public List<WorkflowTaskResponseDto> requestRules(DqRuleRequestDto request) {
            lastRequest = request;
            if (error != null) {
                throw error;
            }
            return requestResponse;
        }

        @Override
        public WorkflowTaskResponseDto findRequest(String clientId, UUID workflowTaskId) {
            lastClientId = clientId;
            lastWorkflowTaskId = workflowTaskId;
            if (error != null) {
                throw error;
            }
            return requestObservationResponse;
        }
    }
}
