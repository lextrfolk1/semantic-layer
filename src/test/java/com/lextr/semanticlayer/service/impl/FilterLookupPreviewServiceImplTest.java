package com.lextr.semanticlayer.service.impl;

import com.lextr.semanticlayer.dao.FilterLookupExecutionLogWriteDao;
import com.lextr.semanticlayer.dao.FilterLookupReadDao;
import com.lextr.semanticlayer.dto.FilterLookupPolicyDecisionDto;
import com.lextr.semanticlayer.dto.FilterLookupPreviewPolicyRequestDto;
import com.lextr.semanticlayer.dto.FilterLookupPreviewRequestDto;
import com.lextr.semanticlayer.dto.FilterLookupPreviewResponseDto;
import com.lextr.semanticlayer.dto.FilterLookupPreviewValueDto;
import com.lextr.semanticlayer.exception.FilterLookupPreviewServiceException;
import com.lextr.semanticlayer.exception.PolicyViolationException;
import com.lextr.semanticlayer.model.FilterLookupExecutionLogRecord;
import com.lextr.semanticlayer.model.FilterLookupExecutionLogWriteRequest;
import com.lextr.semanticlayer.model.FilterLookupPreviewValueRecord;
import com.lextr.semanticlayer.model.SemanticFilterLookupRecord;
import com.lextr.semanticlayer.service.FilterLookupPolicyClient;
import com.lextr.semanticlayer.service.FilterLookupSqlPreviewClient;
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

class FilterLookupPreviewServiceImplTest {

    @Test
    void previewsManualLookupsAtomicallyAndWritesAuditRows() {
        TransactionHarness harness = new TransactionHarness();
        RecordingFilterLookupReadDao readDao = new RecordingFilterLookupReadDao();
        readDao.lookupsByCode.put("LEDGER_SCOPE", manualLookup("LEDGER_SCOPE"));
        readDao.lookupsByCode.put("OPEN_PERIODS", manualLookup("OPEN_PERIODS"));
        readDao.valuesByLookup.put("LEDGER_SCOPE", List.of(previewValue("LEDGER_100"), previewValue("LEDGER_200")));
        readDao.valuesByLookup.put("OPEN_PERIODS", List.of(previewValue("2026-Q1")));
        RecordingFilterLookupExecutionLogWriteDao executionLogWriteDao =
                new RecordingFilterLookupExecutionLogWriteDao(harness);
        RecordingFilterLookupPolicyClient policyClient =
                new RecordingFilterLookupPolicyClient(new FilterLookupPolicyDecisionDto(true, null, null));
        FilterLookupPreviewServiceImpl service = new FilterLookupPreviewServiceImpl(
                readDao,
                executionLogWriteDao,
                policyClient,
                new NoOpFilterLookupSqlPreviewClient(),
                new RecordingTransactionOperations(harness)
        );

        List<FilterLookupPreviewResponseDto> response = service.previewLookups(new FilterLookupPreviewRequestDto(
                "client-a",
                "preview-user",
                List.of("LEDGER_SCOPE", "OPEN_PERIODS")
        ));

        assertTrue(harness.committed);
        assertEquals(2, response.size());
        assertEquals("LEDGER_SCOPE", response.get(0).lookup_cd());
        assertEquals(2, response.get(0).phase1_row_count());
        assertEquals("LEDGER_100", response.get(0).values().get(0).value_cd());
        assertEquals("OPEN_PERIODS", response.get(1).lookup_cd());
        assertEquals(1, response.get(1).phase1_row_count());

        assertEquals(2, executionLogWriteDao.committedExecutionLogs.size());
        assertEquals("SUCCESS", executionLogWriteDao.committedExecutionLogs.get(0).result_status_cd());
        assertEquals("preview-user", executionLogWriteDao.committedExecutionLogs.get(0).executed_by());
        assertEquals(Integer.valueOf(2), executionLogWriteDao.committedExecutionLogs.get(0).phase1_row_count());
        assertEquals("IN_LIST", executionLogWriteDao.committedExecutionLogs.get(0).execution_strategy_used_cd());

        assertEquals(2, policyClient.previewRequests.size());
        assertEquals("LEDGER_SCOPE", policyClient.previewRequests.get(0).lookup_cd());
        assertEquals("MANUAL_LIST", policyClient.previewRequests.get(0).construction_type_cd());
    }

    @Test
    void recordsBlockedAuditLogAndThrowsPolicyViolation() {
        TransactionHarness harness = new TransactionHarness();
        RecordingFilterLookupReadDao readDao = new RecordingFilterLookupReadDao();
        readDao.lookupsByCode.put("LEDGER_SCOPE", manualLookup("LEDGER_SCOPE"));
        RecordingFilterLookupExecutionLogWriteDao executionLogWriteDao =
                new RecordingFilterLookupExecutionLogWriteDao(harness);
        RecordingFilterLookupPolicyClient policyClient =
                new RecordingFilterLookupPolicyClient(new FilterLookupPolicyDecisionDto(
                        false,
                        "GOV-FL-004",
                        "Anticipated values require approval before preview"
                ));
        FilterLookupPreviewServiceImpl service = new FilterLookupPreviewServiceImpl(
                readDao,
                executionLogWriteDao,
                policyClient,
                new NoOpFilterLookupSqlPreviewClient(),
                new RecordingTransactionOperations(harness)
        );

        PolicyViolationException exception = assertThrows(
                PolicyViolationException.class,
                () -> service.previewLookups(new FilterLookupPreviewRequestDto(
                        "client-a",
                        "preview-user",
                        List.of("LEDGER_SCOPE")
                ))
        );

        assertEquals("GOV-FL-004", exception.code());
        assertTrue(!harness.committed);
        assertEquals(1, executionLogWriteDao.committedExecutionLogs.size());
        assertEquals("BLOCKED", executionLogWriteDao.committedExecutionLogs.get(0).result_status_cd());
        assertEquals("GOV-FL-004", executionLogWriteDao.committedExecutionLogs.get(0).blocked_by_policy_cd());
        assertEquals(0, executionLogWriteDao.pendingExecutionLogs.size());
    }

    @Test
    void wrapsFailuresAndRollsBackExecutionLogs() {
        TransactionHarness harness = new TransactionHarness();
        RecordingFilterLookupReadDao readDao = new RecordingFilterLookupReadDao();
        readDao.lookupsByCode.put("LEDGER_SCOPE", manualLookup("LEDGER_SCOPE"));
        readDao.lookupsByCode.put("OPEN_PERIODS", manualLookup("OPEN_PERIODS"));
        readDao.valuesByLookup.put("LEDGER_SCOPE", List.of(previewValue("LEDGER_100")));
        readDao.valuesByLookup.put("OPEN_PERIODS", List.of(previewValue("2026-Q1")));
        FailingFilterLookupExecutionLogWriteDao executionLogWriteDao =
                new FailingFilterLookupExecutionLogWriteDao(harness, 2);
        FilterLookupPreviewServiceImpl service = new FilterLookupPreviewServiceImpl(
                readDao,
                executionLogWriteDao,
                new RecordingFilterLookupPolicyClient(new FilterLookupPolicyDecisionDto(true, null, null)),
                new NoOpFilterLookupSqlPreviewClient(),
                new RecordingTransactionOperations(harness)
        );

        FilterLookupPreviewServiceException exception = assertThrows(
                FilterLookupPreviewServiceException.class,
                () -> service.previewLookups(new FilterLookupPreviewRequestDto(
                        "client-a",
                        "preview-user",
                        List.of("LEDGER_SCOPE", "OPEN_PERIODS")
                ))
        );

        assertTrue(exception.getMessage().contains("Unable to preview filter lookups"));
        assertTrue(harness.rolledBack);
        assertEquals(0, executionLogWriteDao.committedExecutionLogs.size());
    }

    private static SemanticFilterLookupRecord manualLookup(String lookupCode) {
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
                null,
                null,
                LocalDate.parse("2026-08-02"),
                "ACTIVE",
                OffsetDateTime.parse("2026-06-16T10:15:30Z"),
                "producer",
                OffsetDateTime.parse("2026-06-17T10:15:30Z"),
                "platform"
        );
    }

    private static FilterLookupPreviewValueRecord previewValue(String valueCode) {
        return new FilterLookupPreviewValueRecord(
                "LEDGER_SCOPE",
                "client-a",
                valueCode,
                "Value " + valueCode,
                "ACTIVE",
                true,
                LocalDate.parse("2026-07-01"),
                "WKFL-100",
                OffsetDateTime.parse("2026-06-18T10:15:30Z"),
                14,
                "Pending activation",
                "producer",
                OffsetDateTime.parse("2026-06-18T09:15:30Z"),
                "reviewer",
                OffsetDateTime.parse("2026-06-18T11:15:30Z"),
                OffsetDateTime.parse("2026-06-18T12:15:30Z")
        );
    }

    private static final class RecordingFilterLookupReadDao implements FilterLookupReadDao {

        private final Map<String, SemanticFilterLookupRecord> lookupsByCode = new HashMap<>();
        private final Map<String, List<FilterLookupPreviewValueRecord>> valuesByLookup = new HashMap<>();

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
        public List<FilterLookupPreviewValueRecord> findManualValues(String clientId, String lookupCode) {
            return valuesByLookup.getOrDefault(lookupCode, List.of());
        }

        @Override
        public long countValues(String clientId, String lookupCode) {
            return 0;
        }
    }

    private static class RecordingFilterLookupExecutionLogWriteDao implements FilterLookupExecutionLogWriteDao {

        private final TransactionHarness harness;
        protected final List<FilterLookupExecutionLogRecord> committedExecutionLogs = new ArrayList<>();
        protected final List<FilterLookupExecutionLogRecord> pendingExecutionLogs = new ArrayList<>();
        private int sequence = 1;

        private RecordingFilterLookupExecutionLogWriteDao(TransactionHarness harness) {
            this.harness = harness;
            harness.executionLogWriteDao = this;
        }

        @Override
        public FilterLookupExecutionLogRecord insertExecutionLog(FilterLookupExecutionLogWriteRequest request) {
            FilterLookupExecutionLogRecord record = new FilterLookupExecutionLogRecord(
                    (long) sequence++,
                    request.lookup_cd(),
                    request.executed_by(),
                    request.executed_ts(),
                    request.phase1_duration_ms(),
                    request.phase1_row_count(),
                    request.phase1_cache_hit_flg(),
                    request.execution_strategy_used_cd(),
                    request.phase2_duration_ms(),
                    request.result_status_cd(),
                    request.error_txt(),
                    request.blocked_by_policy_cd()
            );
            if (harness.inTransaction) {
                pendingExecutionLogs.add(record);
            } else {
                committedExecutionLogs.add(record);
            }
            return record;
        }
    }

    private static final class FailingFilterLookupExecutionLogWriteDao extends RecordingFilterLookupExecutionLogWriteDao {

        private final int failOnCall;
        private int callCount;

        private FailingFilterLookupExecutionLogWriteDao(TransactionHarness harness, int failOnCall) {
            super(harness);
            this.failOnCall = failOnCall;
        }

        @Override
        public FilterLookupExecutionLogRecord insertExecutionLog(FilterLookupExecutionLogWriteRequest request) {
            callCount++;
            if (callCount == failOnCall) {
                throw new IllegalStateException("execution log write failed");
            }
            return super.insertExecutionLog(request);
        }
    }

    private static final class RecordingFilterLookupPolicyClient implements FilterLookupPolicyClient {

        private final FilterLookupPolicyDecisionDto previewDecision;
        private final List<FilterLookupPreviewPolicyRequestDto> previewRequests = new ArrayList<>();

        private RecordingFilterLookupPolicyClient(FilterLookupPolicyDecisionDto previewDecision) {
            this.previewDecision = previewDecision;
        }

        @Override
        public FilterLookupPolicyDecisionDto validateReviewPeriodFloor(com.lextr.semanticlayer.dto.FilterLookupPolicyRequestDto request) {
            return new FilterLookupPolicyDecisionDto(true, null, null);
        }

        @Override
        public FilterLookupPolicyDecisionDto validatePreviewExecution(FilterLookupPreviewPolicyRequestDto request) {
            previewRequests.add(request);
            return previewDecision;
        }
    }

    private static final class NoOpFilterLookupSqlPreviewClient implements FilterLookupSqlPreviewClient {

        @Override
        public List<FilterLookupPreviewValueDto> previewDistinctValues(String clientId, SemanticFilterLookupRecord lookup) {
            return List.of();
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

        private boolean inTransaction;
        private boolean committed;
        private boolean rolledBack;
        private RecordingFilterLookupExecutionLogWriteDao executionLogWriteDao;

        private void begin() {
            inTransaction = true;
            committed = false;
            rolledBack = false;
            if (executionLogWriteDao != null) {
                executionLogWriteDao.pendingExecutionLogs.clear();
            }
        }

        private void commit() {
            if (executionLogWriteDao != null) {
                executionLogWriteDao.committedExecutionLogs.addAll(executionLogWriteDao.pendingExecutionLogs);
                executionLogWriteDao.pendingExecutionLogs.clear();
            }
            inTransaction = false;
            committed = true;
        }

        private void rollback() {
            if (executionLogWriteDao != null) {
                executionLogWriteDao.pendingExecutionLogs.clear();
            }
            inTransaction = false;
            rolledBack = true;
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
