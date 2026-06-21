package com.lextr.semanticlayer.service;

import com.lextr.semanticlayer.dto.WorkflowApprovalRequestDto;
import com.lextr.semanticlayer.dto.WorkflowTaskResponseDto;

public interface WorkflowApprovalService {
    WorkflowTaskResponseDto approveTask(Long id, WorkflowApprovalRequestDto request);
    WorkflowTaskResponseDto rejectTask(Long id, java.util.Map<String, String> body);
}
