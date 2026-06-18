package com.lextr.semanticlayer.service.impl;

import com.lextr.semanticlayer.dao.FilterLookupRegistrationWriteDao;
import com.lextr.semanticlayer.dao.GovernancePolicyPresetReadDao;
import com.lextr.semanticlayer.dto.FilterLookupPolicyDecisionDto;
import com.lextr.semanticlayer.dto.FilterLookupPolicyRequestDto;
import com.lextr.semanticlayer.dto.FilterLookupRegistrationRequestDto;
import com.lextr.semanticlayer.dto.FilterLookupRegistrationResponseDto;
import com.lextr.semanticlayer.exception.FilterLookupRegistrationServiceException;
import com.lextr.semanticlayer.exception.PolicyViolationException;
import com.lextr.semanticlayer.model.FilterLookupMetadataChangeHistoryRecord;
import com.lextr.semanticlayer.model.FilterLookupMetadataChangeHistoryWriteRequest;
import com.lextr.semanticlayer.model.FilterLookupWorkflowTaskRecord;
import com.lextr.semanticlayer.model.FilterLookupWorkflowTaskWriteRequest;
import com.lextr.semanticlayer.model.GovernancePolicyPresetRecord;
import com.lextr.semanticlayer.model.SemanticFilterLookupRecord;
import com.lextr.semanticlayer.model.SemanticFilterLookupWriteRequest;
import com.lextr.semanticlayer.service.FilterLookupPolicyClient;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FilterLookupRegistrationServiceImplTest {

    @Test
    void registersFilterLookupAtomicallyAndWritesAuditRow() {
        TransactionHarness harness = new TransactionHarness();
        RecordingFilterLookupRegistrationWriteDao writeDao = new RecordingFilterLookupRegistrationWriteDao(harness);
        RecordingFilterLookupPolicyClient policyClient = new RecordingFilterLookupPolicyClient(
                new FilterLookupPolicyDecisionDto(true, null, null)
        );
        FilterLookupRegistrationServiceImpl service = new FilterLookupRegistrationServiceImpl(
                writeDao,
                new RecordingGovernancePolicyPresetReadDao(policyPreset("90")),
                policyClient,
                new RecordingTransactionOperations(harness)
        );

        FilterLookupRegistrationResponseDto response = service.registerFilterLookup(new FilterLookupRegistrationRequestDto(
                "client-a",
                "LEDGER_SCOPE",
                "MANUAL_LIST",
                "HAND_TYPED",
                "meta.gl_balance",
                "ledger_status = 'ACTIVE'",
                "ledger_id",
                "meta.ledger",
                "ledger_id",
                "ledger_id",
                "IN_LIST",
                250,
                5_000,
                120,
                45,
                true,
                true,
                false,
                false,
                "Ledger scope values",
                "producer"
        ));

        assertTrue(harness.committed);
        assertEquals(1, writeDao.committedLookups.size());
        assertEquals(1, writeDao.committedWorkflowTasks.size());
        assertEquals(1, writeDao.committedMetadataChanges.size());
        assertEquals("LEDGER_SCOPE", writeDao.lookupRequest.lookup_cd());
        assertEquals("REVIEW", writeDao.lookupRequest.governance_status_cd());
        assertEquals("PENDING", writeDao.lookupRequest.health_status_cd());
        assertEquals("ACTIVE", writeDao.lookupRequest.lifecycle_status_cd());
        assertEquals("FILTER_LOOKUP_REGISTRATION", writeDao.workflowTaskRequest.task_type_cd());
        assertEquals("PENDING", writeDao.workflowTaskRequest.task_status_cd());
        assertEquals("REGISTERED", writeDao.metadataChangeHistoryRequest.change_type_cd());
        assertTrue(writeDao.metadataChangeHistoryRequest.change_reason_txt().contains("LEDGER_SCOPE"));
        assertEquals(1, policyClient.recordedRequests.size());
        assertEquals("GOV-FL-001", policyClient.recordedRequests.get(0).policy_cd());
        assertEquals(Integer.valueOf(90), policyClient.recordedRequests.get(0).review_period_floor_days());
        assertEquals(Integer.valueOf(45), policyClient.recordedRequests.get(0).review_period_days_override());
        assertEquals(101L, response.id());
        assertEquals("LEDGER_SCOPE", response.lookup_cd());
        assertEquals("REVIEW", response.governance_status_cd());
        assertEquals("PENDING", response.workflow_status_cd());
        assertNotNull(response.next_review_due_dt());
    }

    @Test
    void surfacesPolicyBlockBeforePersistingWrites() {
        TransactionHarness harness = new TransactionHarness();
        RecordingFilterLookupRegistrationWriteDao writeDao = new RecordingFilterLookupRegistrationWriteDao(harness);
        RecordingFilterLookupPolicyClient policyClient = new RecordingFilterLookupPolicyClient(
                new FilterLookupPolicyDecisionDto(false, "GOV-FL-001", "Review period override cannot be looser than the governance floor")
        );
        FilterLookupRegistrationServiceImpl service = new FilterLookupRegistrationServiceImpl(
                writeDao,
                new RecordingGovernancePolicyPresetReadDao(policyPreset("90")),
                policyClient,
                new RecordingTransactionOperations(harness)
        );

        PolicyViolationException exception = assertThrows(PolicyViolationException.class, () -> service.registerFilterLookup(
                new FilterLookupRegistrationRequestDto(
                        "client-a",
                        "LEDGER_SCOPE",
                        "MANUAL_LIST",
                        "HAND_TYPED",
                        null,
                        null,
                        "ledger_id",
                        null,
                        null,
                        null,
                        "IN_LIST",
                        null,
                        null,
                        null,
                        120,
                        null,
                        null,
                        null,
                        null,
                        null,
                        "producer"
                )
        ));

        assertEquals("GOV-FL-001", exception.code());
        assertEquals(0, writeDao.committedLookups.size());
        assertEquals(0, writeDao.committedWorkflowTasks.size());
        assertEquals(0, writeDao.committedMetadataChanges.size());
        assertTrue(!harness.committed);
    }

    @Test
    void wrapsSideEffectFailuresAndRollsBackWrites() {
        TransactionHarness harness = new TransactionHarness();
        FailingFilterLookupRegistrationWriteDao writeDao = new FailingFilterLookupRegistrationWriteDao(harness);
        FilterLookupRegistrationServiceImpl service = new FilterLookupRegistrationServiceImpl(
                writeDao,
                new RecordingGovernancePolicyPresetReadDao(policyPreset("90")),
                request -> new FilterLookupPolicyDecisionDto(true, null, null),
                new RecordingTransactionOperations(harness)
        );

        FilterLookupRegistrationServiceException exception = assertThrows(
                FilterLookupRegistrationServiceException.class,
                () -> service.registerFilterLookup(new FilterLookupRegistrationRequestDto(
                        "client-a",
                        "LEDGER_SCOPE",
                        "MANUAL_LIST",
                        "HAND_TYPED",
                        null,
                        null,
                        "ledger_id",
                        null,
                        null,
                        null,
                        "IN_LIST",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        "producer"
                ))
        );

        assertTrue(exception.getMessage().contains("Unable to register filter lookup"));
        assertTrue(harness.rolledBack);
        assertEquals(0, writeDao.committedLookups.size());
        assertEquals(0, writeDao.committedWorkflowTasks.size());
        assertEquals(0, writeDao.committedMetadataChanges.size());
    }

    private static GovernancePolicyPresetRecord policyPreset(String defaultValue) {
        return new GovernancePolicyPresetRecord(
                "GOV-FL-001",
                "Minimum review frequency (floor, days)",
                "FILTER_LOOKUP",
                defaultValue,
                "INTEGER",
                true,
                true,
                LocalDate.parse("2026-01-01"),
                null,
                "governance-owner",
                OffsetDateTime.parse("2026-01-01T00:00:00Z"),
                OffsetDateTime.parse("2026-01-01T00:00:00Z"),
                "governance-owner"
        );
    }

    private static final class RecordingGovernancePolicyPresetReadDao implements GovernancePolicyPresetReadDao {

        private final GovernancePolicyPresetRecord record;

        private RecordingGovernancePolicyPresetReadDao(GovernancePolicyPresetRecord record) {
            this.record = record;
        }

        @Override
        public Optional<GovernancePolicyPresetRecord> findPolicyPreset(String policyCode,
                                                                       String policyScopeCode,
                                                                       LocalDate asOfDate) {
            return Optional.of(record);
        }
    }

    private static final class RecordingFilterLookupPolicyClient implements FilterLookupPolicyClient {

        private final FilterLookupPolicyDecisionDto decision;
        private final List<FilterLookupPolicyRequestDto> recordedRequests = new ArrayList<>();

        private RecordingFilterLookupPolicyClient(FilterLookupPolicyDecisionDto decision) {
            this.decision = decision;
        }

        @Override
        public FilterLookupPolicyDecisionDto validateReviewPeriodFloor(FilterLookupPolicyRequestDto request) {
            recordedRequests.add(request);
            return decision;
        }
    }

    private static final class RecordingFilterLookupRegistrationWriteDao implements FilterLookupRegistrationWriteDao {

        private final TransactionHarness harness;
        private SemanticFilterLookupWriteRequest lookupRequest;
        private FilterLookupWorkflowTaskWriteRequest workflowTaskRequest;
        private FilterLookupMetadataChangeHistoryWriteRequest metadataChangeHistoryRequest;
        private final List<SemanticFilterLookupRecord> committedLookups = new ArrayList<>();
        private final List<FilterLookupWorkflowTaskRecord> committedWorkflowTasks = new ArrayList<>();
        private final List<FilterLookupMetadataChangeHistoryRecord> committedMetadataChanges = new ArrayList<>();

        private RecordingFilterLookupRegistrationWriteDao(TransactionHarness harness) {
            this.harness = harness;
        }

        @Override
        public SemanticFilterLookupRecord insertLookup(SemanticFilterLookupWriteRequest request) {
            lookupRequest = request;
            SemanticFilterLookupRecord record = new SemanticFilterLookupRecord(
                    101L,
                    request.lookup_cd(),
                    request.construction_type_cd(),
                    request.manual_subtype_cd(),
                    request.filter_obj(),
                    request.filter_condition_txt(),
                    request.filter_attr_cd(),
                    request.validation_obj(),
                    request.validation_attr_cd(),
                    request.suggested_target_attr_cd(),
                    request.execution_strategy_cd(),
                    request.max_input_set_size(),
                    request.max_output_rows(),
                    request.cache_ttl_min(),
                    request.review_period_days_override(),
                    request.rules_eligible_flg(),
                    request.qs_eligible_flg(),
                    request.ai_eligible_flg(),
                    request.replicate_to_ch_flg(),
                    request.description_txt(),
                    request.client_id(),
                    request.governance_status_cd(),
                    request.health_status_cd(),
                    null,
                    null,
                    request.next_review_due_dt(),
                    request.lifecycle_status_cd(),
                    request.created_ts(),
                    request.created_by(),
                    request.updated_ts(),
                    request.updated_by()
            );
            harness.addLookup(record, committedLookups);
            return record;
        }

        @Override
        public FilterLookupWorkflowTaskRecord insertWorkflowTask(FilterLookupWorkflowTaskWriteRequest request) {
            workflowTaskRequest = request;
            FilterLookupWorkflowTaskRecord record = new FilterLookupWorkflowTaskRecord(
                    301L,
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
            harness.addWorkflowTask(record, committedWorkflowTasks);
            return record;
        }

        @Override
        public FilterLookupMetadataChangeHistoryRecord insertMetadataChangeHistory(
                FilterLookupMetadataChangeHistoryWriteRequest request
        ) {
            metadataChangeHistoryRequest = request;
            FilterLookupMetadataChangeHistoryRecord record = new FilterLookupMetadataChangeHistoryRecord(
                    401L,
                    request.entity_type_cd(),
                    request.entity_ref(),
                    request.change_type_cd(),
                    request.changed_by(),
                    request.changed_ts(),
                    request.old_value_json(),
                    request.new_value_json(),
                    request.change_reason_txt()
            );
            harness.addMetadataChange(record, committedMetadataChanges);
            return record;
        }
    }

    private static final class FailingFilterLookupRegistrationWriteDao implements FilterLookupRegistrationWriteDao {

        private final TransactionHarness harness;
        private final List<SemanticFilterLookupRecord> committedLookups = new ArrayList<>();
        private final List<FilterLookupWorkflowTaskRecord> committedWorkflowTasks = new ArrayList<>();
        private final List<FilterLookupMetadataChangeHistoryRecord> committedMetadataChanges = new ArrayList<>();

        private FailingFilterLookupRegistrationWriteDao(TransactionHarness harness) {
            this.harness = harness;
        }

        @Override
        public SemanticFilterLookupRecord insertLookup(SemanticFilterLookupWriteRequest request) {
            SemanticFilterLookupRecord record = new SemanticFilterLookupRecord(
                    101L,
                    request.lookup_cd(),
                    request.construction_type_cd(),
                    request.manual_subtype_cd(),
                    request.filter_obj(),
                    request.filter_condition_txt(),
                    request.filter_attr_cd(),
                    request.validation_obj(),
                    request.validation_attr_cd(),
                    request.suggested_target_attr_cd(),
                    request.execution_strategy_cd(),
                    request.max_input_set_size(),
                    request.max_output_rows(),
                    request.cache_ttl_min(),
                    request.review_period_days_override(),
                    request.rules_eligible_flg(),
                    request.qs_eligible_flg(),
                    request.ai_eligible_flg(),
                    request.replicate_to_ch_flg(),
                    request.description_txt(),
                    request.client_id(),
                    request.governance_status_cd(),
                    request.health_status_cd(),
                    null,
                    null,
                    request.next_review_due_dt(),
                    request.lifecycle_status_cd(),
                    request.created_ts(),
                    request.created_by(),
                    request.updated_ts(),
                    request.updated_by()
            );
            harness.addLookup(record, committedLookups);
            return record;
        }

        @Override
        public FilterLookupWorkflowTaskRecord insertWorkflowTask(FilterLookupWorkflowTaskWriteRequest request) {
            FilterLookupWorkflowTaskRecord record = new FilterLookupWorkflowTaskRecord(
                    301L,
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
            harness.addWorkflowTask(record, committedWorkflowTasks);
            throw new IllegalStateException("db write failed");
        }

        @Override
        public FilterLookupMetadataChangeHistoryRecord insertMetadataChangeHistory(
                FilterLookupMetadataChangeHistoryWriteRequest request
        ) {
            throw new UnsupportedOperationException("Not reached");
        }
    }

    private static final class RecordingTransactionOperations implements TransactionOperations {

        private final TransactionHarness harness;

        private RecordingTransactionOperations(TransactionHarness harness) {
            this.harness = harness;
        }

        @Override
        public <T> T execute(TransactionCallback<T> action) {
            harness.begin();
            try {
                T result = action.doInTransaction(new SimpleTransactionStatus());
                harness.commit();
                return result;
            } catch (RuntimeException exception) {
                harness.rollback();
                throw exception;
            }
        }
    }

    private static final class TransactionHarness {

        private boolean committed;
        private boolean rolledBack;
        private final List<SemanticFilterLookupRecord> pendingLookups = new ArrayList<>();
        private final List<FilterLookupWorkflowTaskRecord> pendingWorkflowTasks = new ArrayList<>();
        private final List<FilterLookupMetadataChangeHistoryRecord> pendingMetadataChanges = new ArrayList<>();
        private List<SemanticFilterLookupRecord> lookupSink;
        private List<FilterLookupWorkflowTaskRecord> workflowTaskSink;
        private List<FilterLookupMetadataChangeHistoryRecord> metadataChangeSink;

        private void begin() {
            committed = false;
            rolledBack = false;
            pendingLookups.clear();
            pendingWorkflowTasks.clear();
            pendingMetadataChanges.clear();
        }

        private void commit() {
            if (lookupSink != null) {
                lookupSink.addAll(pendingLookups);
            }
            if (workflowTaskSink != null) {
                workflowTaskSink.addAll(pendingWorkflowTasks);
            }
            if (metadataChangeSink != null) {
                metadataChangeSink.addAll(pendingMetadataChanges);
            }
            committed = true;
        }

        private void rollback() {
            pendingLookups.clear();
            pendingWorkflowTasks.clear();
            pendingMetadataChanges.clear();
            rolledBack = true;
        }

        private void addLookup(SemanticFilterLookupRecord record, List<SemanticFilterLookupRecord> sink) {
            lookupSink = sink;
            pendingLookups.add(record);
        }

        private void addWorkflowTask(FilterLookupWorkflowTaskRecord record, List<FilterLookupWorkflowTaskRecord> sink) {
            workflowTaskSink = sink;
            pendingWorkflowTasks.add(record);
        }

        private void addMetadataChange(FilterLookupMetadataChangeHistoryRecord record,
                                       List<FilterLookupMetadataChangeHistoryRecord> sink) {
            metadataChangeSink = sink;
            pendingMetadataChanges.add(record);
        }
    }

    private static final class SimpleTransactionStatus implements TransactionStatus {

        @Override
        public boolean isNewTransaction() {
            return true;
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
