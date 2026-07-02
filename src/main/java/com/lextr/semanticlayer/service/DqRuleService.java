package com.lextr.semanticlayer.service;

import com.lextr.semanticlayer.dto.DqRuleAttributeDto;
import com.lextr.semanticlayer.dto.DqRuleCatalogDto;
import com.lextr.semanticlayer.dto.DqRuleRequestDto;
import com.lextr.semanticlayer.dto.DqRuleResultDto;
import com.lextr.semanticlayer.dto.DqRuleResultIngestRequestDto;
import com.lextr.semanticlayer.dto.WorkflowTaskResponseDto;

import java.util.List;
import java.util.UUID;

public interface DqRuleService {

    List<DqRuleCatalogDto> findRules(String clientId, String ruleDimensionCode, String lifecycleStatusCode);

    DqRuleCatalogDto findRule(String clientId, String ruleCode);

    List<DqRuleAttributeDto> findRuleAttributes(String clientId, String ruleCode);

    List<DqRuleResultDto> findRuleResults(String clientId, String logicalAttributeCode);

    DqRuleResultDto ingestResult(DqRuleResultIngestRequestDto request, String principalCd);

    List<WorkflowTaskResponseDto> requestRules(DqRuleRequestDto request);

    WorkflowTaskResponseDto findRequest(String clientId, UUID workflowTaskId);
}
