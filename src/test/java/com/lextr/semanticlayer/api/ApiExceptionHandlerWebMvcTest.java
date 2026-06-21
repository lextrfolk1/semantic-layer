package com.lextr.semanticlayer.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lextr.semanticlayer.dto.GovernancePolicyPresetDto;
import com.lextr.semanticlayer.dto.ObjectExposureDetailDto;
import com.lextr.semanticlayer.dto.ObjectExposureSummaryDto;
import com.lextr.semanticlayer.dto.ObjectRegistrationRequestDto;
import com.lextr.semanticlayer.dto.ObjectRegistrationResponseDto;
import com.lextr.semanticlayer.dto.WorkflowApprovalRequestDto;
import com.lextr.semanticlayer.dto.WorkflowTaskResponseDto;
import com.lextr.semanticlayer.exception.WorkflowTaskAlreadyApprovedException;
import com.lextr.semanticlayer.service.GovernancePolicyPresetReadService;
import com.lextr.semanticlayer.service.ObjectExposureReadService;
import com.lextr.semanticlayer.service.ObjectRegistrationService;
import com.lextr.semanticlayer.service.WorkflowApprovalService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ApiExceptionHandlerWebMvcTest {

    @Test
    void appliesValidationErrorContractToWorkflowRequests() throws Exception {
        RecordingWorkflowApprovalService workflowService = new RecordingWorkflowApprovalService();
        MockMvc mockMvc = mockMvc(workflowService, new NoOpObjectRegistrationService(),
                new RecordingObjectExposureReadService(), new RecordingGovernancePolicyPresetReadService());

        mockMvc.perform(post("/api/workflow-tasks/{id}/approve", 301L)
                        .contentType("application/json")
                        .content("""
                                {
                                  "client_id": "client-a",
                                  "approved_by": "123456789012345678901234567890123",
                                  "approval_note_txt": "too long name"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("approved_by: approved_by must be 32 characters or less"));

        assertNull(workflowService.lastRequest);
    }

    @Test
    void appliesMalformedBodyContractToWorkflowRequests() throws Exception {
        RecordingWorkflowApprovalService workflowService = new RecordingWorkflowApprovalService();
        MockMvc mockMvc = mockMvc(workflowService, new NoOpObjectRegistrationService(),
                new RecordingObjectExposureReadService(), new RecordingGovernancePolicyPresetReadService());

        mockMvc.perform(post("/api/workflow-tasks/{id}/approve", 301L)
                        .contentType("application/json")
                        .content("""
                                {
                                  "client_id":
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value("Request body is malformed"));

        assertNull(workflowService.lastRequest);
    }

    @Test
    void appliesTypeMismatchContractToObjectRoutes() throws Exception {
        RecordingObjectExposureReadService exposureReadService = new RecordingObjectExposureReadService();
        MockMvc mockMvc = mockMvc(new RecordingWorkflowApprovalService(), new NoOpObjectRegistrationService(),
                exposureReadService, new RecordingGovernancePolicyPresetReadService());

        mockMvc.perform(get("/api/objects/{object_id}", "not-a-uuid")
                        .queryParam("client_id", "client-a"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value("object_id: 'not-a-uuid' is not a valid UUID"));

        assertNull(exposureReadService.lastObjectId);
    }

    @Test
    void appliesMissingParameterContractToGovernanceRoutes() throws Exception {
        RecordingGovernancePolicyPresetReadService governanceService = new RecordingGovernancePolicyPresetReadService();
        MockMvc mockMvc = mockMvc(new RecordingWorkflowApprovalService(), new NoOpObjectRegistrationService(),
                new RecordingObjectExposureReadService(), governanceService);

        mockMvc.perform(get("/api/governance/policy-presets")
                        .queryParam("policy_scope_cd", "FILTER_LOOKUP"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message")
                        .value("Required request parameter 'client_id' for method parameter type String is not present"));

        assertNull(governanceService.lastClientId);
    }

    @Test
    void preservesAnnotatedSemanticLayerStatusAcrossControllers() throws Exception {
        RecordingWorkflowApprovalService workflowService = new RecordingWorkflowApprovalService();
        workflowService.error = new WorkflowTaskAlreadyApprovedException(301L);
        MockMvc mockMvc = mockMvc(workflowService, new NoOpObjectRegistrationService(),
                new RecordingObjectExposureReadService(), new RecordingGovernancePolicyPresetReadService());

        mockMvc.perform(post("/api/workflow-tasks/{id}/approve", 301L)
                        .contentType("application/json")
                        .content(validWorkflowRequestJson()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("UNPROCESSABLE_ENTITY"))
                .andExpect(jsonPath("$.message")
                        .value("Workflow task 301 is already approved and cannot be re-approved"));

        assertEquals(301L, workflowService.lastId);
    }

    private static MockMvc mockMvc(WorkflowApprovalService workflowApprovalService,
                                   ObjectRegistrationService objectRegistrationService,
                                   ObjectExposureReadService objectExposureReadService,
                                   GovernancePolicyPresetReadService governancePolicyPresetReadService) {
        ObjectMapper objectMapper = new ObjectMapper()
                .findAndRegisterModules()
                .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        return MockMvcBuilders.standaloneSetup(
                        new WorkflowTaskController(workflowApprovalService, providerOf(null), null),
                        new ObjectRegistrationController(objectRegistrationService, objectExposureReadService),
                        new GovernancePolicyPresetController(governancePolicyPresetReadService)
                )
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

    private static String validWorkflowRequestJson() {
        return """
                {
                  "client_id": "client-a",
                  "approved_by": "approver",
                  "approval_note_txt": "looks good"
                }
                """;
    }

    private static final class RecordingWorkflowApprovalService implements WorkflowApprovalService {
        private Long lastId;
        private WorkflowApprovalRequestDto lastRequest;
        private RuntimeException error;

        @Override
        public WorkflowTaskResponseDto approveTask(Long id, WorkflowApprovalRequestDto request) {
            lastId = id;
            lastRequest = request;
            if (error != null) {
                throw error;
            }
            return null;
        }

        @Override
        public WorkflowTaskResponseDto rejectTask(Long id, java.util.Map<String, String> body) {
            lastId = id;
            if (error != null) {
                throw error;
            }
            return null;
        }
    }

    private static final class NoOpObjectRegistrationService implements ObjectRegistrationService {

        @Override
        public ObjectRegistrationResponseDto registerObject(ObjectRegistrationRequestDto request) {
            throw new UnsupportedOperationException("Not used in exception handler tests");
        }
    }

    private static final class RecordingObjectExposureReadService implements ObjectExposureReadService {
        private UUID lastObjectId;

        @Override
        public List<ObjectExposureSummaryDto> findObjects(String clientId, String schemaCode, String lifecycleStatusCode) {
            throw new UnsupportedOperationException("Not used in exception handler tests");
        }

        @Override
        public ObjectExposureDetailDto findObject(String clientId, UUID objectId) {
            lastObjectId = objectId;
            throw new UnsupportedOperationException("Not used in exception handler tests");
        }
    }

    private static final class RecordingGovernancePolicyPresetReadService implements GovernancePolicyPresetReadService {
        private String lastClientId;

        @Override
        public List<GovernancePolicyPresetDto> findPolicyPresets(String clientId, String policyScopeCode, LocalDate asOfDate) {
            lastClientId = clientId;
            throw new UnsupportedOperationException("Not used in exception handler tests");
        }

        @Override
        public GovernancePolicyPresetDto findPolicyPreset(String clientId, String policyCode, String policyScopeCode, LocalDate asOfDate) {
            lastClientId = clientId;
            throw new UnsupportedOperationException("Not used in exception handler tests");
        }
    }
}
