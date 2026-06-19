package com.lextr.semanticlayer.logging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lextr.semanticlayer.api.ApiExceptionHandler;
import com.lextr.semanticlayer.api.WorkflowTaskController;
import com.lextr.semanticlayer.dto.WorkflowApprovalRequestDto;
import com.lextr.semanticlayer.dto.WorkflowTaskResponseDto;
import com.lextr.semanticlayer.exception.WorkflowTaskAlreadyApprovedException;
import com.lextr.semanticlayer.service.WorkflowApprovalService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.util.StringUtils;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(OutputCaptureExtension.class)
class ControllerExecutionTimeAspectTest {

    @Test
    void logsSharedControllerTimingPathForSuccessfulRequests(CapturedOutput output) throws Exception {
        RecordingWorkflowApprovalService service = new RecordingWorkflowApprovalService();
        service.response = new WorkflowTaskResponseDto(
                301L,
                "FILTER_LOOKUP_REGISTRATION",
                "FILTER_LOOKUP",
                "LEDGER_SCOPE",
                "APPROVED",
                "producer",
                null,
                null,
                null,
                "Review filter lookup LEDGER_SCOPE",
                "client-a",
                "approver",
                null,
                "looks good"
        );
        MockMvc mockMvc = mockMvc(service);

        mockMvc.perform(post("/api/workflow-tasks/{id}/approve", 301L)
                        .contentType("application/json")
                        .content(validRequestJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.task_status_cd").value("APPROVED"));

        assertEquals(301L, service.lastId);
        assertEquals("approver", service.lastRequest.approved_by());
        assertContains(output, "Handled request POST /api/workflow-tasks/301/approve via WorkflowTaskController.approveTask in ");
        assertFalse(output.getOut().contains("failed with status="));
    }

    @Test
    void logsHandledErrorsOnceWithoutDuplicateControllerFailureLogs(CapturedOutput output) throws Exception {
        RecordingWorkflowApprovalService service = new RecordingWorkflowApprovalService();
        service.error = new WorkflowTaskAlreadyApprovedException(301L);
        MockMvc mockMvc = mockMvc(service);

        mockMvc.perform(post("/api/workflow-tasks/{id}/approve", 301L)
                        .contentType("application/json")
                        .content(validRequestJson()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("UNPROCESSABLE_ENTITY"))
                .andExpect(jsonPath("$.message").value("Workflow task 301 is already approved and cannot be re-approved"));

        String failureLog = "Request POST /api/workflow-tasks/301/approve failed with status=422 code=UNPROCESSABLE_ENTITY exception=WorkflowTaskAlreadyApprovedException message=Workflow task 301 is already approved and cannot be re-approved";
        assertContains(output, failureLog);
        assertEquals(1, StringUtils.countOccurrencesOf(output.getOut(), failureLog));
        assertFalse(output.getOut().contains(
                "Handled request POST /api/workflow-tasks/301/approve via WorkflowTaskController.approveTask in "
        ));
    }

    private static MockMvc mockMvc(WorkflowApprovalService service) {
        WorkflowTaskController controller = new WorkflowTaskController(service);
        AspectJProxyFactory proxyFactory = new AspectJProxyFactory(controller);
        proxyFactory.addAspect(new ControllerExecutionTimeAspect());

        ObjectMapper objectMapper = new ObjectMapper()
                .findAndRegisterModules()
                .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        return MockMvcBuilders.standaloneSetup(new Object[]{proxyFactory.getProxy()})
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

    private static void assertContains(CapturedOutput output, String expected) {
        String combined = output.getOut() + output.getErr();
        org.junit.jupiter.api.Assertions.assertTrue(
                combined.contains(expected),
                () -> "Expected log output to contain: " + expected + "\nActual output:\n" + combined
        );
    }

    private static final class RecordingWorkflowApprovalService implements WorkflowApprovalService {
        private Long lastId;
        private WorkflowApprovalRequestDto lastRequest;
        private WorkflowTaskResponseDto response;
        private RuntimeException error;

        @Override
        public WorkflowTaskResponseDto approveTask(Long id, WorkflowApprovalRequestDto request) {
            lastId = id;
            lastRequest = request;
            if (error != null) {
                throw error;
            }
            return response;
        }
    }
}
