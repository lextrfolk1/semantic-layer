package com.lextr.semanticlayer.service.impl;

import com.lextr.semanticlayer.dao.FilterLookupRegistrationWriteDao;
import com.lextr.semanticlayer.dao.WorkflowApprovalDao;
import com.lextr.semanticlayer.dto.WorkflowApprovalRequestDto;
import com.lextr.semanticlayer.dto.WorkflowTaskResponseDto;
import com.lextr.semanticlayer.dto.WorkflowPolicyDecisionDto;
import com.lextr.semanticlayer.dto.WorkflowPolicyRequestDto;
import com.lextr.semanticlayer.exception.PolicyViolationException;
import com.lextr.semanticlayer.exception.RegistryResourceNotFoundException;
import com.lextr.semanticlayer.exception.WorkflowApprovalServiceException;
import com.lextr.semanticlayer.exception.WorkflowTaskAlreadyApprovedException;
import com.lextr.semanticlayer.model.FilterLookupWorkflowTaskRecord;
import com.lextr.semanticlayer.model.FilterLookupMetadataChangeHistoryRecord;
import com.lextr.semanticlayer.model.FilterLookupMetadataChangeHistoryWriteRequest;
import com.lextr.semanticlayer.service.WorkflowPolicyClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
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

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(OutputCaptureExtension.class)
class WorkflowApprovalServiceImplTest {

    @Test
    void approvesTaskAtomicallyAndWritesAudit() {
        TransactionHarness harness = new TransactionHarness();
        RecordingWorkflowApprovalDao dao = new RecordingWorkflowApprovalDao();
        OffsetDateTime submittedTs = OffsetDateTime.parse("2026-06-18T10:15:30Z");
        dao.tasks.put(301L, new FilterLookupWorkflowTaskRecord(
                301L,
                "FILTER_LOOKUP_REGISTRATION",
                "FILTER_LOOKUP",
                "LEDGER_SCOPE",
                "PENDING",
                "producer",
                submittedTs,
                null,
                LocalDate.parse("2026-09-16"),
                "Review filter lookup LEDGER_SCOPE",
                "client-a",
                null,
                null,
                null
        ));

        RecordingFilterLookupRegistrationWriteDao writeDao = new RecordingFilterLookupRegistrationWriteDao();
        RecordingWorkflowPolicyClient policyClient = new RecordingWorkflowPolicyClient(
                new WorkflowPolicyDecisionDto(true, null, null)
        );

        WorkflowApprovalServiceImpl service = new WorkflowApprovalServiceImpl(
                dao,
                writeDao,
                policyClient,
                new RecordingTransactionOperations(harness)
        );

        WorkflowTaskResponseDto response = service.approveTask(
                301L,
                new WorkflowApprovalRequestDto("client-a", "approver", "looks good")
        );

        assertTrue(harness.committed);
        assertEquals("APPROVED", response.task_status_cd());
        assertEquals("approver", response.approved_by());
        assertEquals("looks good", response.approval_note_txt());
        assertNotNull(response.approved_ts());

        // Side effect verified
        assertEquals("ACTIVE", dao.lookupStatus.get("LEDGER_SCOPE"));

        // Audit verified
        assertEquals(1, writeDao.metadataChanges.size());
        assertEquals("APPROVED", writeDao.metadataChanges.get(0).change_type_cd());
        assertEquals("LEDGER_SCOPE", writeDao.metadataChanges.get(0).entity_ref());

        // Policy checked
        assertEquals(1, policyClient.requests.size());
        assertEquals("approver", policyClient.requests.get(0).approved_by());
    }

    @Test
    void appliesOverrideSideEffectOnApproval() {
        TransactionHarness harness = new TransactionHarness();
        RecordingWorkflowApprovalDao dao = new RecordingWorkflowApprovalDao();
        dao.tasks.put(302L, new FilterLookupWorkflowTaskRecord(
                302L,
                "ATTRIBUTE_LOGICAL_NAME_OVERRIDE",
                "ATTRIBUTE",
                "402",
                "PENDING",
                "producer",
                OffsetDateTime.now(),
                null,
                null,
                "Review override",
                "client-a",
                null,
                null,
                null
        ));

        RecordingFilterLookupRegistrationWriteDao writeDao = new RecordingFilterLookupRegistrationWriteDao();
        WorkflowApprovalServiceImpl service = new WorkflowApprovalServiceImpl(
                dao,
                writeDao,
                new RecordingWorkflowPolicyClient(new WorkflowPolicyDecisionDto(true, null, null)),
                new RecordingTransactionOperations(harness)
        );

        service.approveTask(302L, new WorkflowApprovalRequestDto("client-a", "approver", "approved"));
        assertEquals("ACTIVE", dao.overrideStatus.get(402L));
    }

    @Test
    void logsWarningWhenOverrideEntityReferenceIsNotNumeric(CapturedOutput output) {
        TransactionHarness harness = new TransactionHarness();
        RecordingWorkflowApprovalDao dao = new RecordingWorkflowApprovalDao();
        dao.tasks.put(302L, new FilterLookupWorkflowTaskRecord(
                302L,
                "ATTRIBUTE_LOGICAL_NAME_OVERRIDE",
                "ATTRIBUTE",
                "not-a-number",
                "PENDING",
                "producer",
                OffsetDateTime.now(),
                null,
                null,
                "Review override",
                "client-a",
                null,
                null,
                null
        ));

        WorkflowApprovalServiceImpl service = new WorkflowApprovalServiceImpl(
                dao,
                new RecordingFilterLookupRegistrationWriteDao(),
                new RecordingWorkflowPolicyClient(new WorkflowPolicyDecisionDto(true, null, null)),
                new RecordingTransactionOperations(harness)
        );

        WorkflowTaskResponseDto response =
                service.approveTask(302L, new WorkflowApprovalRequestDto("client-a", "approver", "approved"));

        assertEquals("APPROVED", response.task_status_cd());
        assertTrue((output.getOut() + output.getErr()).contains(
                "Skipping ATTRIBUTE_LOGICAL_NAME_OVERRIDE side effect for workflow task 302 because entity_ref='not-a-number' is not numeric"
        ));
    }

    @Test
    void appliesValueSideEffectOnApproval() {
        TransactionHarness harness = new TransactionHarness();
        RecordingWorkflowApprovalDao dao = new RecordingWorkflowApprovalDao();
        dao.tasks.put(303L, new FilterLookupWorkflowTaskRecord(
                303L,
                "FILTER_LOOKUP_VALUE",
                "FILTER_LOOKUP",
                "LEDGER_SCOPE:USD",
                "PENDING",
                "producer",
                OffsetDateTime.now(),
                null,
                null,
                "Review USD value",
                "client-a",
                null,
                null,
                null
        ));

        RecordingFilterLookupRegistrationWriteDao writeDao = new RecordingFilterLookupRegistrationWriteDao();
        WorkflowApprovalServiceImpl service = new WorkflowApprovalServiceImpl(
                dao,
                writeDao,
                new RecordingWorkflowPolicyClient(new WorkflowPolicyDecisionDto(true, null, null)),
                new RecordingTransactionOperations(harness)
        );

        service.approveTask(303L, new WorkflowApprovalRequestDto("client-a", "approver", "approved"));
        assertEquals("ACTIVE", dao.valueStatus.get("LEDGER_SCOPE:USD"));
        assertTrue(dao.valueValidated.get("LEDGER_SCOPE:USD"));
    }

    @Test
    void surfacesPolicyBlockAndDoesNotWriteToDb() {
        TransactionHarness harness = new TransactionHarness();
        RecordingWorkflowApprovalDao dao = new RecordingWorkflowApprovalDao();
        dao.tasks.put(301L, new FilterLookupWorkflowTaskRecord(
                301L,
                "FILTER_LOOKUP_REGISTRATION",
                "FILTER_LOOKUP",
                "LEDGER_SCOPE",
                "PENDING",
                "producer",
                OffsetDateTime.now(),
                null,
                null,
                "Review",
                "client-a",
                null,
                null,
                null
        ));

        RecordingFilterLookupRegistrationWriteDao writeDao = new RecordingFilterLookupRegistrationWriteDao();
        RecordingWorkflowPolicyClient policyClient = new RecordingWorkflowPolicyClient(
                new WorkflowPolicyDecisionDto(false, "POL-SV-003", "Approver cannot be the same as submitter")
        );

        WorkflowApprovalServiceImpl service = new WorkflowApprovalServiceImpl(
                dao,
                writeDao,
                policyClient,
                new RecordingTransactionOperations(harness)
        );

        PolicyViolationException exception = assertThrows(PolicyViolationException.class, () ->
                service.approveTask(301L, new WorkflowApprovalRequestDto("client-a", "producer", "self approval"))
        );

        assertEquals("POL-SV-003", exception.code());
        assertEquals("Approver cannot be the same as submitter", exception.getMessage());
        assertFalse(harness.committed);
        assertNull(dao.lookupStatus.get("LEDGER_SCOPE"));
        assertEquals(0, writeDao.metadataChanges.size());
    }

    @Test
    void throwsTaskAlreadyApprovedWhenStatusNotPending() {
        TransactionHarness harness = new TransactionHarness();
        RecordingWorkflowApprovalDao dao = new RecordingWorkflowApprovalDao();
        dao.tasks.put(301L, new FilterLookupWorkflowTaskRecord(
                301L,
                "FILTER_LOOKUP_REGISTRATION",
                "FILTER_LOOKUP",
                "LEDGER_SCOPE",
                "APPROVED",
                "producer",
                OffsetDateTime.now(),
                null,
                null,
                "Review",
                "client-a",
                "approver1",
                OffsetDateTime.now(),
                "done"
        ));

        WorkflowApprovalServiceImpl service = new WorkflowApprovalServiceImpl(
                dao,
                new RecordingFilterLookupRegistrationWriteDao(),
                new RecordingWorkflowPolicyClient(new WorkflowPolicyDecisionDto(true, null, null)),
                new RecordingTransactionOperations(harness)
        );

        assertThrows(WorkflowTaskAlreadyApprovedException.class, () ->
                service.approveTask(301L, new WorkflowApprovalRequestDto("client-a", "approver2", "re-approve"))
        );
    }

    @Test
    void throwsResourceNotFoundForUnknownTask() {
        TransactionHarness harness = new TransactionHarness();
        RecordingWorkflowApprovalDao dao = new RecordingWorkflowApprovalDao();

        WorkflowApprovalServiceImpl service = new WorkflowApprovalServiceImpl(
                dao,
                new RecordingFilterLookupRegistrationWriteDao(),
                new RecordingWorkflowPolicyClient(new WorkflowPolicyDecisionDto(true, null, null)),
                new RecordingTransactionOperations(harness)
        );

        assertThrows(RegistryResourceNotFoundException.class, () ->
                service.approveTask(999L, new WorkflowApprovalRequestDto("client-a", "approver", "looks good"))
        );
    }

    @Test
    void rollsBackAndWrapsExceptions() {
        TransactionHarness harness = new TransactionHarness();
        RecordingWorkflowApprovalDao dao = new RecordingWorkflowApprovalDao() {
            @Override
            public FilterLookupWorkflowTaskRecord approveTask(String clientId, Long id, String approvedBy, OffsetDateTime approvedTs, String approvalNote) {
                throw new RuntimeException("DB Connection Lost");
            }
        };
        dao.tasks.put(301L, new FilterLookupWorkflowTaskRecord(
                301L,
                "FILTER_LOOKUP_REGISTRATION",
                "FILTER_LOOKUP",
                "LEDGER_SCOPE",
                "PENDING",
                "producer",
                OffsetDateTime.now(),
                null,
                null,
                "Review",
                "client-a",
                null,
                null,
                null
        ));

        WorkflowApprovalServiceImpl service = new WorkflowApprovalServiceImpl(
                dao,
                new RecordingFilterLookupRegistrationWriteDao(),
                new RecordingWorkflowPolicyClient(new WorkflowPolicyDecisionDto(true, null, null)),
                new RecordingTransactionOperations(harness)
        );

        assertThrows(WorkflowApprovalServiceException.class, () ->
                service.approveTask(301L, new WorkflowApprovalRequestDto("client-a", "approver", "approve"))
        );

        assertTrue(harness.rolledBack);
    }

    @Test
    void rejectsTaskAndAppliesSideEffects() {
        TransactionHarness harness = new TransactionHarness();
        RecordingWorkflowApprovalDao dao = new RecordingWorkflowApprovalDao();
        OffsetDateTime submittedTs = OffsetDateTime.parse("2026-06-18T10:15:30Z");
        
        // 1. Filter Lookup Rejection
        dao.tasks.put(401L, new FilterLookupWorkflowTaskRecord(
                401L, "FILTER_LOOKUP_REGISTRATION", "FILTER_LOOKUP", "LEDGER_SCOPE",
                "PENDING", "producer", submittedTs, null, null, "Review", "client-a", null, null, null
        ));
        // 2. Attribute Override Rejection
        dao.tasks.put(402L, new FilterLookupWorkflowTaskRecord(
                402L, "ATTRIBUTE_LOGICAL_NAME_OVERRIDE", "ATTRIBUTE", "502",
                "PENDING", "producer", submittedTs, null, null, "Review", "client-a", null, null, null
        ));
        // 3. Object Rejection
        dao.tasks.put(403L, new FilterLookupWorkflowTaskRecord(
                403L, "OBJECT_REGISTRATION", "OBJECT", "obj-123",
                "PENDING", "producer", submittedTs, null, null, "Review", "client-a", null, null, null
        ));
        // 4. Pairing Rejection
        dao.tasks.put(404L, new FilterLookupWorkflowTaskRecord(
                404L, "ATTRIBUTE_PAIRING_REGISTRATION", "ATTRIBUTE_PAIRING", "pair-123",
                "PENDING", "producer", submittedTs, null, null, "Review", "client-a", null, null, null
        ));
        // 5. Relationship Rejection
        dao.tasks.put(405L, new FilterLookupWorkflowTaskRecord(
                405L, "RELATIONSHIP_REGISTRATION", "RELATIONSHIP", "rel-123",
                "PENDING", "producer", submittedTs, null, null, "Review", "client-a", null, null, null
        ));

        RecordingFilterLookupRegistrationWriteDao writeDao = new RecordingFilterLookupRegistrationWriteDao();
        WorkflowApprovalServiceImpl service = new WorkflowApprovalServiceImpl(
                dao, writeDao, new RecordingWorkflowPolicyClient(new WorkflowPolicyDecisionDto(true, null, null)),
                new RecordingTransactionOperations(harness)
        );

        // Perform rejections
        service.rejectTask(401L, Map.of("rejected_by", "rejecter", "rejection_note_txt", "no lookup"));
        assertEquals("client-a", dao.rejectionClientIds.get(401L));
        assertEquals("SUSPENDED", dao.lookupStatus.get("LEDGER_SCOPE"));
        assertEquals("REJECTED", dao.lookupLifecycleStatus.get("LEDGER_SCOPE"));

        service.rejectTask(402L, Map.of("rejected_by", "rejecter", "rejection_note_txt", "no override"));
        assertEquals("client-a", dao.rejectionClientIds.get(402L));
        assertEquals("REJECTED", dao.overrideStatus.get(502L));

        service.rejectTask(403L, Map.of("rejected_by", "rejecter", "rejection_note_txt", "no object"));
        assertEquals("client-a", dao.rejectionClientIds.get(403L));
        assertEquals("DRAFT", dao.objectStatus.get("obj-123"));
        assertEquals("REJECTED", dao.objectGovStatus.get("obj-123"));

        service.rejectTask(404L, Map.of("rejected_by", "rejecter", "rejection_note_txt", "no pairing"));
        assertEquals("client-a", dao.rejectionClientIds.get(404L));
        assertEquals("DRAFT", dao.pairingStatus.get("pair-123"));
        assertEquals("REJECTED", dao.pairingGovStatus.get("pair-123"));

        service.rejectTask(405L, Map.of("rejected_by", "rejecter", "rejection_note_txt", "no relationship"));
        assertEquals("client-a", dao.rejectionClientIds.get(405L));
        assertEquals("REJECTED", dao.relationshipStatus.get("rel-123"));
    }

    @Test
    void approvesObjectPairingRelationshipSideEffects() {
        TransactionHarness harness = new TransactionHarness();
        RecordingWorkflowApprovalDao dao = new RecordingWorkflowApprovalDao();
        OffsetDateTime submittedTs = OffsetDateTime.parse("2026-06-18T10:15:30Z");

        dao.tasks.put(501L, new FilterLookupWorkflowTaskRecord(
                501L, "OBJECT_REGISTRATION", "OBJECT", "obj-123",
                "PENDING", "producer", submittedTs, null, null, "Review", "client-a", null, null, null
        ));
        dao.tasks.put(502L, new FilterLookupWorkflowTaskRecord(
                502L, "ATTRIBUTE_PAIRING_REGISTRATION", "ATTRIBUTE_PAIRING", "pair-123",
                "PENDING", "producer", submittedTs, null, null, "Review", "client-a", null, null, null
        ));
        dao.tasks.put(503L, new FilterLookupWorkflowTaskRecord(
                503L, "RELATIONSHIP_REGISTRATION", "RELATIONSHIP", "rel-123",
                "PENDING", "producer", submittedTs, null, null, "Review", "client-a", null, null, null
        ));

        WorkflowApprovalServiceImpl service = new WorkflowApprovalServiceImpl(
                dao, new RecordingFilterLookupRegistrationWriteDao(), 
                new RecordingWorkflowPolicyClient(new WorkflowPolicyDecisionDto(true, null, null)),
                new RecordingTransactionOperations(harness)
        );

        service.approveTask(501L, new WorkflowApprovalRequestDto("client-a", "approver", "looks good"));
        assertEquals("APPROVED", dao.objectStatus.get("obj-123"));
        assertEquals("APPROVED", dao.objectGovStatus.get("obj-123"));

        service.approveTask(502L, new WorkflowApprovalRequestDto("client-a", "approver", "looks good"));
        assertEquals("APPROVED", dao.pairingStatus.get("pair-123"));
        assertEquals("APPROVED", dao.pairingGovStatus.get("pair-123"));

        service.approveTask(503L, new WorkflowApprovalRequestDto("client-a", "approver", "looks good"));
        assertEquals("APPROVED", dao.relationshipStatus.get("rel-123"));
    }

    private static class RecordingWorkflowApprovalDao implements WorkflowApprovalDao {
        private final Map<Long, FilterLookupWorkflowTaskRecord> tasks = new HashMap<>();
        private final Map<String, String> lookupStatus = new HashMap<>();
        private final Map<String, String> lookupLifecycleStatus = new HashMap<>();
        private final Map<Long, String> overrideStatus = new HashMap<>();
        private final Map<String, String> valueStatus = new HashMap<>();
        private final Map<String, Boolean> valueValidated = new HashMap<>();
        private final Map<String, String> objectStatus = new HashMap<>();
        private final Map<String, String> objectGovStatus = new HashMap<>();
        private final Map<String, String> pairingStatus = new HashMap<>();
        private final Map<String, String> pairingGovStatus = new HashMap<>();
        private final Map<String, String> relationshipStatus = new HashMap<>();
        private final Map<Long, String> rejectionClientIds = new HashMap<>();

        @Override
        public FilterLookupWorkflowTaskRecord findTaskById(String clientId, Long id) {
            FilterLookupWorkflowTaskRecord record = tasks.get(id);
            if (record != null && record.client_id().equals(clientId)) {
                return record;
            }
            return null;
        }

        @Override
        public FilterLookupWorkflowTaskRecord approveTask(String clientId, Long id, String approvedBy, OffsetDateTime approvedTs, String approvalNote) {
            FilterLookupWorkflowTaskRecord record = tasks.get(id);
            if (record == null || !record.client_id().equals(clientId)) {
                throw new IllegalArgumentException("Task not found");
            }
            FilterLookupWorkflowTaskRecord approved = new FilterLookupWorkflowTaskRecord(
                    record.id(),
                    record.task_type_cd(),
                    record.entity_type_cd(),
                    record.entity_ref(),
                    "APPROVED",
                    record.submitted_by(),
                    record.submitted_ts(),
                    record.assigned_to(),
                    record.due_dt(),
                    record.description_txt(),
                    record.client_id(),
                    approvedBy,
                    approvedTs,
                    approvalNote
            );
            tasks.put(id, approved);
            return approved;
        }

        @Override
        public void approveLookup(String clientId, String lookupCd, String governanceStatus, OffsetDateTime updatedTs, String updatedBy) {
            lookupStatus.put(lookupCd, governanceStatus);
        }

        @Override
        public void approveAttributeOverride(String clientId, Long id, String lifecycleStatus, OffsetDateTime updatedTs, String updatedBy) {
            overrideStatus.put(id, lifecycleStatus);
        }

        @Override
        public void approveFilterLookupValue(String lookupCd, String valueCd, String lifecycleStatus, boolean validated, OffsetDateTime updatedTs) {
            valueStatus.put(lookupCd + ":" + valueCd, lifecycleStatus);
            valueValidated.put(lookupCd + ":" + valueCd, validated);
        }

        @Override
        public FilterLookupWorkflowTaskRecord findTaskByIdOnly(Long id) {
            return tasks.get(id);
        }

        @Override
        public FilterLookupWorkflowTaskRecord rejectTask(String clientId, Long id, String rejectedBy, OffsetDateTime rejectedTs, String rejectionNote) {
            FilterLookupWorkflowTaskRecord record = tasks.get(id);
            if (record == null) {
                throw new IllegalArgumentException("Task not found");
            }
            rejectionClientIds.put(id, clientId);
            FilterLookupWorkflowTaskRecord rejected = new FilterLookupWorkflowTaskRecord(
                    record.id(),
                    record.task_type_cd(),
                    record.entity_type_cd(),
                    record.entity_ref(),
                    "REJECTED",
                    record.submitted_by(),
                    record.submitted_ts(),
                    record.assigned_to(),
                    record.due_dt(),
                    record.description_txt(),
                    record.client_id(),
                    rejectedBy,
                    rejectedTs,
                    rejectionNote
            );
            tasks.put(id, rejected);
            return rejected;
        }

        @Override
        public void approveObject(String clientId, String objectId, String lifecycleStatus, OffsetDateTime updatedTs, String updatedBy) {
            objectStatus.put(objectId, lifecycleStatus);
            objectGovStatus.put(objectId, "APPROVED");
        }

        @Override
        public void grantDefaultAttributeAccess(String clientId, String objectId, String approvedBy, OffsetDateTime approvedTs) {
            // no-op in test
        }

        @Override
        public void approvePairing(String clientId, String pairingCd, String lifecycleStatus, OffsetDateTime updatedTs, String updatedBy) {
            pairingStatus.put(pairingCd, lifecycleStatus);
            pairingGovStatus.put(pairingCd, "APPROVED");
        }

        @Override
        public void approveRelationship(String relationshipCd, String lifecycleStatus, OffsetDateTime updatedTs, String updatedBy) {
            relationshipStatus.put(relationshipCd, lifecycleStatus);
        }

        @Override
        public void rejectLookup(String clientId, String lookupCd, String governanceStatus, String lifecycleStatus, OffsetDateTime updatedTs, String updatedBy) {
            lookupStatus.put(lookupCd, governanceStatus);
            lookupLifecycleStatus.put(lookupCd, lifecycleStatus);
        }

        @Override
        public void rejectAttributeOverride(String clientId, Long id, String overrideStatus, OffsetDateTime updatedTs, String updatedBy) {
            this.overrideStatus.put(id, overrideStatus);
        }

        @Override
        public void rejectObject(String clientId, String objectId, String lifecycleStatus, String governanceReviewStatus, OffsetDateTime updatedTs, String updatedBy) {
            objectStatus.put(objectId, lifecycleStatus);
            objectGovStatus.put(objectId, governanceReviewStatus);
        }

        @Override
        public void rejectPairing(String clientId, String pairingCd, String lifecycleStatus, String governanceReviewStatus, OffsetDateTime updatedTs, String updatedBy) {
            pairingStatus.put(pairingCd, lifecycleStatus);
            pairingGovStatus.put(pairingCd, governanceReviewStatus);
        }

        @Override
        public void rejectRelationship(String relationshipCd, String lifecycleStatus, OffsetDateTime updatedTs, String updatedBy) {
            relationshipStatus.put(relationshipCd, lifecycleStatus);
        }
    }

    private static final class RecordingFilterLookupRegistrationWriteDao implements FilterLookupRegistrationWriteDao {
        private final List<FilterLookupMetadataChangeHistoryWriteRequest> metadataChanges = new ArrayList<>();

        @Override
        public com.lextr.semanticlayer.model.SemanticFilterLookupRecord insertLookup(com.lextr.semanticlayer.model.SemanticFilterLookupWriteRequest request) {
            return null;
        }

        @Override
        public com.lextr.semanticlayer.model.FilterLookupWorkflowTaskRecord insertWorkflowTask(com.lextr.semanticlayer.model.FilterLookupWorkflowTaskWriteRequest request) {
            return null;
        }

        @Override
        public FilterLookupMetadataChangeHistoryRecord insertMetadataChangeHistory(FilterLookupMetadataChangeHistoryWriteRequest request) {
            metadataChanges.add(request);
            return new FilterLookupMetadataChangeHistoryRecord(
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
        }
    }

    private static final class RecordingWorkflowPolicyClient implements WorkflowPolicyClient {
        private final WorkflowPolicyDecisionDto decision;
        private final List<WorkflowPolicyRequestDto> requests = new ArrayList<>();

        private RecordingWorkflowPolicyClient(WorkflowPolicyDecisionDto decision) {
            this.decision = decision;
        }

        @Override
        public WorkflowPolicyDecisionDto validateApproval(WorkflowPolicyRequestDto request) {
            requests.add(request);
            return decision;
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

        private void begin() {
            committed = false;
            rolledBack = false;
        }

        private void commit() {
            committed = true;
        }

        private void rollback() {
            rolledBack = true;
        }
    }

    private static final class SimpleTransactionStatus implements TransactionStatus {
        @Override
        public boolean isNewTransaction() { return true; }
        @Override
        public boolean hasSavepoint() { return false; }
        @Override
        public void setRollbackOnly() {}
        @Override
        public boolean isRollbackOnly() { return false; }
        @Override
        public void flush() {}
        @Override
        public boolean isCompleted() { return false; }
        @Override
        public Object createSavepoint() { return null; }
        @Override
        public void rollbackToSavepoint(Object savepoint) {}
        @Override
        public void releaseSavepoint(Object savepoint) {}
    }
}
