package com.lextr.semanticlayer.service.impl;

import com.lextr.semanticlayer.dao.DqRuleDao;
import com.lextr.semanticlayer.dto.DqRuleAttributeDto;
import com.lextr.semanticlayer.dto.DqRuleCatalogDto;
import com.lextr.semanticlayer.dto.DqRuleMatrixCoverageDto;
import com.lextr.semanticlayer.dto.DqRulePolicyDecisionDto;
import com.lextr.semanticlayer.dto.DqRuleRequestDto;
import com.lextr.semanticlayer.dto.DqRuleResultDto;
import com.lextr.semanticlayer.dto.DqRuleResultIngestRequestDto;
import com.lextr.semanticlayer.dto.WorkflowTaskResponseDto;
import com.lextr.semanticlayer.exception.PolicyViolationException;
import com.lextr.semanticlayer.exception.RegistryResourceNotFoundException;
import com.lextr.semanticlayer.exception.SemanticLayerException;
import com.lextr.semanticlayer.model.DqRuleAttributeRecord;
import com.lextr.semanticlayer.model.DqRuleCatalogRecord;
import com.lextr.semanticlayer.model.DqRuleRequestWorkflowTaskRecord;
import com.lextr.semanticlayer.model.DqRuleRequestWorkflowTaskWriteRequest;
import com.lextr.semanticlayer.model.DqRuleResultRecord;
import com.lextr.semanticlayer.model.DqRuleResultWriteRequest;
import com.lextr.semanticlayer.model.MetadataChangeHistoryRecord;
import com.lextr.semanticlayer.model.MetadataChangeHistoryWriteRequest;
import com.lextr.semanticlayer.service.DqRulePolicyClient;
import com.lextr.semanticlayer.service.DqRuleService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class DqRuleServiceImpl implements DqRuleService {

    private static final String WORKFLOW_TYPE_CD = "DQ_RULE_REQUEST";
    private static final String ENTITY_TYPE_CD = "DQ_RULE_REQUEST";
    private static final String REQUEST_STATUS_CD = "REQUESTED";
    private static final String REQUEST_CHANGE_TYPE_CD = "REQUESTED";
    private static final String RESULT_CHANGE_TYPE_CD = "RESULT_INGESTED";
    private static final String COVERAGE_CHANGE_TYPE_CD = "COVERAGE_COMPUTED";
    private static final String DEFAULT_ASSIGNED_TO = "semantic-layer";

    private final DqRuleDao dqRuleDao;
    private final DqRulePolicyClient dqRulePolicyClient;
    private final TransactionOperations transactionOperations;

    @Autowired
    public DqRuleServiceImpl(ObjectProvider<DqRuleDao> dqRuleDaoProvider,
                             ObjectProvider<DqRulePolicyClient> dqRulePolicyClientProvider,
                             @Qualifier("semanticLayerTransactionOperations")
                             ObjectProvider<TransactionOperations> transactionOperationsProvider) {
        this(
                dqRuleDaoProvider.getIfAvailable(MissingDqRuleDao::new),
                dqRulePolicyClientProvider.getIfAvailable(DefaultDqRulePolicyClient::new),
                transactionOperationsProvider.getIfAvailable(NoOpTransactionOperations::new)
        );
    }

    DqRuleServiceImpl(DqRuleDao dqRuleDao,
                      DqRulePolicyClient dqRulePolicyClient,
                      TransactionOperations transactionOperations) {
        this.dqRuleDao = dqRuleDao;
        this.dqRulePolicyClient = dqRulePolicyClient;
        this.transactionOperations = transactionOperations;
    }

    @Override
    public List<DqRuleCatalogDto> findRules(String clientId, String ruleDimensionCode, String lifecycleStatusCode) {
        return dqRuleDao.findRules(clientId, ruleDimensionCode, lifecycleStatusCode).stream()
                .map(this::toCatalogDto)
                .toList();
    }

    @Override
    public DqRuleCatalogDto findRule(String clientId, String ruleCode) {
        return dqRuleDao.findRule(clientId, ruleCode)
                .map(this::toCatalogDto)
                .orElseThrow(() -> new RegistryResourceNotFoundException("dq rule", ruleCode));
    }

    @Override
    public List<DqRuleAttributeDto> findRuleAttributes(String clientId, String ruleCode) {
        return dqRuleDao.findRuleAttributes(clientId, ruleCode).stream()
                .map(this::toAttributeDto)
                .toList();
    }

    @Override
    public List<DqRuleResultDto> findRuleResults(String clientId, String logicalAttributeCode) {
        return dqRuleDao.findResultsByLogicalAttribute(clientId, logicalAttributeCode).stream()
                .map(this::toResultDto)
                .toList();
    }

    @Override
    public WorkflowTaskResponseDto findRequest(String clientId, UUID workflowTaskId) {
        return dqRuleDao.findRequest(clientId, workflowTaskId)
                .map(this::toWorkflowTaskDto)
                .orElseThrow(() -> new RegistryResourceNotFoundException("dq rule request", workflowTaskId.toString()));
    }

    @Override
    public List<WorkflowTaskResponseDto> requestRules(DqRuleRequestDto request) {
        DqRulePolicyDecisionDto decision = dqRulePolicyClient.validateRequest(request);
        if (!decision.allowed()) {
            throw new PolicyViolationException(decision.code(), decision.message());
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        List<String> requestedRules = new LinkedHashSet<>(request.rule_names()).stream().toList();

        try {
            return transactionOperations.execute(status -> requestedRules.stream()
                    .map(ruleCode -> persistRequest(request, now, ruleCode))
                    .toList());
        } catch (PolicyViolationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new com.lextr.semanticlayer.exception.DqRuleServiceException("Unable to request DQ rule", exception);
        }
    }

    public DqRuleResultDto ingestResult(DqRuleResultIngestRequestDto request, String principalCd) {
        validateEnginePrincipal(request, principalCd);
        DqRuleCatalogDto rule = findRule(request.client_id(), request.rule_cd());

        OffsetDateTime now = effectiveNow(request.observed_ts());
        try {
            return transactionOperations.execute(status -> {
                DqRuleResultRecord result = dqRuleDao.insertResult(new DqRuleResultWriteRequest(
                        request.rule_cd(),
                        request.logical_attribute_cd(),
                        request.client_id(),
                        request.observed_value_txt(),
                        request.expected_value_txt(),
                        request.result_status_cd(),
                        request.result_reason_txt(),
                        now,
                        now,
                        request.ingested_by(),
                        now,
                        request.ingested_by()
                ));

                DqRuleMatrixCoverageDto coverage = computeMatrixCoverage(request.client_id(), request.rule_cd());
                dqRuleDao.insertMetadataChangeHistory(new MetadataChangeHistoryWriteRequest(
                        syntheticChangeId(request.client_id(), request.rule_cd(), RESULT_CHANGE_TYPE_CD),
                        request.client_id(),
                        ENTITY_TYPE_CD,
                        syntheticChangeId(request.client_id(), request.rule_cd(), RESULT_CHANGE_TYPE_CD),
                        RESULT_CHANGE_TYPE_CD,
                        "Ingested DQ result for " + request.rule_cd() + "; coverage=" + coverage.coverage_pct() + "%",
                        now,
                        request.ingested_by()
                ));
                return toResultDto(result);
            });
        } catch (PolicyViolationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new com.lextr.semanticlayer.exception.DqRuleServiceException("Unable to ingest DQ result", exception);
        }
    }

    public DqRuleMatrixCoverageDto computeMatrixCoverage(String clientId, String ruleCode) {
        DqRuleCatalogDto rule = findRule(clientId, ruleCode);
        List<DqRuleAttributeRecord> attributes = dqRuleDao.findRuleAttributes(clientId, ruleCode);
        List<DqRuleResultRecord> results = dqRuleDao.findResultsByLogicalAttribute(clientId, rule.logical_attribute_cd());

        int attributeCount = attributes.size();
        int resultCount = results.size();
        int coveragePct = attributeCount == 0 ? 0 : Math.min(100, (int) Math.round(resultCount * 100.0 / attributeCount));
        return new DqRuleMatrixCoverageDto(ruleCode, rule.logical_attribute_cd(), attributeCount, resultCount, coveragePct, coveragePct >= 100);
    }

    private WorkflowTaskResponseDto persistRequest(DqRuleRequestDto request, OffsetDateTime now, String ruleCode) {
        DqRuleRequestWorkflowTaskRecord task = dqRuleDao.insertWorkflowTask(new DqRuleRequestWorkflowTaskWriteRequest(
                request.client_id(),
                WORKFLOW_TYPE_CD,
                ENTITY_TYPE_CD,
                ruleCode,
                REQUEST_STATUS_CD,
                request.requested_by(),
                now,
                DEFAULT_ASSIGNED_TO,
                now.toLocalDate().plusDays(7),
                request.request_txt() == null || request.request_txt().isBlank()
                        ? "Request DQ rule " + ruleCode
                        : request.request_txt()
        ));

        DqRuleMatrixCoverageDto coverage = computeMatrixCoverage(request.client_id(), ruleCode);
        dqRuleDao.insertMetadataChangeHistory(new MetadataChangeHistoryWriteRequest(
                syntheticChangeId(request.client_id(), ruleCode, REQUEST_CHANGE_TYPE_CD),
                request.client_id(),
                ENTITY_TYPE_CD,
                syntheticChangeId(request.client_id(), ruleCode, REQUEST_CHANGE_TYPE_CD),
                REQUEST_CHANGE_TYPE_CD,
                "Requested DQ rule " + ruleCode + "; coverage=" + coverage.coverage_pct() + "%",
                now,
                request.requested_by()
        ));
        return toWorkflowTaskDto(task);
    }

    private void validateEnginePrincipal(DqRuleResultIngestRequestDto request, String principalCd) {
        DqRulePolicyDecisionDto decision = dqRulePolicyClient.validateResultIngest(request, principalCd);
        if (!decision.allowed()) {
            throw new PolicyViolationException(decision.code(), decision.message());
        }
    }

    private static OffsetDateTime effectiveNow(OffsetDateTime observedTs) {
        return observedTs == null ? OffsetDateTime.now(ZoneOffset.UTC) : observedTs;
    }

    private static UUID syntheticChangeId(String clientId, String ruleCode, String suffix) {
        return UUID.nameUUIDFromBytes((clientId + ":" + ruleCode + ":" + suffix).getBytes(StandardCharsets.UTF_8));
    }

    private DqRuleCatalogDto toCatalogDto(DqRuleCatalogRecord record) {
        return new DqRuleCatalogDto(
                record.rule_cd(),
                record.rule_nm(),
                record.rule_dimension_cd(),
                record.logical_attribute_cd(),
                record.rule_scope_cd(),
                record.rule_expression_txt(),
                record.severity_cd(),
                record.lifecycle_status_cd(),
                record.client_id(),
                record.created_ts(),
                record.created_by(),
                record.updated_ts(),
                record.updated_by()
        );
    }

    private DqRuleAttributeDto toAttributeDto(DqRuleAttributeRecord record) {
        return new DqRuleAttributeDto(
                record.id(),
                record.rule_cd(),
                record.attribute_cd(),
                record.attribute_role_cd(),
                record.client_id(),
                record.created_ts(),
                record.created_by(),
                record.updated_ts(),
                record.updated_by()
        );
    }

    private DqRuleResultDto toResultDto(DqRuleResultRecord record) {
        return new DqRuleResultDto(
                record.id(),
                record.rule_cd(),
                record.logical_attribute_cd(),
                record.client_id(),
                record.observed_value_txt(),
                record.expected_value_txt(),
                record.result_status_cd(),
                record.result_reason_txt(),
                record.observed_ts(),
                record.created_ts(),
                record.created_by(),
                record.updated_ts(),
                record.updated_by()
        );
    }

    private WorkflowTaskResponseDto toWorkflowTaskDto(DqRuleRequestWorkflowTaskRecord record) {
        return new WorkflowTaskResponseDto(
                record.id(),
                record.task_type_cd(),
                record.entity_type_cd(),
                record.rule_cd(),
                record.task_status_cd(),
                record.submitted_by(),
                record.submitted_ts(),
                record.assigned_to(),
                record.due_dt(),
                record.description_txt(),
                record.client_id(),
                record.approved_by(),
                record.approved_ts(),
                record.approval_note_txt()
        );
    }

    private static final class DefaultDqRulePolicyClient implements DqRulePolicyClient {
    }

    private static final class MissingDqRuleDao implements DqRuleDao {

        @Override
        public List<DqRuleCatalogRecord> findRules(String clientId, String ruleDimensionCode, String lifecycleStatusCode) {
            throw new SemanticLayerException("DqRuleDao is not configured");
        }

        @Override
        public Optional<DqRuleCatalogRecord> findRule(String clientId, String ruleCode) {
            throw new SemanticLayerException("DqRuleDao is not configured");
        }

        @Override
        public List<DqRuleAttributeRecord> findRuleAttributes(String clientId, String ruleCode) {
            throw new SemanticLayerException("DqRuleDao is not configured");
        }

        @Override
        public List<DqRuleResultRecord> findResultsByLogicalAttribute(String clientId, String logicalAttributeCode) {
            throw new SemanticLayerException("DqRuleDao is not configured");
        }

        @Override
        public java.util.Optional<DqRuleRequestWorkflowTaskRecord> findRequest(String clientId, UUID workflowTaskId) {
            throw new SemanticLayerException("DqRuleDao is not configured");
        }

        @Override
        public DqRuleRequestWorkflowTaskRecord insertWorkflowTask(DqRuleRequestWorkflowTaskWriteRequest request) {
            throw new SemanticLayerException("DqRuleDao is not configured");
        }

        @Override
        public DqRuleResultRecord insertResult(DqRuleResultWriteRequest request) {
            throw new SemanticLayerException("DqRuleDao is not configured");
        }

        @Override
        public MetadataChangeHistoryRecord insertMetadataChangeHistory(MetadataChangeHistoryWriteRequest request) {
            throw new SemanticLayerException("DqRuleDao is not configured");
        }
    }

    private static final class NoOpTransactionOperations implements TransactionOperations {

        @Override
        public <T> T execute(TransactionCallback<T> action) {
            return action.doInTransaction(new NoOpTransactionStatus());
        }
    }

    private static final class NoOpTransactionStatus implements TransactionStatus {

        @Override
        public boolean isNewTransaction() {
            return false;
        }

        @Override
        public boolean hasSavepoint() {
            return false;
        }

        @Override
        public void setRollbackOnly() {
        }

        @Override
        public boolean isRollbackOnly() {
            return false;
        }

        @Override
        public void flush() {
        }

        @Override
        public boolean isCompleted() {
            return false;
        }

        @Override
        public Object createSavepoint() {
            return null;
        }

        @Override
        public void rollbackToSavepoint(Object savepoint) {
        }

        @Override
        public void releaseSavepoint(Object savepoint) {
        }
    }
}
