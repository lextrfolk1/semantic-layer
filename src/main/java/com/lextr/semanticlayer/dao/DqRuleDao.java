package com.lextr.semanticlayer.dao;

import com.lextr.semanticlayer.model.DqRuleAttributeRecord;
import com.lextr.semanticlayer.model.DqRuleCatalogRecord;
import com.lextr.semanticlayer.model.DqRuleRequestWorkflowTaskRecord;
import com.lextr.semanticlayer.model.DqRuleRequestWorkflowTaskWriteRequest;
import com.lextr.semanticlayer.model.DqRuleResultRecord;
import com.lextr.semanticlayer.model.DqRuleResultWriteRequest;
import com.lextr.semanticlayer.model.MetadataChangeHistoryRecord;
import com.lextr.semanticlayer.model.MetadataChangeHistoryWriteRequest;

import java.util.List;
import java.util.Optional;

public interface DqRuleDao {

    List<DqRuleCatalogRecord> findRules(String clientId, String ruleDimensionCode, String lifecycleStatusCode);

    Optional<DqRuleCatalogRecord> findRule(String clientId, String ruleCode);

    List<DqRuleAttributeRecord> findRuleAttributes(String clientId, String ruleCode);

    List<DqRuleResultRecord> findResultsByLogicalAttribute(String clientId, String logicalAttributeCode);

    DqRuleRequestWorkflowTaskRecord insertWorkflowTask(DqRuleRequestWorkflowTaskWriteRequest request);

    Optional<DqRuleRequestWorkflowTaskRecord> findRequest(String clientId, java.util.UUID workflowTaskId);

    DqRuleResultRecord insertResult(DqRuleResultWriteRequest request);

    MetadataChangeHistoryRecord insertMetadataChangeHistory(MetadataChangeHistoryWriteRequest request);
}
