package com.lextr.semanticlayer.api;

import com.lextr.semanticlayer.dto.WorkflowApprovalRequestDto;
import com.lextr.semanticlayer.dto.WorkflowTaskResponseDto;
import com.lextr.semanticlayer.service.WorkflowApprovalService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/workflow-tasks")
public class WorkflowTaskController {

    private final WorkflowApprovalService workflowApprovalService;

    public WorkflowTaskController(WorkflowApprovalService workflowApprovalService) {
        this.workflowApprovalService = workflowApprovalService;
    }

    @PostMapping("/{id}/approve")
    public WorkflowTaskResponseDto approveTask(
            @PathVariable("id") Long id,
            @Valid @RequestBody WorkflowApprovalRequestDto request) {
        return workflowApprovalService.approveTask(id, request);
    }
}
