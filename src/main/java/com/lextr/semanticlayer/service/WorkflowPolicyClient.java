package com.lextr.semanticlayer.service;

import com.lextr.semanticlayer.dto.WorkflowPolicyDecisionDto;
import com.lextr.semanticlayer.dto.WorkflowPolicyRequestDto;

public interface WorkflowPolicyClient {

    WorkflowPolicyDecisionDto validateApproval(WorkflowPolicyRequestDto request);
}
