package com.lextr.semanticlayer.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lextr.semanticlayer.dao.RuleResultDao;
import com.lextr.semanticlayer.dto.DqRuleAttributeDto;
import com.lextr.semanticlayer.dto.DqRuleCatalogDto;
import com.lextr.semanticlayer.dto.DqRuleResultDto;
import com.lextr.semanticlayer.dto.DqRuleResultIngestRequestDto;
import com.lextr.semanticlayer.dto.ExternalRuleResultIngestRequestDto;
import com.lextr.semanticlayer.dto.RuleResultIngestResponseDto;
import com.lextr.semanticlayer.dto.RuleResultPolicyDecisionDto;
import com.lextr.semanticlayer.dto.WorkflowTaskResponseDto;
import com.lextr.semanticlayer.exception.PolicyViolationException;
import com.lextr.semanticlayer.model.ExternalRuleResultRecord;
import com.lextr.semanticlayer.model.ExternalRuleResultWriteRequest;
import com.lextr.semanticlayer.model.ObjectExposureAccessAuditWriteRequest;
import com.lextr.semanticlayer.service.DqRuleService;
import com.lextr.semanticlayer.service.RuleResultPolicyClient;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuleResultServiceImplTest {

    @Test
    void rejectsRuleResultIngestForNonEnginePrincipal() {
        RecordingRuleResultDao dao = new RecordingRuleResultDao();
        RecordingDqRuleService dqRuleService = new RecordingDqRuleService();
        RuleResultServiceImpl service = new RuleResultServiceImpl(
                dao,
                new RecordingRuleResultPolicyClient(new RuleResultPolicyDecisionDto(false, "POL-RR-001", "Rule result ingest denied for non-engine principal ANALYST")),
                dqRuleService,
                new RecordingTransactionOperations(),
                new ObjectMapper()
        );

        PolicyViolationException exception = assertThrows(
                PolicyViolationException.class,
                () -> service.ingestRuleResult(
                        request("VALUE"),
                        "ANALYST"
                )
        );

        assertEquals("POL-RR-001", exception.code());
        assertTrue(dao.insertedResults.isEmpty());
        assertTrue(dao.insertedAudits.isEmpty());
        assertTrue(dqRuleService.requests.isEmpty());
    }

    @Test
    void routesEditcheckToDqRuleServiceAndWritesAuditRow() throws Exception {
        RecordingRuleResultDao dao = new RecordingRuleResultDao();
        dao.insertedResult = new ExternalRuleResultRecord(
                401L,
                "client-a",
                101L,
                "RULE-100",
                "VALUE",
                "{\"verdict\":\"PASS\"}",
                OffsetDateTime.parse("2026-06-18T10:15:30Z"),
                OffsetDateTime.parse("2026-06-18T10:15:30Z"),
                "engine",
                OffsetDateTime.parse("2026-06-18T10:15:30Z"),
                "engine"
        );
        RecordingDqRuleService dqRuleService = new RecordingDqRuleService();
        dqRuleService.response = new DqRuleResultDto(
                900L,
                "LEDGER_COMPLETENESS",
                "ledger_id",
                "client-a",
                "123",
                "123",
                "PASS",
                null,
                OffsetDateTime.parse("2026-06-18T10:15:30Z"),
                OffsetDateTime.parse("2026-06-18T10:15:30Z"),
                "ENGINE",
                OffsetDateTime.parse("2026-06-18T10:15:30Z"),
                "ENGINE"
        );
        RuleResultServiceImpl service = new RuleResultServiceImpl(
                dao,
                new RecordingRuleResultPolicyClient(new RuleResultPolicyDecisionDto(true, null, null)),
                dqRuleService,
                new RecordingTransactionOperations(),
                new ObjectMapper()
        );

        RuleResultIngestResponseDto response = service.ingestRuleResult(request("EDITCHECK"), "ENGINE");

        assertEquals("LP-24", response.route_target_cd());
        assertEquals(Long.valueOf(900L), response.dq_result_id());
        assertEquals(1, dqRuleService.requests.size());
        assertEquals("LEDGER_COMPLETENESS", dqRuleService.requests.get(0).rule_cd());
        assertEquals("ledger_id", dqRuleService.requests.get(0).logical_attribute_cd());
        assertEquals("ENGINE", dqRuleService.principalCds.get(0));
        assertTrue(dao.insertedResults.isEmpty());
        assertEquals(1, dao.insertedAudits.size());
        assertEquals("ROUTED_EDITCHECK", dao.insertedAudits.get(0).change_type_cd());
        assertEquals("101:RULE-100", dao.insertedAudits.get(0).entity_ref());
    }

    @Test
    void ingestsNonEditcheckRuleResultAndWritesAuditRow() throws Exception {
        RecordingRuleResultDao dao = new RecordingRuleResultDao();
        dao.insertedResult = new ExternalRuleResultRecord(
                501L,
                "client-a",
                101L,
                "RULE-100",
                "VALUE",
                "{\"verdict\":\"PASS\"}",
                OffsetDateTime.parse("2026-06-18T10:15:30Z"),
                OffsetDateTime.parse("2026-06-18T10:15:30Z"),
                "ENGINE",
                OffsetDateTime.parse("2026-06-18T10:15:30Z"),
                "ENGINE"
        );
        RuleResultServiceImpl service = new RuleResultServiceImpl(
                dao,
                new RecordingRuleResultPolicyClient(new RuleResultPolicyDecisionDto(true, null, null)),
                new RecordingDqRuleService(),
                new RecordingTransactionOperations(),
                new ObjectMapper()
        );

        RuleResultIngestResponseDto response = service.ingestRuleResult(request("VALUE"), "ENGINE");

        assertEquals(Long.valueOf(501L), response.external_rule_result_id());
        assertEquals("SELF", response.route_target_cd());
        assertEquals(1, dao.insertedResults.size());
        assertEquals(1, dao.insertedAudits.size());
        assertEquals("INGESTED", dao.insertedAudits.get(0).change_type_cd());
        assertEquals("101:RULE-100", dao.insertedAudits.get(0).entity_ref());
        assertTrue(dao.insertedResults.get(0).output_payload_jsonb().contains("\"verdict\":\"PASS\""));
        assertTrue(response.output_payload_jsonb().path("verdict").isTextual());
    }

    private static ExternalRuleResultIngestRequestDto request(String outputKindCd) throws Exception {
        return new ExternalRuleResultIngestRequestDto(
                "client-a",
                101L,
                "RULE-100",
                outputKindCd,
                new ObjectMapper().readTree("""
                        {
                          "rule_cd": "LEDGER_COMPLETENESS",
                          "logical_attribute_cd": "ledger_id",
                          "observed_value_txt": "123",
                          "expected_value_txt": "123",
                          "result_status_cd": "PASS",
                          "result_reason_txt": "ok",
                          "observed_ts": "2026-06-18T10:15:30Z",
                          "verdict": "PASS"
                        }
                        """),
                OffsetDateTime.parse("2026-06-18T10:15:30Z")
        );
    }

    private static final class RecordingRuleResultPolicyClient implements RuleResultPolicyClient {

        private final RuleResultPolicyDecisionDto decision;

        private RecordingRuleResultPolicyClient(RuleResultPolicyDecisionDto decision) {
            this.decision = decision;
        }

        @Override
        public RuleResultPolicyDecisionDto validateIngest(ExternalRuleResultIngestRequestDto request, String principalCd) {
            return decision;
        }
    }

    private static final class RecordingRuleResultDao implements RuleResultDao {

        private final List<ExternalRuleResultWriteRequest> insertedResults = new ArrayList<>();
        private final List<ObjectExposureAccessAuditWriteRequest> insertedAudits = new ArrayList<>();
        private ExternalRuleResultRecord insertedResult;

        @Override
        public ExternalRuleResultRecord insertResult(ExternalRuleResultWriteRequest request) {
            insertedResults.add(request);
            return insertedResult;
        }

        @Override
        public void insertMetadataChangeHistory(ObjectExposureAccessAuditWriteRequest request) {
            insertedAudits.add(request);
        }
    }

    private static final class RecordingDqRuleService implements DqRuleService {

        private final List<DqRuleResultIngestRequestDto> requests = new ArrayList<>();
        private final List<String> principalCds = new ArrayList<>();
        private DqRuleResultDto response;

        @Override
        public List<DqRuleCatalogDto> findRules(String clientId, String ruleDimensionCode, String lifecycleStatusCode) {
            throw new UnsupportedOperationException();
        }

        @Override
        public DqRuleCatalogDto findRule(String clientId, String ruleCode) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<DqRuleAttributeDto> findRuleAttributes(String clientId, String ruleCode) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<DqRuleResultDto> findRuleResults(String clientId, String logicalAttributeCode) {
            throw new UnsupportedOperationException();
        }

        @Override
        public DqRuleResultDto ingestResult(DqRuleResultIngestRequestDto request, String principalCd) {
            requests.add(request);
            principalCds.add(principalCd);
            return response;
        }

        @Override
        public List<WorkflowTaskResponseDto> requestRules(com.lextr.semanticlayer.dto.DqRuleRequestDto request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public WorkflowTaskResponseDto findRequest(String clientId, UUID workflowTaskId) {
            throw new UnsupportedOperationException();
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
