package com.lextr.semanticlayer.service.impl;

import com.lextr.semanticlayer.dao.FilterLookupReadDao;
import com.lextr.semanticlayer.dao.FilterLookupRegistrationWriteDao;
import com.lextr.semanticlayer.dao.GovernancePolicyPresetReadDao;
import com.lextr.semanticlayer.dto.FilterLookupCertificationPolicyRequestDto;
import com.lextr.semanticlayer.dto.FilterLookupCertificationRequestDto;
import com.lextr.semanticlayer.dto.FilterLookupEffectiveReviewDto;
import com.lextr.semanticlayer.dto.FilterLookupPolicyDecisionDto;
import com.lextr.semanticlayer.exception.FilterLookupCertificationServiceException;
import com.lextr.semanticlayer.exception.PolicyViolationException;
import com.lextr.semanticlayer.model.FilterLookupCertificationWriteRequest;
import com.lextr.semanticlayer.model.FilterLookupMetadataChangeHistoryRecord;
import com.lextr.semanticlayer.model.FilterLookupMetadataChangeHistoryWriteRequest;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FilterLookupCertificationServiceImplTest {

    @Test
    void certifiesFilterLookupAtomicallyAndWritesAuditRow() {
        TransactionHarness harness = new TransactionHarness();
        RecordingFilterLookupReadDao readDao = new RecordingFilterLookupReadDao();
        readDao.lookupsByCode.put("LEDGER_SCOPE", lookup("LEDGER_SCOPE", "PENDING", null, null, LocalDate.parse("2026-08-02")));
        readDao.valueCountsByLookup.put("LEDGER_SCOPE", 2L);
        readDao.staleCountsByLookup.put("LEDGER_SCOPE", 0L);
        RecordingFilterLookupRegistrationWriteDao writeDao = new RecordingFilterLookupRegistrationWriteDao(harness);
        RecordingFilterLookupPolicyClient policyClient = new RecordingFilterLookupPolicyClient(
                new FilterLookupPolicyDecisionDto(true, null, null)
        );
        FilterLookupCertificationServiceImpl service = new FilterLookupCertificationServiceImpl(
                readDao,
                writeDao,
                new RecordingGovernancePolicyPresetReadDao(policyPreset("90")),
                policyClient,
                new RecordingTransactionOperations(harness)
        );

        FilterLookupEffectiveReviewDto response = service.certifyLookup(
                "LEDGER_SCOPE",
                new FilterLookupCertificationRequestDto("client-a", "certifier")
        );

        assertTrue(harness.committed);
        assertEquals(1, writeDao.committedCertifiedLookups.size());
        assertEquals(1, writeDao.committedMetadataChanges.size());
        assertEquals("LEDGER_SCOPE", writeDao.certificationRequest.lookup_cd());
        assertEquals("client-a", writeDao.certificationRequest.client_id());
        assertEquals("HEALTHY", writeDao.certificationRequest.health_status_cd());
        assertEquals("certifier", writeDao.certificationRequest.last_certified_by());
        assertEquals("CERTIFIED", writeDao.metadataChangeHistoryRequest.change_type_cd());
        assertTrue(writeDao.metadataChangeHistoryRequest.change_reason_txt().contains("LEDGER_SCOPE"));
        assertEquals(1, policyClient.certificationRequests.size());
        assertEquals("LEDGER_SCOPE", policyClient.certificationRequests.get(0).lookup_cd());
        assertEquals(0L, policyClient.certificationRequests.get(0).stale_value_count());
        assertEquals("LEDGER_SCOPE", response.lookup_cd());
        assertEquals("HEALTHY", response.health_status_cd());
        assertEquals("certifier", response.last_certified_by());
        assertEquals(2L, response.value_count());
        assertEquals("GOV_DEFAULT", response.effective_review_period_source_cd());
    }

    @Test
    void surfacesPolicyBlockBeforePersistingWrites() {
        TransactionHarness harness = new TransactionHarness();
        RecordingFilterLookupReadDao readDao = new RecordingFilterLookupReadDao();
        readDao.lookupsByCode.put("LEDGER_SCOPE", lookup("LEDGER_SCOPE", "PENDING", null, null, LocalDate.parse("2026-08-02")));
        readDao.staleCountsByLookup.put("LEDGER_SCOPE", 3L);
        RecordingFilterLookupRegistrationWriteDao writeDao = new RecordingFilterLookupRegistrationWriteDao(harness);
        RecordingFilterLookupPolicyClient policyClient = new RecordingFilterLookupPolicyClient(
                new FilterLookupPolicyDecisionDto(false, "POL-SV-001", "Stale values block certification")
        );
        FilterLookupCertificationServiceImpl service = new FilterLookupCertificationServiceImpl(
                readDao,
                writeDao,
                new RecordingGovernancePolicyPresetReadDao(policyPreset("90")),
                policyClient,
                new RecordingTransactionOperations(harness)
        );

        PolicyViolationException exception = assertThrows(
                PolicyViolationException.class,
                () -> service.certifyLookup("LEDGER_SCOPE", new FilterLookupCertificationRequestDto("client-a", "certifier"))
        );

        assertEquals("POL-SV-001", exception.code());
        assertEquals(0, writeDao.committedCertifiedLookups.size());
        assertEquals(0, writeDao.committedMetadataChanges.size());
        assertTrue(!harness.committed);
        assertEquals(1, policyClient.certificationRequests.size());
        assertEquals(3L, policyClient.certificationRequests.get(0).stale_value_count());
    }

    @Test
    void wrapsAuditFailuresAndRollsBackWrites() {
        TransactionHarness harness = new TransactionHarness();
        RecordingFilterLookupReadDao readDao = new RecordingFilterLookupReadDao();
        readDao.lookupsByCode.put("LEDGER_SCOPE", lookup("LEDGER_SCOPE", "PENDING", null, null, LocalDate.parse("2026-08-02")));
        readDao.valueCountsByLookup.put("LEDGER_SCOPE", 2L);
        readDao.staleCountsByLookup.put("LEDGER_SCOPE", 0L);
        FailingFilterLookupRegistrationWriteDao writeDao = new FailingFilterLookupRegistrationWriteDao(harness);
        FilterLookupCertificationServiceImpl service = new FilterLookupCertificationServiceImpl(
                readDao,
                writeDao,
                new RecordingGovernancePolicyPresetReadDao(policyPreset("90")),
                new RecordingFilterLookupPolicyClient(new FilterLookupPolicyDecisionDto(true, null, null)),
                new RecordingTransactionOperations(harness)
        );

        FilterLookupCertificationServiceException exception = assertThrows(
                FilterLookupCertificationServiceException.class,
                () -> service.certifyLookup("LEDGER_SCOPE", new FilterLookupCertificationRequestDto("client-a", "certifier"))
        );

        assertTrue(exception.getMessage().contains("Unable to certify filter lookup"));
        assertTrue(harness.rolledBack);
        assertEquals(0, writeDao.committedCertifiedLookups.size());
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

    private static SemanticFilterLookupRecord lookup(String lookupCode,
                                                     String healthStatusCode,
                                                     OffsetDateTime lastCertifiedTs,
                                                     String lastCertifiedBy,
                                                     LocalDate nextReviewDueDate) {
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
                healthStatusCode,
                lastCertifiedTs,
                lastCertifiedBy,
                nextReviewDueDate,
                "ACTIVE",
                OffsetDateTime.parse("2026-06-16T10:15:30Z"),
                "producer",
                OffsetDateTime.parse("2026-06-17T10:15:30Z"),
                "platform"
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

    private static final class RecordingFilterLookupReadDao implements FilterLookupReadDao {

        private final Map<String, SemanticFilterLookupRecord> lookupsByCode = new HashMap<>();
        private final Map<String, Long> staleCountsByLookup = new HashMap<>();
        private final Map<String, Long> valueCountsByLookup = new HashMap<>();

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
        public long countStaleValues(String clientId, String lookupCode) {
            return staleCountsByLookup.getOrDefault(lookupCode, 0L);
        }

        @Override
        public long countValues(String clientId, String lookupCode) {
            return valueCountsByLookup.getOrDefault(lookupCode, 0L);
        }
    }

    private static final class RecordingFilterLookupPolicyClient implements FilterLookupPolicyClient {

        private final FilterLookupPolicyDecisionDto certificationDecision;
        private final List<FilterLookupCertificationPolicyRequestDto> certificationRequests = new ArrayList<>();

        private RecordingFilterLookupPolicyClient(FilterLookupPolicyDecisionDto certificationDecision) {
            this.certificationDecision = certificationDecision;
        }

        @Override
        public FilterLookupPolicyDecisionDto validateReviewPeriodFloor(com.lextr.semanticlayer.dto.FilterLookupPolicyRequestDto request) {
            return new FilterLookupPolicyDecisionDto(true, null, null);
        }

        @Override
        public FilterLookupPolicyDecisionDto validateCertification(FilterLookupCertificationPolicyRequestDto request) {
            certificationRequests.add(request);
            return certificationDecision;
        }
    }

    private static class RecordingFilterLookupRegistrationWriteDao implements FilterLookupRegistrationWriteDao {

        private final TransactionHarness harness;
        private FilterLookupCertificationWriteRequest certificationRequest;
        private FilterLookupMetadataChangeHistoryWriteRequest metadataChangeHistoryRequest;
        protected final List<SemanticFilterLookupRecord> committedCertifiedLookups = new ArrayList<>();
        protected final List<FilterLookupMetadataChangeHistoryRecord> committedMetadataChanges = new ArrayList<>();

        private RecordingFilterLookupRegistrationWriteDao(TransactionHarness harness) {
            this.harness = harness;
        }

        @Override
        public SemanticFilterLookupRecord insertLookup(SemanticFilterLookupWriteRequest request) {
            throw new UnsupportedOperationException("Not used in certification tests");
        }

        @Override
        public SemanticFilterLookupRecord certifyLookup(FilterLookupCertificationWriteRequest request) {
            certificationRequest = request;
            SemanticFilterLookupRecord record = lookup(
                    request.lookup_cd(),
                    request.health_status_cd(),
                    request.last_certified_ts(),
                    request.last_certified_by(),
                    request.next_review_due_dt()
            );
            harness.addCertifiedLookup(record, committedCertifiedLookups);
            return record;
        }

        @Override
        public com.lextr.semanticlayer.model.FilterLookupWorkflowTaskRecord insertWorkflowTask(
                com.lextr.semanticlayer.model.FilterLookupWorkflowTaskWriteRequest request
        ) {
            throw new UnsupportedOperationException("Not used in certification tests");
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
        private final List<SemanticFilterLookupRecord> pendingCertifiedLookups = new ArrayList<>();
        private final List<FilterLookupMetadataChangeHistoryRecord> pendingMetadataChanges = new ArrayList<>();
        private List<SemanticFilterLookupRecord> certifiedLookupSink;
        private List<FilterLookupMetadataChangeHistoryRecord> metadataChangeSink;

        private void begin() {
            committed = false;
            rolledBack = false;
            pendingCertifiedLookups.clear();
            pendingMetadataChanges.clear();
        }

        private void commit() {
            if (certifiedLookupSink != null) {
                certifiedLookupSink.addAll(pendingCertifiedLookups);
            }
            if (metadataChangeSink != null) {
                metadataChangeSink.addAll(pendingMetadataChanges);
            }
            committed = true;
        }

        private void rollback() {
            pendingCertifiedLookups.clear();
            pendingMetadataChanges.clear();
            rolledBack = true;
        }

        private void addCertifiedLookup(SemanticFilterLookupRecord record, List<SemanticFilterLookupRecord> sink) {
            certifiedLookupSink = sink;
            pendingCertifiedLookups.add(record);
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
