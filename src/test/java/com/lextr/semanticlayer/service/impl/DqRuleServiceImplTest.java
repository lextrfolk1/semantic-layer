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
import com.lextr.semanticlayer.model.DqRuleAttributeRecord;
import com.lextr.semanticlayer.model.DqRuleCatalogRecord;
import com.lextr.semanticlayer.model.DqRuleRequestWorkflowTaskRecord;
import com.lextr.semanticlayer.model.DqRuleRequestWorkflowTaskWriteRequest;
import com.lextr.semanticlayer.model.DqRuleResultRecord;
import com.lextr.semanticlayer.model.DqRuleResultWriteRequest;
import com.lextr.semanticlayer.model.MetadataChangeHistoryRecord;
import com.lextr.semanticlayer.model.MetadataChangeHistoryWriteRequest;
import com.lextr.semanticlayer.service.DqRulePolicyClient;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DqRuleServiceImplTest {

    @Test
    void requestsDqRulesAtomicallyAndWritesAuditRow() {
        TransactionHarness harness = new TransactionHarness();
        RecordingDqRuleDao dao = new RecordingDqRuleDao(harness);
        dao.catalogRecord = ruleRecord("LEDGER_COMPLETENESS", "ledger_id");
        dao.attributeRecords = List.of(
                attributeRecord("ledger_id"),
                attributeRecord("ledger_status_cd")
        );
        dao.resultRecords = List.of(resultRecord("ledger_id", "PASS"));
        DqRuleServiceImpl service = new DqRuleServiceImpl(
                dao,
                new RecordingDqRulePolicyClient(new DqRulePolicyDecisionDto(true, null, null), new DqRulePolicyDecisionDto(true, null, null)),
                new RecordingTransactionOperations(harness)
        );

        List<WorkflowTaskResponseDto> response = service.requestRules(new DqRuleRequestDto(
                "client-a",
                List.of("LEDGER_COMPLETENESS"),
                "steward",
                "Please request DQ review"
        ));

        assertTrue(harness.committed);
        assertEquals(1, response.size());
        assertEquals("REQUESTED", response.get(0).task_status_cd());
        assertEquals("LEDGER_COMPLETENESS", response.get(0).entity_ref());
        assertEquals(1, dao.insertedWorkflowTasks.size());
        assertEquals(1, dao.insertedMetadataChanges.size());
        assertEquals("REQUESTED", dao.insertedWorkflowTasks.get(0).task_status_cd());
        assertTrue(dao.insertedMetadataChanges.get(0).change_summary_txt().contains("coverage=50%"));
    }

    @Test
    void rejectsResultIngestForNonEnginePrincipal() {
        TransactionHarness harness = new TransactionHarness();
        RecordingDqRuleDao dao = new RecordingDqRuleDao(harness);
        dao.catalogRecord = ruleRecord("LEDGER_COMPLETENESS", "ledger_id");
        DqRuleServiceImpl service = new DqRuleServiceImpl(
                dao,
                new RecordingDqRulePolicyClient(
                        new DqRulePolicyDecisionDto(true, null, null),
                        new DqRulePolicyDecisionDto(false, "POL-DQ-001", "Result ingest is restricted to engine principals")
                ),
                new RecordingTransactionOperations(harness)
        );

        PolicyViolationException exception = assertThrows(
                PolicyViolationException.class,
                () -> service.ingestResult(
                        new DqRuleResultIngestRequestDto(
                                "client-a",
                                "LEDGER_COMPLETENESS",
                                "ledger_id",
                                "123",
                                "123",
                                "PASS",
                                null,
                                OffsetDateTime.parse("2026-06-26T10:00:00Z"),
                                "sensor"
                        ),
                        "human-operator"
                )
        );

        assertEquals("POL-DQ-001", exception.code());
        assertEquals(0, dao.insertedResults.size());
        assertEquals(0, dao.insertedMetadataChanges.size());
    }

    @Test
    void computesMatrixCoverageFromAttributesAndResults() {
        RecordingDqRuleDao dao = new RecordingDqRuleDao(new TransactionHarness());
        dao.catalogRecord = ruleRecord("LEDGER_COMPLETENESS", "ledger_id");
        dao.attributeRecords = List.of(
                attributeRecord("ledger_id"),
                attributeRecord("ledger_status_cd"),
                attributeRecord("ledger_type_cd"),
                attributeRecord("posted_dt")
        );
        dao.resultRecords = List.of(
                resultRecord("ledger_id", "PASS"),
                resultRecord("ledger_id", "PASS")
        );
        DqRuleServiceImpl service = new DqRuleServiceImpl(
                dao,
                new RecordingDqRulePolicyClient(new DqRulePolicyDecisionDto(true, null, null), new DqRulePolicyDecisionDto(true, null, null)),
                new RecordingTransactionOperations(new TransactionHarness())
        );

        DqRuleMatrixCoverageDto coverage = service.computeMatrixCoverage("client-a", "LEDGER_COMPLETENESS");

        assertEquals(4, coverage.attribute_count());
        assertEquals(2, coverage.result_count());
        assertEquals(50, coverage.coverage_pct());
        assertFalse(coverage.fully_covered_flg());
    }

    @Test
    void ingestsResultAndWritesAuditRow() {
        TransactionHarness harness = new TransactionHarness();
        RecordingDqRuleDao dao = new RecordingDqRuleDao(harness);
        dao.catalogRecord = ruleRecord("LEDGER_COMPLETENESS", "ledger_id");
        dao.attributeRecords = List.of(attributeRecord("ledger_id"), attributeRecord("ledger_status_cd"));
        dao.resultRecords = List.of(resultRecord("ledger_id", "PASS"));
        DqRuleServiceImpl service = new DqRuleServiceImpl(
                dao,
                new RecordingDqRulePolicyClient(new DqRulePolicyDecisionDto(true, null, null), new DqRulePolicyDecisionDto(true, null, null)),
                new RecordingTransactionOperations(harness)
        );

        DqRuleResultDto response = service.ingestResult(
                new DqRuleResultIngestRequestDto(
                        "client-a",
                        "LEDGER_COMPLETENESS",
                        "ledger_id",
                        "123",
                        "123",
                        "PASS",
                        "sensor saw expected value",
                        OffsetDateTime.parse("2026-06-26T10:00:00Z"),
                        "sensor"
                ),
                "engine-principal"
        );

        assertTrue(harness.committed);
        assertEquals("LEDGER_COMPLETENESS", response.rule_cd());
        assertEquals(1, dao.insertedResults.size());
        assertEquals(1, dao.insertedMetadataChanges.size());
        assertTrue(dao.insertedMetadataChanges.get(0).change_summary_txt().contains("coverage=50%"));
    }

    private static DqRuleCatalogRecord ruleRecord(String ruleCode, String logicalAttributeCode) {
        return new DqRuleCatalogRecord(
                1L,
                ruleCode,
                "Ledger Completeness",
                "COMPLETENESS",
                logicalAttributeCode,
                "RULESET",
                "ledger_id is present",
                "HIGH",
                "ACTIVE",
                "client-a",
                OffsetDateTime.parse("2026-06-01T10:15:30Z"),
                "producer",
                OffsetDateTime.parse("2026-06-02T10:15:30Z"),
                "reviewer"
        );
    }

    private static DqRuleAttributeRecord attributeRecord(String attributeCode) {
        return new DqRuleAttributeRecord(
                10L,
                "LEDGER_COMPLETENESS",
                attributeCode,
                "SOURCE",
                "client-a",
                OffsetDateTime.parse("2026-06-01T10:15:30Z"),
                "producer",
                OffsetDateTime.parse("2026-06-02T10:15:30Z"),
                "reviewer"
        );
    }

    private static DqRuleResultRecord resultRecord(String logicalAttributeCode, String status) {
        return new DqRuleResultRecord(
                100L,
                "LEDGER_COMPLETENESS",
                logicalAttributeCode,
                "client-a",
                "123",
                "123",
                status,
                null,
                OffsetDateTime.parse("2026-06-26T10:00:00Z"),
                OffsetDateTime.parse("2026-06-26T10:00:00Z"),
                "sensor",
                OffsetDateTime.parse("2026-06-26T10:00:01Z"),
                "sensor"
        );
    }

    private static final class RecordingDqRulePolicyClient implements DqRulePolicyClient {

        private final DqRulePolicyDecisionDto requestDecision;
        private final DqRulePolicyDecisionDto resultDecision;

        private RecordingDqRulePolicyClient(DqRulePolicyDecisionDto requestDecision,
                                            DqRulePolicyDecisionDto resultDecision) {
            this.requestDecision = requestDecision;
            this.resultDecision = resultDecision;
        }

        @Override
        public DqRulePolicyDecisionDto validateRequest(DqRuleRequestDto request) {
            return requestDecision;
        }

        @Override
        public DqRulePolicyDecisionDto validateResultIngest(DqRuleResultIngestRequestDto request, String principalCd) {
            return resultDecision;
        }
    }

    private static final class RecordingDqRuleDao implements DqRuleDao {

        private final TransactionHarness harness;
        private DqRuleCatalogRecord catalogRecord;
        private List<DqRuleAttributeRecord> attributeRecords = new ArrayList<>();
        private List<DqRuleResultRecord> resultRecords = new ArrayList<>();
        private final List<DqRuleRequestWorkflowTaskWriteRequest> insertedWorkflowTasks = new ArrayList<>();
        private final List<DqRuleResultWriteRequest> insertedResults = new ArrayList<>();
        private final List<MetadataChangeHistoryWriteRequest> insertedMetadataChanges = new ArrayList<>();

        private RecordingDqRuleDao(TransactionHarness harness) {
            this.harness = harness;
        }

        @Override
        public List<DqRuleCatalogRecord> findRules(String clientId, String ruleDimensionCode, String lifecycleStatusCode) {
            return catalogRecord == null ? List.of() : List.of(catalogRecord);
        }

        @Override
        public Optional<DqRuleCatalogRecord> findRule(String clientId, String ruleCode) {
            return Optional.ofNullable(catalogRecord);
        }

        @Override
        public Optional<DqRuleRequestWorkflowTaskRecord> findRequest(String clientId, UUID workflowTaskId) {
            return Optional.empty();
        }

        @Override
        public List<DqRuleAttributeRecord> findRuleAttributes(String clientId, String ruleCode) {
            return attributeRecords;
        }

        @Override
        public List<DqRuleResultRecord> findResultsByLogicalAttribute(String clientId, String logicalAttributeCode) {
            return resultRecords;
        }

        @Override
        public DqRuleRequestWorkflowTaskRecord insertWorkflowTask(DqRuleRequestWorkflowTaskWriteRequest request) {
            insertedWorkflowTasks.add(request);
            return new DqRuleRequestWorkflowTaskRecord(
                    201L + insertedWorkflowTasks.size(),
                    request.workflow_type_cd(),
                    request.entity_type_cd(),
                    request.rule_cd(),
                    request.task_status_cd(),
                    request.submitted_by(),
                    request.submitted_ts(),
                    request.assigned_to(),
                    request.due_dt(),
                    request.description_txt(),
                    request.client_id(),
                    null,
                    null,
                    null
            );
        }

        @Override
        public DqRuleResultRecord insertResult(DqRuleResultWriteRequest request) {
            insertedResults.add(request);
            return new DqRuleResultRecord(
                    301L + insertedResults.size(),
                    request.rule_cd(),
                    request.logical_attribute_cd(),
                    request.client_id(),
                    request.observed_value_txt(),
                    request.expected_value_txt(),
                    request.result_status_cd(),
                    request.result_reason_txt(),
                    request.observed_ts(),
                    request.created_ts(),
                    request.created_by(),
                    request.updated_ts(),
                    request.updated_by()
            );
        }

        @Override
        public MetadataChangeHistoryRecord insertMetadataChangeHistory(MetadataChangeHistoryWriteRequest request) {
            insertedMetadataChanges.add(request);
            return new MetadataChangeHistoryRecord(
                    request.change_history_id(),
                    request.client_id(),
                    request.entity_type_cd(),
                    request.entity_id(),
                    request.change_type_cd(),
                    request.change_summary_txt(),
                    request.created_ts(),
                    request.created_by()
            );
        }
    }

    private static final class RecordingTransactionOperations implements TransactionOperations {

        private final TransactionHarness harness;

        private RecordingTransactionOperations(TransactionHarness harness) {
            this.harness = harness;
        }

        @Override
        public <T> T execute(TransactionCallback<T> action) {
            T result = action.doInTransaction(new RecordingTransactionStatus());
            harness.committed = true;
            return result;
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

    private static final class TransactionHarness {
        private boolean committed;
    }
}
