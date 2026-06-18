package com.lextr.semanticlayer.service.impl;

import com.lextr.semanticlayer.dto.WorkflowApprovalRequestDto;
import com.lextr.semanticlayer.dto.WorkflowTaskResponseDto;
import com.lextr.semanticlayer.service.WorkflowApprovalService;
import org.springframework.stereotype.Service;

@Service
public class NoOpWorkflowApprovalServiceImpl implements WorkflowApprovalService {

    @Override
    public WorkflowTaskResponseDto approveTask(Long id, WorkflowApprovalRequestDto request) {
        return null;
    }
}
