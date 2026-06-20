package com.lextr.semanticlayer.api;

import com.lextr.semanticlayer.dto.WorkflowApprovalRequestDto;
import com.lextr.semanticlayer.dto.WorkflowTaskResponseDto;
import com.lextr.semanticlayer.service.WorkflowApprovalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/workflow-tasks")
@Tag(name = "Workflow", description = "Workflow approval operations.")
public class WorkflowTaskController {

    private final WorkflowApprovalService workflowApprovalService;

    public WorkflowTaskController(WorkflowApprovalService workflowApprovalService) {
        this.workflowApprovalService = workflowApprovalService;
    }

    @PostMapping("/{id}/approve")
    @Operation(summary = "Approve workflow task", description = "Approves one workflow task and applies its side effects.")
    public WorkflowTaskResponseDto approveTask(
            @Parameter(description = "Workflow task identifier.") @PathVariable("id") Long id,
            @Valid @RequestBody WorkflowApprovalRequestDto request) {
        return workflowApprovalService.approveTask(id, request);
    }
}
