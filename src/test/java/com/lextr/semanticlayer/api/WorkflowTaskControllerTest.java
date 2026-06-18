package com.lextr.semanticlayer.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lextr.semanticlayer.dto.WorkflowApprovalRequestDto;
import com.lextr.semanticlayer.dto.WorkflowTaskResponseDto;
import com.lextr.semanticlayer.exception.RegistryResourceNotFoundException;
import com.lextr.semanticlayer.exception.WorkflowApprovalServiceException;
import com.lextr.semanticlayer.exception.WorkflowTaskAlreadyApprovedException;
import com.lextr.semanticlayer.service.WorkflowApprovalService;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.time.LocalDate;
import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class WorkflowTaskControllerTest {

    @Test
    void routesWorkflowApprovalEndpointToService() throws Exception {
        RecordingWorkflowApprovalService service = new RecordingWorkflowApprovalService();
        service.response = new WorkflowTaskResponseDto(
                301L,
                "FILTER_LOOKUP_REGISTRATION",
                "FILTER_LOOKUP",
                "LEDGER_SCOPE",
                "APPROVED",
                "producer",
                OffsetDateTime.parse("2026-06-18T10:15:30Z"),
                null,
                LocalDate.parse("2026-09-16"),
                "Review filter lookup LEDGER_SCOPE",
                "client-a",
                "approver",
                OffsetDateTime.parse("2026-06-18T10:20:30Z"),
                "looks good"
        );
        MockMvc mockMvc = mockMvc(service);

        mockMvc.perform(post("/api/workflow-tasks/{id}/approve", 301L)
                        .contentType("application/json")
                        .content(validRequestJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(301))
                .andExpect(jsonPath("$.task_type_cd").value("FILTER_LOOKUP_REGISTRATION"))
                .andExpect(jsonPath("$.task_status_cd").value("APPROVED"))
                .andExpect(jsonPath("$.client_id").value("client-a"))
                .andExpect(jsonPath("$.approved_by").value("approver"))
                .andExpect(jsonPath("$.approved_ts").value("2026-06-18T10:20:30Z"))
                .andExpect(jsonPath("$.approval_note_txt").value("looks good"));

        assertEquals(301L, service.lastId);
        assertEquals("client-a", service.lastRequest.client_id());
        assertEquals("approver", service.lastRequest.approved_by());
        assertEquals("looks good", service.lastRequest.approval_note_txt());
    }

    @Test
    void rejectsApproverNameLongerThanThirtyTwoCharacters() throws Exception {
        MockMvc mockMvc = mockMvc(new RecordingWorkflowApprovalService());

        mockMvc.perform(post("/api/workflow-tasks/{id}/approve", 301L)
                        .contentType("application/json")
                        .content("""
                                {
                                  "client_id": "client-a",
                                  "approved_by": "123456789012345678901234567890123",
                                  "approval_note_txt": "too long name"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsMissingClientId() throws Exception {
        MockMvc mockMvc = mockMvc(new RecordingWorkflowApprovalService());

        mockMvc.perform(post("/api/workflow-tasks/{id}/approve", 301L)
                        .contentType("application/json")
                        .content("""
                                {
                                  "approved_by": "approver",
                                  "approval_note_txt": "missing client id"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void mapsWorkflowServiceErrorsToInternalServerError() throws Exception {
        RecordingWorkflowApprovalService service = new RecordingWorkflowApprovalService();
        service.error = new WorkflowApprovalServiceException("approval failed");
        MockMvc mockMvc = mockMvc(service);

        mockMvc.perform(post("/api/workflow-tasks/{id}/approve", 301L)
                        .contentType("application/json")
                        .content(validRequestJson()))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void mapsAlreadyApprovedTaskToUnprocessableEntity() throws Exception {
        RecordingWorkflowApprovalService service = new RecordingWorkflowApprovalService();
        service.error = new WorkflowTaskAlreadyApprovedException(301L);
        MockMvc mockMvc = mockMvc(service);

        mockMvc.perform(post("/api/workflow-tasks/{id}/approve", 301L)
                        .contentType("application/json")
                        .content(validRequestJson()))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void mapsMissingWorkflowTaskToNotFound() throws Exception {
        RecordingWorkflowApprovalService service = new RecordingWorkflowApprovalService();
        service.error = new RegistryResourceNotFoundException("workflow task", "301");
        MockMvc mockMvc = mockMvc(service);

        mockMvc.perform(post("/api/workflow-tasks/{id}/approve", 301L)
                        .contentType("application/json")
                        .content(validRequestJson()))
                .andExpect(status().isNotFound());
    }

    private static MockMvc mockMvc(WorkflowApprovalService service) {
        WorkflowTaskController controller = new WorkflowTaskController(service);
        ObjectMapper objectMapper = new ObjectMapper()
                .findAndRegisterModules()
                .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
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
                  "approved_by": "approver",
                  "approval_note_txt": "looks good"
                }
                """;
    }

    private static final class RecordingWorkflowApprovalService implements WorkflowApprovalService {
        private Long lastId;
        private WorkflowApprovalRequestDto lastRequest;
        private WorkflowTaskResponseDto response;
        private RuntimeException error;

        @Override
        public WorkflowTaskResponseDto approveTask(Long id, WorkflowApprovalRequestDto request) {
            this.lastId = id;
            this.lastRequest = request;
            if (error != null) {
                throw error;
            }
            return response;
        }
    }
}
