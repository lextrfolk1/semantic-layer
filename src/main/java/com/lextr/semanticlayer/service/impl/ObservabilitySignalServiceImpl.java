package com.lextr.semanticlayer.service.impl;

import com.lextr.semanticlayer.dao.FilterLookupRegistrationWriteDao;
import com.lextr.semanticlayer.dao.ObservabilitySignalDao;
import com.lextr.semanticlayer.dto.DqRuleRequestDto;
import com.lextr.semanticlayer.dto.GovernancePolicyPresetDto;
import com.lextr.semanticlayer.dto.ObservabilitySignalAutoTriggerPolicyRequestDto;
import com.lextr.semanticlayer.dto.ObservabilitySignalCorrelationRequestDto;
import com.lextr.semanticlayer.dto.ObservabilitySignalIngestRequestDto;
import com.lextr.semanticlayer.dto.ObservabilitySignalPolicyDecisionDto;
import com.lextr.semanticlayer.dto.ObservabilitySignalResponseDto;
import com.lextr.semanticlayer.dto.WorkflowTaskResponseDto;
import com.lextr.semanticlayer.exception.ObservabilitySignalServiceException;
import com.lextr.semanticlayer.exception.RegistryResourceNotFoundException;
import com.lextr.semanticlayer.model.FilterLookupMetadataChangeHistoryRecord;
import com.lextr.semanticlayer.model.FilterLookupMetadataChangeHistoryWriteRequest;
import com.lextr.semanticlayer.model.FilterLookupWorkflowTaskRecord;
import com.lextr.semanticlayer.model.FilterLookupWorkflowTaskWriteRequest;
import com.lextr.semanticlayer.model.ObservabilitySignalCorrelationWriteRequest;
import com.lextr.semanticlayer.model.ObservabilitySignalRecord;
import com.lextr.semanticlayer.model.ObservabilitySignalWriteRequest;
import com.lextr.semanticlayer.service.DqRuleService;
import com.lextr.semanticlayer.service.GovernancePolicyPresetReadService;
import com.lextr.semanticlayer.service.ObservabilitySignalService;
import com.lextr.semanticlayer.service.ObservabilitySignalPolicyClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Service
public class ObservabilitySignalServiceImpl implements ObservabilitySignalService {

    private static final String OBSERVABILITY_POLICY_SCOPE_CD = "OBSERVABILITY_SIGNAL";
    private static final String WORKFLOW_ROUTE_POLICY_CD = "GOV-OS-001";
    private static final String DQ_RERUN_POLICY_CD = "GOV-OS-002";
    private static final String DEFAULT_WORKFLOW_ROUTE_THRESHOLD_CD = "WARN";
    private static final String DEFAULT_DQ_RERUN_THRESHOLD_CD = "HIGH";
    private static final String WORKFLOW_TASK_TYPE_CD = "OBSERVABILITY_SIGNAL_ROUTE";
    private static final String WORKFLOW_ENTITY_TYPE_CD = "OBSERVABILITY_SIGNAL";
    private static final String WORKFLOW_TASK_STATUS_CD = "PENDING";
    private static final String DEFAULT_WORKFLOW_ASSIGNEE = "semantic-layer";
    private static final String AUDIT_INGESTED = "INGESTED";
    private static final String AUDIT_CORRELATED = "CORRELATED";
    private static final String AUDIT_ROUTED = "ROUTED";
    private static final String AUDIT_DQ_RERUN_TRIGGERED = "DQ_RERUN_TRIGGERED";
    private static final String AUTO_TRIGGER_POLICY_CD = "POL-OS-001";
    private static final String WORKFLOW_ROUTE_TRIGGER_CD = "WORKFLOW_ROUTE";
    private static final String DQ_RERUN_TRIGGER_CD = "DQ_RERUN";

    private static final String DEFAULT_SIGNAL_STATUS_CD = "OPEN";
    private static final String DEFAULT_SEVERITY_CD = "INFO";

    private final ObservabilitySignalDao observabilitySignalDao;
    private final ObservabilitySignalPolicyClient observabilitySignalPolicyClient;
    private final GovernancePolicyPresetReadService governancePolicyPresetReadService;
    private final FilterLookupRegistrationWriteDao filterLookupRegistrationWriteDao;
    private final DqRuleService dqRuleService;
    private final TransactionOperations transactionOperations;
    private final boolean automationEnabled;

    public ObservabilitySignalServiceImpl(ObjectProvider<ObservabilitySignalDao> observabilitySignalDaoProvider) {
        this(
                observabilitySignalDaoProvider.getIfAvailable(MissingObservabilitySignalDao::new),
                new DefaultObservabilitySignalPolicyClient(),
                null,
                null,
                null,
                new NoOpTransactionOperations(),
                false
        );
    }

    ObservabilitySignalServiceImpl(ObservabilitySignalDao observabilitySignalDao) {
        this(observabilitySignalDao, new DefaultObservabilitySignalPolicyClient(), null, null, null, new NoOpTransactionOperations(), false);
    }

    @Autowired
    public ObservabilitySignalServiceImpl(ObjectProvider<ObservabilitySignalDao> observabilitySignalDaoProvider,
                                          ObjectProvider<ObservabilitySignalPolicyClient> observabilitySignalPolicyClientProvider,
                                          ObjectProvider<GovernancePolicyPresetReadService> governancePolicyPresetReadServiceProvider,
                                          ObjectProvider<FilterLookupRegistrationWriteDao> filterLookupRegistrationWriteDaoProvider,
                                          ObjectProvider<DqRuleService> dqRuleServiceProvider,
                                          @Qualifier("semanticLayerTransactionOperations")
                                          ObjectProvider<TransactionOperations> transactionOperationsProvider) {
        this(
                observabilitySignalDaoProvider.getIfAvailable(MissingObservabilitySignalDao::new),
                observabilitySignalPolicyClientProvider.getIfAvailable(DefaultObservabilitySignalPolicyClient::new),
                governancePolicyPresetReadServiceProvider.getIfAvailable(),
                filterLookupRegistrationWriteDaoProvider.getIfAvailable(),
                dqRuleServiceProvider.getIfAvailable(),
                transactionOperationsProvider.getIfAvailable(NoOpTransactionOperations::new),
                true
        );
    }

    public ObservabilitySignalServiceImpl(ObservabilitySignalDao observabilitySignalDao,
                                          ObservabilitySignalPolicyClient observabilitySignalPolicyClient,
                                          GovernancePolicyPresetReadService governancePolicyPresetReadService,
                                          FilterLookupRegistrationWriteDao filterLookupRegistrationWriteDao,
                                          DqRuleService dqRuleService,
                                          TransactionOperations transactionOperations) {
        this(observabilitySignalDao, observabilitySignalPolicyClient, governancePolicyPresetReadService, filterLookupRegistrationWriteDao, dqRuleService, transactionOperations, true);
    }

    ObservabilitySignalServiceImpl(ObservabilitySignalDao observabilitySignalDao,
                                   ObservabilitySignalPolicyClient observabilitySignalPolicyClient,
                                   GovernancePolicyPresetReadService governancePolicyPresetReadService,
                                   FilterLookupRegistrationWriteDao filterLookupRegistrationWriteDao,
                                   DqRuleService dqRuleService,
                                   TransactionOperations transactionOperations,
                                   boolean automationEnabled) {
        this.observabilitySignalDao = observabilitySignalDao;
        this.observabilitySignalPolicyClient = observabilitySignalPolicyClient;
        this.governancePolicyPresetReadService = governancePolicyPresetReadService;
        this.filterLookupRegistrationWriteDao = filterLookupRegistrationWriteDao;
        this.dqRuleService = dqRuleService;
        this.transactionOperations = transactionOperations;
        this.automationEnabled = automationEnabled;
    }

    @Override
    public ObservabilitySignalResponseDto ingestSignal(ObservabilitySignalIngestRequestDto request) {
        return transactionOperations.execute(status -> ingestSignalWithinTransaction(request));
    }

    @Override
    public List<ObservabilitySignalResponseDto> findSignals(String clientId,
                                                            String signalTypeCode,
                                                            String severityCode,
                                                            String signalStatusCode,
                                                            String correlationKeyText) {
        return observabilitySignalDao.findSignals(clientId, signalTypeCode, severityCode, signalStatusCode, correlationKeyText)
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Override
    public ObservabilitySignalResponseDto correlateSignal(Long signalId, ObservabilitySignalCorrelationRequestDto request) {
        return transactionOperations.execute(status -> correlateSignalWithinTransaction(signalId, request));
    }

    private ObservabilitySignalResponseDto ingestSignalWithinTransaction(ObservabilitySignalIngestRequestDto request) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        ObservabilitySignalRecord record = observabilitySignalDao.insertSignal(new ObservabilitySignalWriteRequest(
                request.client_id(),
                request.signal_type_cd(),
                defaultText(request.severity_cd(), DEFAULT_SEVERITY_CD),
                defaultText(request.signal_status_cd(), DEFAULT_SIGNAL_STATUS_CD),
                request.source_system_cd(),
                request.source_entity_type_cd(),
                request.source_entity_ref_txt(),
                request.correlation_key_txt(),
                request.finding_summary_txt(),
                request.finding_detail_txt(),
                request.detected_ts(),
                request.dq_rerun_requested_flg(),
                request.dq_rerun_reason_txt(),
                now,
                request.reported_by(),
                now,
                request.reported_by()
        ));

        writeAudit(record.client_id(), record.id(), AUDIT_INGESTED, request.reported_by(), now,
                "Ingested observability signal " + record.signal_type_cd());

        if (!automationEnabled) {
            return toDto(record);
        }

        ObservabilityAutomationPolicies policies = resolveAutomationPolicies(record.client_id(), now.toLocalDate());
        ObservabilitySignalRecord effectiveRecord = record;

        FilterLookupWorkflowTaskRecord workflowTask = null;
        if (shouldRouteToWorkflow(record, policies)) {
            workflowTask = routeToWorkflow(record, request.reported_by(), now);
            writeAudit(record.client_id(), record.id(), AUDIT_ROUTED, request.reported_by(), now,
                    "Routed observability signal " + record.signal_type_cd() + " to workflow task " + workflowTask.id());
        }

        boolean dqRerunTriggered = request.dq_rerun_requested_flg() || shouldTriggerDqRerun(record, policies);
        if (dqRerunTriggered) {
            String rerunReason = defaultText(request.dq_rerun_reason_txt(),
                    "DQ rerun requested for observability signal " + record.signal_type_cd());
            triggerDqRerunRequest(record, request.reported_by(), rerunReason);
            writeAudit(record.client_id(), record.id(), AUDIT_DQ_RERUN_TRIGGERED, request.reported_by(), now, rerunReason);
        }

        if (workflowTask != null || dqRerunTriggered) {
            effectiveRecord = updateSignalCorrelation(
                    record.id(),
                    record.client_id(),
                    workflowTask != null ? "TRIAGE" : record.signal_status_cd(),
                    workflowTask != null ? workflowTask.id() : record.workflow_task_id(),
                    dqRerunTriggered,
                    dqRerunTriggered
                            ? defaultText(request.dq_rerun_reason_txt(),
                            "DQ rerun requested for observability signal " + record.signal_type_cd())
                            : record.dq_rerun_reason_txt(),
                    null,
                    null,
                    now,
                    request.reported_by()
            );
        }

        return toDto(effectiveRecord);
    }

    private ObservabilitySignalResponseDto correlateSignalWithinTransaction(Long signalId,
                                                                           ObservabilitySignalCorrelationRequestDto request) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        ObservabilitySignalRecord currentRecord = observabilitySignalDao.correlateSignal(
                new ObservabilitySignalCorrelationWriteRequest(
                        signalId,
                        request.client_id(),
                        request.signal_status_cd(),
                        request.workflow_task_id(),
                        request.dq_rerun_requested_flg(),
                        request.dq_rerun_reason_txt(),
                        request.acknowledged_ts(),
                        request.resolved_ts(),
                        now,
                        request.correlated_by()
                )
        ).orElseThrow(() -> new RegistryResourceNotFoundException("observability signal", String.valueOf(signalId)));

        writeAudit(currentRecord.client_id(), currentRecord.id(), AUDIT_CORRELATED, request.correlated_by(), now,
                "Correlated observability signal " + currentRecord.signal_type_cd() + " to status " + request.signal_status_cd());

        if (!automationEnabled) {
            return toDto(currentRecord);
        }

        ObservabilityAutomationPolicies policies = resolveAutomationPolicies(currentRecord.client_id(), now.toLocalDate());
        ObservabilitySignalRecord effectiveRecord = currentRecord;

        if (request.workflow_task_id() == null && shouldRouteToWorkflow(currentRecord, policies)) {
            FilterLookupWorkflowTaskRecord workflowTask = routeToWorkflow(currentRecord, request.correlated_by(), now);
            writeAudit(currentRecord.client_id(), currentRecord.id(), AUDIT_ROUTED, request.correlated_by(), now,
                    "Routed observability signal " + currentRecord.signal_type_cd() + " to workflow task " + workflowTask.id());
            effectiveRecord = updateSignalCorrelation(
                    effectiveRecord.id(),
                    effectiveRecord.client_id(),
                    "TRIAGE",
                    workflowTask.id(),
                    effectiveRecord.dq_rerun_requested_flg(),
                    effectiveRecord.dq_rerun_reason_txt(),
                    request.acknowledged_ts(),
                    request.resolved_ts(),
                    now,
                    request.correlated_by()
            );
        }

        boolean dqRerunTriggered = request.dq_rerun_requested_flg() || shouldTriggerDqRerun(currentRecord, policies);
        if (dqRerunTriggered) {
            String rerunReason = defaultText(request.dq_rerun_reason_txt(),
                    "DQ rerun requested for observability signal " + currentRecord.signal_type_cd());
            triggerDqRerunRequest(currentRecord, request.correlated_by(), rerunReason);
            writeAudit(currentRecord.client_id(), currentRecord.id(), AUDIT_DQ_RERUN_TRIGGERED, request.correlated_by(), now, rerunReason);
            effectiveRecord = updateSignalCorrelation(
                    effectiveRecord.id(),
                    effectiveRecord.client_id(),
                    effectiveRecord.signal_status_cd(),
                    effectiveRecord.workflow_task_id(),
                    true,
                    rerunReason,
                    request.acknowledged_ts(),
                    request.resolved_ts(),
                    now,
                    request.correlated_by()
            );
        }

        return toDto(effectiveRecord);
    }

    private ObservabilitySignalRecord updateSignalCorrelation(Long signalId,
                                                              String clientId,
                                                              String signalStatusCode,
                                                              Long workflowTaskId,
                                                              boolean dqRerunRequested,
                                                              String dqRerunReason,
                                                              OffsetDateTime acknowledgedTs,
                                                              OffsetDateTime resolvedTs,
                                                              OffsetDateTime now,
                                                              String updatedBy) {
        return observabilitySignalDao.correlateSignal(new ObservabilitySignalCorrelationWriteRequest(
                signalId,
                clientId,
                signalStatusCode,
                workflowTaskId,
                dqRerunRequested,
                dqRerunReason,
                acknowledgedTs,
                resolvedTs,
                now,
                updatedBy
        )).orElseThrow(() -> new RegistryResourceNotFoundException("observability signal", String.valueOf(signalId)));
    }

    private ObservabilitySignalResponseDto toDto(ObservabilitySignalRecord record) {
        return new ObservabilitySignalResponseDto(
                record.id(),
                record.client_id(),
                record.signal_type_cd(),
                record.severity_cd(),
                record.signal_status_cd(),
                record.source_system_cd(),
                record.source_entity_type_cd(),
                record.source_entity_ref_txt(),
                record.correlation_key_txt(),
                record.finding_summary_txt(),
                record.finding_detail_txt(),
                record.detected_ts(),
                record.acknowledged_ts(),
                record.resolved_ts(),
                record.workflow_task_id(),
                record.dq_rerun_requested_flg(),
                record.dq_rerun_reason_txt(),
                record.created_ts(),
                record.created_by(),
                record.updated_ts(),
                record.updated_by()
        );
    }

    private ObservabilityAutomationPolicies resolveAutomationPolicies(String clientId, LocalDate asOfDate) {
        if (governancePolicyPresetReadService == null) {
            return new ObservabilityAutomationPolicies(DEFAULT_WORKFLOW_ROUTE_THRESHOLD_CD, DEFAULT_DQ_RERUN_THRESHOLD_CD);
        }
        List<GovernancePolicyPresetDto> presets = governancePolicyPresetReadService.findPolicyPresets(
                clientId,
                OBSERVABILITY_POLICY_SCOPE_CD,
                asOfDate
        );
        return new ObservabilityAutomationPolicies(
                policyValue(presets, WORKFLOW_ROUTE_POLICY_CD, DEFAULT_WORKFLOW_ROUTE_THRESHOLD_CD),
                policyValue(presets, DQ_RERUN_POLICY_CD, DEFAULT_DQ_RERUN_THRESHOLD_CD)
        );
    }

    private String policyValue(List<GovernancePolicyPresetDto> presets, String policyCode, String defaultValue) {
        return presets.stream()
                .filter(policy -> policyCode.equals(policy.policy_cd()))
                .map(GovernancePolicyPresetDto::default_value_txt)
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse(defaultValue);
    }

    private boolean shouldRouteToWorkflow(ObservabilitySignalRecord record, ObservabilityAutomationPolicies policies) {
        return autoTriggerAllowed(record, policies.workflowRouteThresholdCd(), WORKFLOW_ROUTE_TRIGGER_CD);
    }

    private boolean shouldTriggerDqRerun(ObservabilitySignalRecord record, ObservabilityAutomationPolicies policies) {
        return autoTriggerAllowed(record, policies.dqRerunThresholdCd(), DQ_RERUN_TRIGGER_CD);
    }

    private boolean autoTriggerAllowed(ObservabilitySignalRecord record, String thresholdSeverityCd, String triggerCd) {
        ObservabilitySignalPolicyDecisionDto decision = observabilitySignalPolicyClient.validateAutoTrigger(
                new ObservabilitySignalAutoTriggerPolicyRequestDto(
                        AUTO_TRIGGER_POLICY_CD,
                        record.client_id(),
                        record.signal_type_cd(),
                        triggerCd,
                        record.severity_cd(),
                        thresholdSeverityCd
                )
        );
        return decision.allowed();
    }

    private FilterLookupWorkflowTaskRecord routeToWorkflow(ObservabilitySignalRecord record, String submittedBy, OffsetDateTime now) {
        if (filterLookupRegistrationWriteDao == null) {
            throw new ObservabilitySignalServiceException("Workflow task routing is not configured");
        }
        return filterLookupRegistrationWriteDao.insertWorkflowTask(new FilterLookupWorkflowTaskWriteRequest(
                WORKFLOW_TASK_TYPE_CD,
                WORKFLOW_ENTITY_TYPE_CD,
                String.valueOf(record.id()),
                WORKFLOW_TASK_STATUS_CD,
                submittedBy,
                now,
                DEFAULT_WORKFLOW_ASSIGNEE,
                now.toLocalDate().plusDays(7),
                "Route observability signal " + record.signal_type_cd() + " for client " + record.client_id(),
                record.client_id(),
                null,
                null,
                null
        ));
    }

    private List<WorkflowTaskResponseDto> triggerDqRerunRequest(ObservabilitySignalRecord record,
                                                                String requestedBy,
                                                                String requestText) {
        if (dqRuleService == null) {
            throw new ObservabilitySignalServiceException("DQ rerun trigger is not configured");
        }
        return dqRuleService.requestRules(new DqRuleRequestDto(
                record.client_id(),
                List.of(record.signal_type_cd()),
                requestedBy,
                requestText
        ));
    }

    private void writeAudit(String clientId,
                            Long signalId,
                            String changeTypeCode,
                            String changedBy,
                            OffsetDateTime changedTs,
                            String changeReasonTxt) {
        if (filterLookupRegistrationWriteDao == null) {
            return;
        }
        filterLookupRegistrationWriteDao.insertMetadataChangeHistory(new FilterLookupMetadataChangeHistoryWriteRequest(
                WORKFLOW_ENTITY_TYPE_CD,
                String.valueOf(signalId),
                changeTypeCode,
                changedBy,
                changedTs,
                null,
                null,
                changeReasonTxt
        ));
    }

    private static String defaultText(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private static final class MissingObservabilitySignalDao implements ObservabilitySignalDao {

        @Override
        public ObservabilitySignalRecord insertSignal(ObservabilitySignalWriteRequest request) {
            throw new ObservabilitySignalServiceException("ObservabilitySignalDao is not configured");
        }

        @Override
        public List<ObservabilitySignalRecord> findSignals(String clientId,
                                                           String signalTypeCode,
                                                           String severityCode,
                                                           String signalStatusCode,
                                                           String correlationKeyText) {
            throw new ObservabilitySignalServiceException("ObservabilitySignalDao is not configured");
        }

        @Override
        public java.util.Optional<ObservabilitySignalRecord> correlateSignal(ObservabilitySignalCorrelationWriteRequest request) {
            throw new ObservabilitySignalServiceException("ObservabilitySignalDao is not configured");
        }
    }

    private static final class NoOpTransactionOperations implements TransactionOperations {

        @Override
        public <T> T execute(TransactionCallback<T> action) {
            return action.doInTransaction(new NoOpTransactionStatus());
        }
    }

    private static final class DefaultObservabilitySignalPolicyClient implements ObservabilitySignalPolicyClient {

        @Override
        public ObservabilitySignalPolicyDecisionDto validateAutoTrigger(ObservabilitySignalAutoTriggerPolicyRequestDto request) {
            if (!validInput(request)) {
                return deny("POL-OS-001: unknown or invalid input");
            }

            int severityRank = severityRank(request.severity_cd());
            int thresholdRank = severityRank(request.threshold_severity_cd());
            if (severityRank >= thresholdRank) {
                return allow("POL-OS-001: allow");
            }

            return deny("POL-OS-001: auto-trigger denied for " + request.trigger_cd()
                    + " because severity " + request.severity_cd()
                    + " is below threshold " + request.threshold_severity_cd());
        }

        private boolean validInput(ObservabilitySignalAutoTriggerPolicyRequestDto request) {
            return request != null
                    && "POL-OS-001".equals(request.policy_cd())
                    && isPopulated(request.client_id())
                    && isPopulated(request.signal_type_cd())
                    && isPopulated(request.trigger_cd())
                    && isPopulated(request.severity_cd())
                    && isPopulated(request.threshold_severity_cd())
                    && severityRank(request.severity_cd()) > 0
                    && severityRank(request.threshold_severity_cd()) > 0
                    && ("WORKFLOW_ROUTE".equals(request.trigger_cd()) || "DQ_RERUN".equals(request.trigger_cd()));
        }

        private boolean isPopulated(String value) {
            return value != null && !value.isBlank();
        }

        private ObservabilitySignalPolicyDecisionDto allow(String reason) {
            return new ObservabilitySignalPolicyDecisionDto(true, "POL-OS-001", reason);
        }

        private ObservabilitySignalPolicyDecisionDto deny(String reason) {
            return new ObservabilitySignalPolicyDecisionDto(false, "POL-OS-001", reason);
        }
    }

    private static int severityRank(String severityCode) {
        if (severityCode == null) {
            return 0;
        }
        return switch (severityCode.trim().toUpperCase()) {
            case "HIGH" -> 3;
            case "WARN" -> 2;
            case "INFO" -> 1;
            default -> 0;
        };
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

    private record ObservabilityAutomationPolicies(String workflowRouteThresholdCd, String dqRerunThresholdCd) {
    }
}
