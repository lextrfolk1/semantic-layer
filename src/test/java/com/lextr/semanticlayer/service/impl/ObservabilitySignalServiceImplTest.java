package com.lextr.semanticlayer.service.impl;

import com.lextr.semanticlayer.dto.ObservabilitySignalCorrelationRequestDto;
import com.lextr.semanticlayer.dto.ObservabilitySignalIngestRequestDto;
import com.lextr.semanticlayer.dto.ObservabilitySignalResponseDto;
import com.lextr.semanticlayer.exception.RegistryResourceNotFoundException;
import com.lextr.semanticlayer.dto.GovernancePolicyPresetDto;
import com.lextr.semanticlayer.dto.WorkflowTaskResponseDto;
import com.lextr.semanticlayer.dao.FilterLookupRegistrationWriteDao;
import com.lextr.semanticlayer.model.ObservabilitySignalCorrelationWriteRequest;
import com.lextr.semanticlayer.model.FilterLookupMetadataChangeHistoryRecord;
import com.lextr.semanticlayer.model.FilterLookupMetadataChangeHistoryWriteRequest;
import com.lextr.semanticlayer.model.FilterLookupWorkflowTaskRecord;
import com.lextr.semanticlayer.model.FilterLookupWorkflowTaskWriteRequest;
import com.lextr.semanticlayer.model.ObservabilitySignalRecord;
import com.lextr.semanticlayer.model.ObservabilitySignalWriteRequest;
import com.lextr.semanticlayer.service.DqRuleService;
import com.lextr.semanticlayer.service.GovernancePolicyPresetReadService;
import com.lextr.semanticlayer.service.ObservabilitySignalService;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

import java.time.OffsetDateTime;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ObservabilitySignalServiceImplTest {

    @Test
    void defaultsMissingSeverityAndStatusBeforePersistingSignal() {
        RecordingObservabilitySignalDao dao = new RecordingObservabilitySignalDao();
        dao.insertedRecord = record(501L, "OPEN", "INFO");
        ObservabilitySignalService service = new ObservabilitySignalServiceImpl(dao);

        ObservabilitySignalResponseDto response = service.ingestSignal(new ObservabilitySignalIngestRequestDto(
                "client-a",
                "FRESHNESS",
                null,
                null,
                "PIPELINE",
                "DATASET",
                "orders",
                "orders#2026-06-18",
                "Freshness lag detected",
                "Latest event lagged by 4h",
                true,
                "Re-run ETL",
                OffsetDateTime.parse("2026-06-18T10:15:30Z"),
                "tooling"
        ));

        assertEquals("INFO", dao.lastInsertRequest.severity_cd());
        assertEquals("OPEN", dao.lastInsertRequest.signal_status_cd());
        assertEquals("tooling", dao.lastInsertRequest.created_by());
        assertEquals(501L, response.id());
        assertEquals("OPEN", response.signal_status_cd());
        assertEquals("INFO", response.severity_cd());
    }

    @Test
    void findSignalsDelegatesFiltersAndMapsResults() {
        RecordingObservabilitySignalDao dao = new RecordingObservabilitySignalDao();
        dao.signals = List.of(record(501L, "OPEN", "WARN"));
        ObservabilitySignalService service = new ObservabilitySignalServiceImpl(dao);

        List<ObservabilitySignalResponseDto> responses = service.findSignals(
                "client-a",
                "FRESHNESS",
                "WARN",
                "OPEN",
                "orders#2026-06-18"
        );

        assertEquals("client-a", dao.lastClientId);
        assertEquals("FRESHNESS", dao.lastSignalTypeCode);
        assertEquals("WARN", dao.lastSeverityCode);
        assertEquals("OPEN", dao.lastSignalStatusCode);
        assertEquals("orders#2026-06-18", dao.lastCorrelationKeyText);
        assertEquals(1, responses.size());
        assertEquals(501L, responses.get(0).id());
    }

    @Test
    void correlateSignalReturnsUpdatedRecord() {
        RecordingObservabilitySignalDao dao = new RecordingObservabilitySignalDao();
        dao.correlatedRecord = Optional.of(record(501L, "TRIAGE", "WARN"));
        ObservabilitySignalService service = new ObservabilitySignalServiceImpl(dao);

        ObservabilitySignalResponseDto response = service.correlateSignal(501L, new ObservabilitySignalCorrelationRequestDto(
                "client-a",
                "TRIAGE",
                701L,
                true,
                "Create DQ rerun",
                OffsetDateTime.parse("2026-06-18T10:20:30Z"),
                OffsetDateTime.parse("2026-06-18T11:20:30Z"),
                "analyst"
        ));

        assertEquals(501L, dao.lastCorrelationRequest.id());
        assertEquals("TRIAGE", dao.lastCorrelationRequest.signal_status_cd());
        assertEquals(701L, dao.lastCorrelationRequest.workflow_task_id());
        assertEquals("analyst", dao.lastCorrelationRequest.updated_by());
        assertEquals("TRIAGE", response.signal_status_cd());
    }

    @Test
    void correlateSignalMapsMissingRowsToNotFound() {
        RecordingObservabilitySignalDao dao = new RecordingObservabilitySignalDao();
        dao.correlatedRecord = Optional.empty();
        ObservabilitySignalService service = new ObservabilitySignalServiceImpl(dao);

        assertThrows(RegistryResourceNotFoundException.class, () -> service.correlateSignal(501L, new ObservabilitySignalCorrelationRequestDto(
                "client-a",
                "TRIAGE",
                null,
                false,
                null,
                null,
                null,
                "analyst"
        )));
    }

    @Test
    void routesWorkflowTaskAndWritesAuditWhenSeverityMeetsPolicyThreshold() {
        RecordingObservabilitySignalDao signalDao = new RecordingObservabilitySignalDao();
        signalDao.insertedRecord = record(501L, "OPEN", "WARN", null);
        RecordingGovernancePolicyPresetReadService policyService = new RecordingGovernancePolicyPresetReadService(
                List.of(
                        policy("GOV-OS-001", "WARN"),
                        policy("GOV-OS-002", "HIGH")
                )
        );
        RecordingFilterLookupRegistrationWriteDao workflowDao = new RecordingFilterLookupRegistrationWriteDao();
        RecordingDqRuleService dqRuleService = new RecordingDqRuleService();
        ObservabilitySignalService service = new ObservabilitySignalServiceImpl(
                signalDao,
                policyService,
                workflowDao,
                dqRuleService,
                new RecordingTransactionOperations()
        );

        ObservabilitySignalResponseDto response = service.ingestSignal(new ObservabilitySignalIngestRequestDto(
                "client-a",
                "FRESHNESS",
                "WARN",
                "OPEN",
                "PIPELINE",
                "DATASET",
                "orders",
                "orders#2026-06-18",
                "Freshness lag detected",
                "Latest event lagged by 4h",
                false,
                null,
                OffsetDateTime.parse("2026-06-18T10:15:30Z"),
                "tooling"
        ));

        assertEquals("OBSERVABILITY_SIGNAL", policyService.lastPolicyScopeCode);
        assertEquals("client-a", policyService.lastClientId);
        assertEquals(1, workflowDao.insertedWorkflowTasks.size());
        assertEquals(2, workflowDao.insertedMetadataChanges.size());
        assertEquals(0, dqRuleService.requests.size());
        assertEquals(501L, response.id());
        assertEquals(901L, response.workflow_task_id());
        assertEquals("TRIAGE", response.signal_status_cd());
    }

    @Test
    void triggersDqRerunWhenSeverityMeetsPolicyThreshold() {
        RecordingObservabilitySignalDao signalDao = new RecordingObservabilitySignalDao();
        signalDao.insertedRecord = record(601L, "OPEN", "WARN", null);
        RecordingGovernancePolicyPresetReadService policyService = new RecordingGovernancePolicyPresetReadService(
                List.of(
                        policy("GOV-OS-001", "HIGH"),
                        policy("GOV-OS-002", "WARN")
                )
        );
        RecordingFilterLookupRegistrationWriteDao workflowDao = new RecordingFilterLookupRegistrationWriteDao();
        RecordingDqRuleService dqRuleService = new RecordingDqRuleService();
        ObservabilitySignalService service = new ObservabilitySignalServiceImpl(
                signalDao,
                policyService,
                workflowDao,
                dqRuleService,
                new RecordingTransactionOperations()
        );

        ObservabilitySignalResponseDto response = service.ingestSignal(new ObservabilitySignalIngestRequestDto(
                "client-a",
                "FRESHNESS",
                "WARN",
                "OPEN",
                "PIPELINE",
                "DATASET",
                "orders",
                "orders#2026-06-18",
                "Freshness lag detected",
                "Latest event lagged by 4h",
                false,
                "Refresh DQ checks",
                OffsetDateTime.parse("2026-06-18T10:15:30Z"),
                "tooling"
        ));

        assertEquals(0, workflowDao.insertedWorkflowTasks.size());
        assertEquals(1, dqRuleService.requests.size());
        assertEquals(List.of("FRESHNESS"), dqRuleService.requests.get(0).rule_names());
        assertEquals("tooling", dqRuleService.requests.get(0).requested_by());
        assertEquals(601L, response.id());
        assertTrue(response.dq_rerun_requested_flg());
        assertEquals("Refresh DQ checks", response.dq_rerun_reason_txt());
    }

    private static ObservabilitySignalRecord record(Long id, String signalStatusCode, String severityCode) {
        return record(id, signalStatusCode, severityCode, 701L);
    }

    private static ObservabilitySignalRecord record(Long id, String signalStatusCode, String severityCode, Long workflowTaskId) {
        OffsetDateTime timestamp = OffsetDateTime.parse("2026-06-18T10:15:30Z");
        return new ObservabilitySignalRecord(
                id,
                "client-a",
                "FRESHNESS",
                severityCode,
                signalStatusCode,
                "PIPELINE",
                "DATASET",
                "orders",
                "orders#2026-06-18",
                "Freshness lag detected",
                "Latest event lagged by 4h",
                timestamp,
                null,
                null,
                workflowTaskId,
                true,
                "Create DQ rerun",
                timestamp,
                "tooling",
                timestamp,
                "tooling"
        );
    }

    private static GovernancePolicyPresetDto policy(String policyCode, String defaultValue) {
        return new GovernancePolicyPresetDto(
                policyCode,
                policyCode,
                "OBSERVABILITY_SIGNAL",
                defaultValue,
                "STRING",
                true,
                true,
                LocalDate.of(2026, 6, 1),
                null,
                "reviewer",
                OffsetDateTime.parse("2026-06-01T10:15:30Z"),
                OffsetDateTime.parse("2026-06-01T10:15:30Z"),
                "reviewer"
        );
    }

    private static final class RecordingObservabilitySignalDao implements com.lextr.semanticlayer.dao.ObservabilitySignalDao {

        private ObservabilitySignalWriteRequest lastInsertRequest;
        private ObservabilitySignalCorrelationWriteRequest lastCorrelationRequest;
        private String lastClientId;
        private String lastSignalTypeCode;
        private String lastSeverityCode;
        private String lastSignalStatusCode;
        private String lastCorrelationKeyText;
        private List<ObservabilitySignalRecord> signals = new ArrayList<>();
        private ObservabilitySignalRecord insertedRecord;
        private Optional<ObservabilitySignalRecord> correlatedRecord = Optional.empty();

        @Override
        public ObservabilitySignalRecord insertSignal(ObservabilitySignalWriteRequest request) {
            lastInsertRequest = request;
            return insertedRecord;
        }

        @Override
        public List<ObservabilitySignalRecord> findSignals(String clientId,
                                                           String signalTypeCode,
                                                           String severityCode,
                                                           String signalStatusCode,
                                                           String correlationKeyText) {
            lastClientId = clientId;
            lastSignalTypeCode = signalTypeCode;
            lastSeverityCode = severityCode;
            lastSignalStatusCode = signalStatusCode;
            lastCorrelationKeyText = correlationKeyText;
            return signals;
        }

        @Override
        public Optional<ObservabilitySignalRecord> correlateSignal(ObservabilitySignalCorrelationWriteRequest request) {
            lastCorrelationRequest = request;
            if (correlatedRecord.isPresent()) {
                return correlatedRecord;
            }
            if (insertedRecord != null && insertedRecord.id().equals(request.id())) {
                ObservabilitySignalRecord updated = new ObservabilitySignalRecord(
                        insertedRecord.id(),
                        insertedRecord.client_id(),
                        insertedRecord.signal_type_cd(),
                        insertedRecord.severity_cd(),
                        request.signal_status_cd(),
                        insertedRecord.source_system_cd(),
                        insertedRecord.source_entity_type_cd(),
                        insertedRecord.source_entity_ref_txt(),
                        insertedRecord.correlation_key_txt(),
                        insertedRecord.finding_summary_txt(),
                        insertedRecord.finding_detail_txt(),
                        insertedRecord.detected_ts(),
                        request.acknowledged_ts(),
                        request.resolved_ts(),
                        request.workflow_task_id(),
                        request.dq_rerun_requested_flg(),
                        request.dq_rerun_reason_txt(),
                        insertedRecord.created_ts(),
                        insertedRecord.created_by(),
                        request.updated_ts(),
                        request.updated_by()
                );
                correlatedRecord = Optional.of(updated);
                return correlatedRecord;
            }
            return Optional.empty();
        }
    }

    private static final class RecordingGovernancePolicyPresetReadService implements GovernancePolicyPresetReadService {

        private final List<GovernancePolicyPresetDto> presets;
        private String lastClientId;
        private String lastPolicyScopeCode;
        private LocalDate lastAsOfDate;

        private RecordingGovernancePolicyPresetReadService(List<GovernancePolicyPresetDto> presets) {
            this.presets = presets;
        }

        @Override
        public List<GovernancePolicyPresetDto> findPolicyPresets(String clientId, String policyScopeCode, LocalDate asOfDate) {
            lastClientId = clientId;
            lastPolicyScopeCode = policyScopeCode;
            lastAsOfDate = asOfDate;
            return presets;
        }

        @Override
        public GovernancePolicyPresetDto findPolicyPreset(String clientId, String policyCode, String policyScopeCode, LocalDate asOfDate) {
            lastClientId = clientId;
            lastPolicyScopeCode = policyScopeCode;
            lastAsOfDate = asOfDate;
            return presets.stream().filter(policy -> policy.policy_cd().equals(policyCode)).findFirst().orElse(null);
        }
    }

    private static final class RecordingFilterLookupRegistrationWriteDao implements FilterLookupRegistrationWriteDao {

        private final List<FilterLookupWorkflowTaskWriteRequest> insertedWorkflowTasks = new ArrayList<>();
        private final List<FilterLookupMetadataChangeHistoryWriteRequest> insertedMetadataChanges = new ArrayList<>();

        @Override
        public com.lextr.semanticlayer.model.SemanticFilterLookupRecord insertLookup(com.lextr.semanticlayer.model.SemanticFilterLookupWriteRequest request) {
            throw new UnsupportedOperationException("Not used in tests");
        }

        @Override
        public FilterLookupWorkflowTaskRecord insertWorkflowTask(FilterLookupWorkflowTaskWriteRequest request) {
            insertedWorkflowTasks.add(request);
            return new FilterLookupWorkflowTaskRecord(
                    900L + insertedWorkflowTasks.size(),
                    request.task_type_cd(),
                    request.entity_type_cd(),
                    request.entity_ref(),
                    request.task_status_cd(),
                    request.submitted_by(),
                    request.submitted_ts(),
                    request.assigned_to(),
                    request.due_dt(),
                    request.description_txt(),
                    request.client_id(),
                    request.approved_by(),
                    request.approved_ts(),
                    request.approval_note_txt()
            );
        }

        @Override
        public FilterLookupMetadataChangeHistoryRecord insertMetadataChangeHistory(FilterLookupMetadataChangeHistoryWriteRequest request) {
            insertedMetadataChanges.add(request);
            return new FilterLookupMetadataChangeHistoryRecord(
                    800L + insertedMetadataChanges.size(),
                    request.entity_type_cd(),
                    request.entity_ref(),
                    request.change_type_cd(),
                    request.changed_by(),
                    request.changed_ts(),
                    request.old_value_json(),
                    request.new_value_json(),
                    request.change_reason_txt()
            );
        }
    }

    private static final class RecordingDqRuleService implements DqRuleService {

        private final List<com.lextr.semanticlayer.dto.DqRuleRequestDto> requests = new ArrayList<>();

        @Override
        public List<com.lextr.semanticlayer.dto.DqRuleCatalogDto> findRules(String clientId, String ruleDimensionCode, String lifecycleStatusCode) {
            throw new UnsupportedOperationException("Not used in tests");
        }

        @Override
        public com.lextr.semanticlayer.dto.DqRuleCatalogDto findRule(String clientId, String ruleCode) {
            throw new UnsupportedOperationException("Not used in tests");
        }

        @Override
        public List<com.lextr.semanticlayer.dto.DqRuleAttributeDto> findRuleAttributes(String clientId, String ruleCode) {
            throw new UnsupportedOperationException("Not used in tests");
        }

        @Override
        public List<com.lextr.semanticlayer.dto.DqRuleResultDto> findRuleResults(String clientId, String logicalAttributeCode) {
            throw new UnsupportedOperationException("Not used in tests");
        }

        @Override
        public List<WorkflowTaskResponseDto> requestRules(com.lextr.semanticlayer.dto.DqRuleRequestDto request) {
            requests.add(request);
            return List.of();
        }

        @Override
        public WorkflowTaskResponseDto findRequest(String clientId, java.util.UUID workflowTaskId) {
            throw new UnsupportedOperationException("Not used in tests");
        }
    }

    private static final class RecordingTransactionOperations implements TransactionOperations {

        @Override
        public <T> T execute(TransactionCallback<T> action) {
            return action.doInTransaction(new RecordingTransactionStatus());
        }
    }

    private static final class RecordingTransactionStatus implements TransactionStatus {

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
