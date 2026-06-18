package com.lextr.semanticlayer.service.impl;

import com.lextr.semanticlayer.dao.FilterLookupReadDao;
import com.lextr.semanticlayer.dao.FilterLookupRegistrationWriteDao;
import com.lextr.semanticlayer.dto.FilterLookupBindingPolicyRequestDto;
import com.lextr.semanticlayer.dto.FilterLookupBindingRequestDto;
import com.lextr.semanticlayer.dto.FilterLookupBindingResponseDto;
import com.lextr.semanticlayer.dto.FilterLookupPolicyDecisionDto;
import com.lextr.semanticlayer.exception.FilterLookupBindingServiceException;
import com.lextr.semanticlayer.exception.PolicyViolationException;
import com.lextr.semanticlayer.exception.RegistryResourceNotFoundException;
import com.lextr.semanticlayer.model.FilterLookupBindingRecord;
import com.lextr.semanticlayer.model.FilterLookupBindingWriteRequest;
import com.lextr.semanticlayer.model.FilterLookupMetadataChangeHistoryRecord;
import com.lextr.semanticlayer.model.FilterLookupMetadataChangeHistoryWriteRequest;
import com.lextr.semanticlayer.model.SemanticFilterLookupRecord;
import com.lextr.semanticlayer.service.FilterLookupPolicyClient;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FilterLookupBindingServiceImplTest {

    @Test
    void bindsFilterLookupAtomicallyAndWritesAuditRow() {
        TransactionHarness harness = new TransactionHarness();
        RecordingFilterLookupReadDao readDao = new RecordingFilterLookupReadDao();
        readDao.lookupsByCode.put("LEDGER_SCOPE", lookup("LEDGER_SCOPE", LocalDate.parse("2026-08-02")));
        RecordingFilterLookupRegistrationWriteDao writeDao = new RecordingFilterLookupRegistrationWriteDao(harness);
        RecordingFilterLookupPolicyClient policyClient = new RecordingFilterLookupPolicyClient(
                new FilterLookupPolicyDecisionDto(true, null, null)
        );
        FilterLookupBindingServiceImpl service = new FilterLookupBindingServiceImpl(
                readDao,
                writeDao,
                policyClient,
                new RecordingTransactionOperations(harness)
        );

        FilterLookupBindingResponseDto response = service.bindLookup(
                "LEDGER_SCOPE",
                new FilterLookupBindingRequestDto(
                        "client-a",
                        "meta.gl_balance",
                        "ledger_id",
                        "PIPELINE",
                        "daily-pipeline",
                        "binder"
                )
        );

        assertTrue(harness.committed);
        assertEquals(1, writeDao.committedBindings.size());
        assertEquals(1, writeDao.committedMetadataChanges.size());

        assertEquals("LEDGER_SCOPE", writeDao.bindingRequest.lookup_cd());
        assertEquals("meta.gl_balance", writeDao.bindingRequest.bound_obj());
        assertEquals("ledger_id", writeDao.bindingRequest.bound_attr_cd());
        assertEquals("PIPELINE", writeDao.bindingRequest.binding_context_cd());
        assertEquals("daily-pipeline", writeDao.bindingRequest.binding_ref());
        assertEquals("binder", writeDao.bindingRequest.bound_by());

        assertEquals("BOUND", writeDao.metadataChangeHistoryRequest.change_type_cd());
        assertTrue(writeDao.metadataChangeHistoryRequest.change_reason_txt().contains("LEDGER_SCOPE"));
        assertTrue(writeDao.metadataChangeHistoryRequest.change_reason_txt().contains("meta.gl_balance.ledger_id"));

        assertEquals(1, policyClient.bindingRequests.size());
        assertEquals("LEDGER_SCOPE", policyClient.bindingRequests.get(0).lookup_cd());
        assertEquals("PIPELINE", policyClient.bindingRequests.get(0).binding_context_cd());
        assertEquals(false, policyClient.bindingRequests.get(0).is_overdue());

        assertEquals(501L, response.id());
        assertEquals("LEDGER_SCOPE", response.lookup_cd());
        assertEquals("meta.gl_balance", response.bound_obj());
        assertEquals("ledger_id", response.bound_attr_cd());
        assertEquals("PIPELINE", response.binding_context_cd());
        assertEquals("daily-pipeline", response.binding_ref());
        assertEquals("binder", response.bound_by());
        assertTrue(response.is_active_flg());
    }

    @Test
    void surfacesPolicyBlockBeforePersistingWrites() {
        TransactionHarness harness = new TransactionHarness();
        RecordingFilterLookupReadDao readDao = new RecordingFilterLookupReadDao();
        readDao.lookupsByCode.put("LEDGER_SCOPE", lookup("LEDGER_SCOPE", LocalDate.parse("2026-06-01"))); // overdue since test runs in June 2026
        RecordingFilterLookupRegistrationWriteDao writeDao = new RecordingFilterLookupRegistrationWriteDao(harness);
        RecordingFilterLookupPolicyClient policyClient = new RecordingFilterLookupPolicyClient(
                new FilterLookupPolicyDecisionDto(false, "POL-SV-002", "POL-SV-002: overdue lookup binding is blocked for PIPELINE")
        );
        FilterLookupBindingServiceImpl service = new FilterLookupBindingServiceImpl(
                readDao,
                writeDao,
                policyClient,
                new RecordingTransactionOperations(harness)
        );

        PolicyViolationException exception = assertThrows(
                PolicyViolationException.class,
                () -> service.bindLookup(
                        "LEDGER_SCOPE",
                        new FilterLookupBindingRequestDto(
                                "client-a",
                                "meta.gl_balance",
                                "ledger_id",
                                "PIPELINE",
                                "daily-pipeline",
                                "binder"
                        )
                )
        );

        assertEquals("POL-SV-002", exception.code());
        assertEquals("POL-SV-002: overdue lookup binding is blocked for PIPELINE", exception.getMessage());
        assertEquals(0, writeDao.committedBindings.size());
        assertEquals(0, writeDao.committedMetadataChanges.size());
        assertTrue(!harness.committed);

        assertEquals(1, policyClient.bindingRequests.size());
        assertEquals(true, policyClient.bindingRequests.get(0).is_overdue());
    }

    @Test
    void throwsResourceNotFoundForUnknownLookup() {
        TransactionHarness harness = new TransactionHarness();
        RecordingFilterLookupReadDao readDao = new RecordingFilterLookupReadDao();
        RecordingFilterLookupRegistrationWriteDao writeDao = new RecordingFilterLookupRegistrationWriteDao(harness);
        RecordingFilterLookupPolicyClient policyClient = new RecordingFilterLookupPolicyClient(
                new FilterLookupPolicyDecisionDto(true, null, null)
        );
        FilterLookupBindingServiceImpl service = new FilterLookupBindingServiceImpl(
                readDao,
                writeDao,
                policyClient,
                new RecordingTransactionOperations(harness)
        );

        assertThrows(
                RegistryResourceNotFoundException.class,
                () -> service.bindLookup(
                        "UNKNOWN_LOOKUP",
                        new FilterLookupBindingRequestDto(
                                "client-a",
                                "meta.gl_balance",
                                "ledger_id",
                                "PIPELINE",
                                "daily-pipeline",
                                "binder"
                        )
                )
        );
    }

    @Test
    void wrapsAuditFailuresAndRollsBackWrites() {
        TransactionHarness harness = new TransactionHarness();
        RecordingFilterLookupReadDao readDao = new RecordingFilterLookupReadDao();
        readDao.lookupsByCode.put("LEDGER_SCOPE", lookup("LEDGER_SCOPE", LocalDate.parse("2026-08-02")));
        FailingFilterLookupRegistrationWriteDao writeDao = new FailingFilterLookupRegistrationWriteDao(harness);
        FilterLookupBindingServiceImpl service = new FilterLookupBindingServiceImpl(
                readDao,
                writeDao,
                new RecordingFilterLookupPolicyClient(new FilterLookupPolicyDecisionDto(true, null, null)),
                new RecordingTransactionOperations(harness)
        );

        FilterLookupBindingServiceException exception = assertThrows(
                FilterLookupBindingServiceException.class,
                () -> service.bindLookup(
                        "LEDGER_SCOPE",
                        new FilterLookupBindingRequestDto(
                                "client-a",
                                "meta.gl_balance",
                                "ledger_id",
                                "PIPELINE",
                                "daily-pipeline",
                                "binder"
                        )
                )
        );

        assertTrue(exception.getMessage().contains("Unable to bind filter lookup"));
        assertTrue(harness.rolledBack);
        assertEquals(0, writeDao.committedBindings.size());
        assertEquals(0, writeDao.committedMetadataChanges.size());
    }

    private static SemanticFilterLookupRecord lookup(String lookupCode, LocalDate nextReviewDueDate) {
        return new SemanticFilterLookupRecord(
                101L,
                lookupCode,
                "MANUAL_LIST",
                "HAND_TYPED",
                "meta.gl_balance",
                "ledger_status = 'ACTIVE'",
                "ledger_id",
                "meta.ledger",
                "ledger_id",
                "ledger_id",
                "IN_LIST",
                500,
                10_000,
                60,
                null,
                true,
                true,
                false,
                false,
                "Ledger scope values",
                "client-a",
                "ACTIVE",
                "HEALTHY",
                OffsetDateTime.parse("2026-06-16T10:15:30Z"),
                "certifier",
                nextReviewDueDate,
                "ACTIVE",
                OffsetDateTime.parse("2026-06-16T10:15:30Z"),
                "producer",
                OffsetDateTime.parse("2026-06-17T10:15:30Z"),
                "platform"
        );
    }

    private static final class RecordingFilterLookupReadDao implements FilterLookupReadDao {

        private final Map<String, SemanticFilterLookupRecord> lookupsByCode = new HashMap<>();

        @Override
        public List<SemanticFilterLookupRecord> findLookups(String clientId,
                                                            String governanceStatusCode,
                                                            String healthStatusCode,
                                                            String lifecycleStatusCode) {
            return List.of();
        }

        @Override
        public Optional<SemanticFilterLookupRecord> findLookup(String clientId, String lookupCode) {
            return Optional.ofNullable(lookupsByCode.get(lookupCode));
        }

        @Override
        public List<com.lextr.semanticlayer.model.FilterLookupPreviewValueRecord> findManualValues(String clientId,
                                                                                                    String lookupCode) {
            return List.of();
        }

        @Override
        public long countValues(String clientId, String lookupCode) {
            return 0;
        }
    }

    private static final class RecordingFilterLookupPolicyClient implements FilterLookupPolicyClient {

        private final FilterLookupPolicyDecisionDto bindingDecision;
        private final List<FilterLookupBindingPolicyRequestDto> bindingRequests = new ArrayList<>();

        private RecordingFilterLookupPolicyClient(FilterLookupPolicyDecisionDto bindingDecision) {
            this.bindingDecision = bindingDecision;
        }

        @Override
        public FilterLookupPolicyDecisionDto validateReviewPeriodFloor(com.lextr.semanticlayer.dto.FilterLookupPolicyRequestDto request) {
            return new FilterLookupPolicyDecisionDto(true, null, null);
        }

        @Override
        public FilterLookupPolicyDecisionDto validateBinding(FilterLookupBindingPolicyRequestDto request) {
            bindingRequests.add(request);
            return bindingDecision;
        }
    }

    private static class RecordingFilterLookupRegistrationWriteDao implements FilterLookupRegistrationWriteDao {

        private final TransactionHarness harness;
        private FilterLookupBindingWriteRequest bindingRequest;
        private FilterLookupMetadataChangeHistoryWriteRequest metadataChangeHistoryRequest;
        protected final List<FilterLookupBindingRecord> committedBindings = new ArrayList<>();
        protected final List<FilterLookupMetadataChangeHistoryRecord> committedMetadataChanges = new ArrayList<>();

        private RecordingFilterLookupRegistrationWriteDao(TransactionHarness harness) {
            this.harness = harness;
        }

        @Override
        public SemanticFilterLookupRecord insertLookup(com.lextr.semanticlayer.model.SemanticFilterLookupWriteRequest request) {
            throw new UnsupportedOperationException("Not used in binding tests");
        }

        @Override
        public FilterLookupBindingRecord insertBinding(FilterLookupBindingWriteRequest request) {
            bindingRequest = request;
            FilterLookupBindingRecord record = new FilterLookupBindingRecord(
                    501L,
                    request.lookup_cd(),
                    request.bound_obj(),
                    request.bound_attr_cd(),
                    request.binding_context_cd(),
                    request.binding_ref(),
                    request.bound_by(),
                    request.bound_ts(),
                    request.is_active_flg()
            );
            harness.addBinding(record, committedBindings);
            return record;
        }

        @Override
        public com.lextr.semanticlayer.model.FilterLookupWorkflowTaskRecord insertWorkflowTask(
                com.lextr.semanticlayer.model.FilterLookupWorkflowTaskWriteRequest request
        ) {
            throw new UnsupportedOperationException("Not used in binding tests");
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

    private static final class FailingFilterLookupRegistrationWriteDao extends RecordingFilterLookupRegistrationWriteDao {

        private FailingFilterLookupRegistrationWriteDao(TransactionHarness harness) {
            super(harness);
        }

        @Override
        public FilterLookupMetadataChangeHistoryRecord insertMetadataChangeHistory(
                FilterLookupMetadataChangeHistoryWriteRequest request
        ) {
            throw new IllegalStateException("audit write failed");
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
        private final List<FilterLookupBindingRecord> pendingBindings = new ArrayList<>();
        private final List<FilterLookupMetadataChangeHistoryRecord> pendingMetadataChanges = new ArrayList<>();
        private List<FilterLookupBindingRecord> bindingSink;
        private List<FilterLookupMetadataChangeHistoryRecord> metadataChangeSink;

        private void begin() {
            committed = false;
            rolledBack = false;
            pendingBindings.clear();
            pendingMetadataChanges.clear();
        }

        private void commit() {
            if (bindingSink != null) {
                bindingSink.addAll(pendingBindings);
            }
            if (metadataChangeSink != null) {
                metadataChangeSink.addAll(pendingMetadataChanges);
            }
            committed = true;
        }

        private void rollback() {
            pendingBindings.clear();
            pendingMetadataChanges.clear();
            rolledBack = true;
        }

        private void addBinding(FilterLookupBindingRecord record, List<FilterLookupBindingRecord> sink) {
            bindingSink = sink;
            pendingBindings.add(record);
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
