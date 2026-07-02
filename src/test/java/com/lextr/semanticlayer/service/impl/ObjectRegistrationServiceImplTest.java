package com.lextr.semanticlayer.service.impl;

import com.lextr.semanticlayer.dao.ObjectRegistrationWriteDao;
import com.lextr.semanticlayer.dto.AttributeRegistrationRequestDto;
import com.lextr.semanticlayer.dto.ObjectRegistrationRequestDto;
import com.lextr.semanticlayer.dto.ObjectRegistrationResponseDto;
import com.lextr.semanticlayer.exception.ObjectRegistrationServiceException;
import com.lextr.semanticlayer.exception.PolicyViolationException;
import com.lextr.semanticlayer.dto.TaxonomyPolicyDecisionDto;
import com.lextr.semanticlayer.dto.TaxonomyPolicyRequestDto;
import com.lextr.semanticlayer.model.AttributeCatalogRecord;
import com.lextr.semanticlayer.model.AttributeCatalogWriteRequest;
import com.lextr.semanticlayer.model.MetadataChangeHistoryRecord;
import com.lextr.semanticlayer.model.MetadataChangeHistoryWriteRequest;
import com.lextr.semanticlayer.model.ObjectCatalogRecord;
import com.lextr.semanticlayer.model.ObjectCatalogWriteRequest;
import com.lextr.semanticlayer.model.WorkflowTaskRecord;
import com.lextr.semanticlayer.model.WorkflowTaskWriteRequest;
import com.lextr.semanticlayer.service.TaxonomyPolicyClient;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ObjectRegistrationServiceImplTest {

    @Test
    void registersObjectAtomicallyAndWritesAuditRow() {
        TransactionHarness harness = new TransactionHarness();
        RecordingObjectRegistrationWriteDao dao = new RecordingObjectRegistrationWriteDao(harness);
        RecordingTaxonomyPolicyClient policyClient = new RecordingTaxonomyPolicyClient(
                new TaxonomyPolicyDecisionDto(true, null, null)
        );
        ObjectRegistrationServiceImpl service = new ObjectRegistrationServiceImpl(
                dao,
                policyClient,
                new RecordingTransactionOperations(harness)
        );

        ObjectRegistrationResponseDto result = service.registerObject(new ObjectRegistrationRequestDto(
                "client-a",
                "GL_BALANCE",
                "GL Balance",
                "TABLE",
                "meta",
                UUID.fromString("00000000-0000-0000-0000-000000000201"),
                "producer",
                List.of(new AttributeRegistrationRequestDto(
                        "AMOUNT",
                        "Amount",
                        "DECIMAL",
                        "MDRM12345678",
                        "MDRM",
                        "US",
                        true,
                        false,
                        false
                ))
        ));

        assertTrue(harness.committed);
        assertEquals(1, dao.committedObjects.size());
        assertEquals(1, dao.committedAttributes.size());
        assertEquals(1, dao.committedWorkflowTasks.size());
        assertEquals(1, dao.committedMetadataChanges.size());
        assertEquals("GL_BALANCE", dao.objectRequest.object_cd());
        assertEquals("client-a", dao.objectRequest.client_id());
        assertEquals("AMOUNT", dao.attributeRequests.get(0).attribute_cd());
        assertTrue(dao.attributeRequests.get(0).pk_flg());
        assertEquals(false, dao.attributeRequests.get(0).fk_flg());
        assertEquals(false, dao.attributeRequests.get(0).nullable_flg());
        assertEquals("PENDING_APPROVAL", dao.workflowTaskRequest.task_status_cd());
        assertEquals("REGISTERED", dao.metadataChangeHistoryRequest.change_type_cd());
        assertEquals("Registered draft object", dao.committedMetadataChanges.get(0).change_summary_txt());
        assertEquals(1, policyClient.recordedRequests.size());
        TaxonomyPolicyRequestDto policyRequest = policyClient.recordedRequests.get(0);
        assertEquals("client-a", policyRequest.client_id());
        assertEquals("MDRM12345678", policyRequest.taxonomy_cd());
        assertEquals("MDRM", policyRequest.taxonomy_source_cd());
        assertEquals("US", policyRequest.taxonomy_jurisdiction_cd());
        assertEquals("DRAFT", result.lifecycle_status_cd());
        assertEquals("PENDING_APPROVAL", result.workflow_status_cd());
        assertEquals("AMOUNT", result.attributes().get(0).attribute_cd());
        assertTrue(result.attributes().get(0).pk_flg());
        assertEquals(false, result.attributes().get(0).fk_flg());
        assertEquals(false, result.attributes().get(0).nullable_flg());
        assertNotNull(result.object_id());
        assertNotNull(result.workflow_task_id());
        assertNotNull(result.change_history_id());
    }

    @Test
    void deduplicatesAttributesByAttributeCodeKeepingFirstOccurrence() {
        TransactionHarness harness = new TransactionHarness();
        RecordingObjectRegistrationWriteDao dao = new RecordingObjectRegistrationWriteDao(harness);
        ObjectRegistrationServiceImpl service = new ObjectRegistrationServiceImpl(
                dao,
                request -> new TaxonomyPolicyDecisionDto(true, null, null),
                new RecordingTransactionOperations(harness)
        );

        ObjectRegistrationResponseDto result = service.registerObject(new ObjectRegistrationRequestDto(
                "client-a",
                "GL_BALANCE",
                "GL Balance",
                "TABLE",
                "meta",
                UUID.fromString("00000000-0000-0000-0000-000000000201"),
                "producer",
                List.of(
                        new AttributeRegistrationRequestDto("AMOUNT", "Amount", "DECIMAL", "MDRM12345678", "MDRM", "US", true, false, false),
                        new AttributeRegistrationRequestDto("AMOUNT", "Amount Dup", "INTEGER", "MDRM99999999", "MDRM", "US", false, false, true),
                        new AttributeRegistrationRequestDto("BALANCE", "Balance", "DECIMAL", "MDRM12345678", "MDRM", "US", false, true, true)
                )
        ));

        assertTrue(harness.committed);
        assertEquals(2, dao.committedAttributes.size(), "Duplicate attribute_cd should be deduplicated");
        assertEquals(2, result.attributes().size());
        assertEquals("AMOUNT", result.attributes().get(0).attribute_cd());
        assertEquals("BALANCE", result.attributes().get(1).attribute_cd());
        // Verify the first occurrence wins — attribute_nm should be "Amount", not "Amount Dup"
        assertEquals("Amount", dao.attributeRequests.get(0).attribute_nm());
    }

    @Test
    void surfacesPolicyBlockBeforePersistingWrites() {
        TransactionHarness harness = new TransactionHarness();
        RecordingObjectRegistrationWriteDao dao = new RecordingObjectRegistrationWriteDao(harness);
        RecordingTaxonomyPolicyClient policyClient = new RecordingTaxonomyPolicyClient(
                new TaxonomyPolicyDecisionDto(false, "taxonomy.jurisdiction_valid", "Taxonomy jurisdiction is invalid")
        );
        ObjectRegistrationServiceImpl service = new ObjectRegistrationServiceImpl(
                dao,
                policyClient,
                new RecordingTransactionOperations(harness)
        );

        PolicyViolationException exception = assertThrows(PolicyViolationException.class, () -> service.registerObject(new ObjectRegistrationRequestDto(
                "client-a",
                "GL_BALANCE",
                "GL Balance",
                "TABLE",
                "meta",
                UUID.fromString("00000000-0000-0000-0000-000000000201"),
                "producer",
                List.of(new AttributeRegistrationRequestDto(
                        "AMOUNT",
                        "Amount",
                        "DECIMAL",
                        "MDRM12345678",
                        "MDRM",
                        "US",
                        true,
                        false,
                        false
                ))
        )));

        assertEquals("taxonomy.jurisdiction_valid", exception.code());
        assertEquals(1, policyClient.recordedRequests.size());
        assertEquals("client-a", policyClient.recordedRequests.get(0).client_id());
        assertEquals(0, dao.committedObjects.size());
        assertEquals(0, dao.committedMetadataChanges.size());
        assertTrue(!harness.committed);
    }

    @Test
    void wrapsDaoFailuresInServiceExceptionAndRollsBackWrites() {
        TransactionHarness harness = new TransactionHarness();
        FailingObjectRegistrationWriteDao dao = new FailingObjectRegistrationWriteDao(harness);
        ObjectRegistrationServiceImpl service = new ObjectRegistrationServiceImpl(
                dao,
                request -> new TaxonomyPolicyDecisionDto(true, null, null),
                new RecordingTransactionOperations(harness)
        );

        assertThrows(ObjectRegistrationServiceException.class, () -> service.registerObject(new ObjectRegistrationRequestDto(
                "client-a",
                "GL_BALANCE",
                "GL Balance",
                "TABLE",
                "meta",
                UUID.fromString("00000000-0000-0000-0000-000000000201"),
                "producer",
                List.of(new AttributeRegistrationRequestDto(
                        "AMOUNT",
                        "Amount",
                        "DECIMAL",
                        "MDRM12345678",
                        "MDRM",
                        "US",
                        true,
                        false,
                        false
                ))
        )));

        assertTrue(harness.rolledBack);
        assertEquals(0, dao.committedObjects.size());
        assertEquals(0, dao.committedAttributes.size());
        assertEquals(0, dao.committedWorkflowTasks.size());
        assertEquals(0, dao.committedMetadataChanges.size());
    }

    private static final class RecordingObjectRegistrationWriteDao implements ObjectRegistrationWriteDao {

        private final TransactionHarness harness;
        private ObjectCatalogWriteRequest objectRequest;
        private final List<AttributeCatalogWriteRequest> attributeRequests = new ArrayList<>();
        private WorkflowTaskWriteRequest workflowTaskRequest;
        private MetadataChangeHistoryWriteRequest metadataChangeHistoryRequest;
        private final List<ObjectCatalogRecord> committedObjects = new ArrayList<>();
        private final List<AttributeCatalogRecord> committedAttributes = new ArrayList<>();
        private final List<WorkflowTaskRecord> committedWorkflowTasks = new ArrayList<>();
        private final List<MetadataChangeHistoryRecord> committedMetadataChanges = new ArrayList<>();

        private RecordingObjectRegistrationWriteDao(TransactionHarness harness) {
            this.harness = harness;
        }

        @Override
        public ObjectCatalogRecord insertDraftObject(ObjectCatalogWriteRequest request) {
            objectRequest = request;
            ObjectCatalogRecord record = new ObjectCatalogRecord(
                    request.object_id(),
                    request.client_id(),
                    request.object_cd(),
                    request.object_nm(),
                    request.object_type_cd(),
                    request.schema_cd(),
                    request.connection_id(),
                    "DRAFT",
                    request.created_ts(),
                    request.created_by(),
                    request.updated_ts(),
                    request.updated_by()
            );
            harness.addObject(record, committedObjects);
            return record;
        }

        @Override
        public AttributeCatalogRecord insertAttribute(AttributeCatalogWriteRequest request) {
            attributeRequests.add(request);
            AttributeCatalogRecord record = new AttributeCatalogRecord(
                    request.attribute_id(),
                    request.object_id(),
                    request.client_id(),
                    request.attribute_cd(),
                    request.attribute_nm(),
                    request.data_type_cd(),
                    request.taxonomy_cd(),
                    request.taxonomy_source_cd(),
                    request.taxonomy_jurisdiction_cd(),
                    request.pk_flg(),
                    request.fk_flg(),
                    request.nullable_flg(),
                    request.created_ts(),
                    request.created_by(),
                    request.updated_ts(),
                    request.updated_by()
            );
            harness.addAttribute(record, committedAttributes);
            return record;
        }

        @Override
        public WorkflowTaskRecord insertWorkflowTask(WorkflowTaskWriteRequest request) {
            workflowTaskRequest = request;
            WorkflowTaskRecord record = new WorkflowTaskRecord(
                    request.workflow_task_id(),
                    request.client_id(),
                    request.workflow_type_cd(),
                    request.entity_type_cd(),
                    request.entity_id(),
                    request.task_status_cd(),
                    request.created_ts(),
                    request.created_by(),
                    request.updated_ts(),
                    request.updated_by()
            );
            harness.addWorkflowTask(record, committedWorkflowTasks);
            return record;
        }

        @Override
        public MetadataChangeHistoryRecord insertMetadataChangeHistory(MetadataChangeHistoryWriteRequest request) {
            metadataChangeHistoryRequest = request;
            MetadataChangeHistoryRecord record = new MetadataChangeHistoryRecord(
                    request.change_history_id(),
                    request.client_id(),
                    request.entity_type_cd(),
                    request.entity_id(),
                    request.change_type_cd(),
                    request.change_summary_txt(),
                    request.created_ts(),
                    request.created_by()
            );
            harness.addMetadataChange(record, committedMetadataChanges);
            return record;
        }
    }

    private static final class RecordingTaxonomyPolicyClient implements TaxonomyPolicyClient {

        private final TaxonomyPolicyDecisionDto decision;
        private final List<TaxonomyPolicyRequestDto> recordedRequests = new ArrayList<>();

        private RecordingTaxonomyPolicyClient(TaxonomyPolicyDecisionDto decision) {
            this.decision = decision;
        }

        @Override
        public TaxonomyPolicyDecisionDto validateJurisdiction(TaxonomyPolicyRequestDto request) {
            recordedRequests.add(request);
            return decision;
        }
    }

    private static final class FailingObjectRegistrationWriteDao implements ObjectRegistrationWriteDao {

        private final TransactionHarness harness;
        private final List<ObjectCatalogRecord> committedObjects = new ArrayList<>();
        private final List<AttributeCatalogRecord> committedAttributes = new ArrayList<>();
        private final List<WorkflowTaskRecord> committedWorkflowTasks = new ArrayList<>();
        private final List<MetadataChangeHistoryRecord> committedMetadataChanges = new ArrayList<>();

        private FailingObjectRegistrationWriteDao(TransactionHarness harness) {
            this.harness = harness;
        }

        @Override
        public ObjectCatalogRecord insertDraftObject(ObjectCatalogWriteRequest request) {
            ObjectCatalogRecord record = new ObjectCatalogRecord(
                    request.object_id(),
                    request.client_id(),
                    request.object_cd(),
                    request.object_nm(),
                    request.object_type_cd(),
                    request.schema_cd(),
                    request.connection_id(),
                    "DRAFT",
                    request.created_ts(),
                    request.created_by(),
                    request.updated_ts(),
                    request.updated_by()
            );
            harness.addObject(record, committedObjects);
            return record;
        }

        @Override
        public AttributeCatalogRecord insertAttribute(AttributeCatalogWriteRequest request) {
            AttributeCatalogRecord record = new AttributeCatalogRecord(
                    request.attribute_id(),
                    request.object_id(),
                    request.client_id(),
                    request.attribute_cd(),
                    request.attribute_nm(),
                    request.data_type_cd(),
                    request.taxonomy_cd(),
                    request.taxonomy_source_cd(),
                    request.taxonomy_jurisdiction_cd(),
                    request.pk_flg(),
                    request.fk_flg(),
                    request.nullable_flg(),
                    request.created_ts(),
                    request.created_by(),
                    request.updated_ts(),
                    request.updated_by()
            );
            harness.addAttribute(record, committedAttributes);
            throw new IllegalStateException("db write failed");
        }

        @Override
        public WorkflowTaskRecord insertWorkflowTask(WorkflowTaskWriteRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public MetadataChangeHistoryRecord insertMetadataChangeHistory(MetadataChangeHistoryWriteRequest request) {
            throw new UnsupportedOperationException();
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
        private boolean inTransaction;
        private final List<ObjectCatalogRecord> pendingObjects = new ArrayList<>();
        private final List<AttributeCatalogRecord> pendingAttributes = new ArrayList<>();
        private final List<WorkflowTaskRecord> pendingWorkflowTasks = new ArrayList<>();
        private final List<MetadataChangeHistoryRecord> pendingMetadataChanges = new ArrayList<>();
        private List<ObjectCatalogRecord> objectSink;
        private List<AttributeCatalogRecord> attributeSink;
        private List<WorkflowTaskRecord> workflowTaskSink;
        private List<MetadataChangeHistoryRecord> metadataChangeSink;

        private void begin() {
            committed = false;
            rolledBack = false;
            inTransaction = true;
            pendingObjects.clear();
            pendingAttributes.clear();
            pendingWorkflowTasks.clear();
            pendingMetadataChanges.clear();
        }

        private void commit() {
            if (objectSink != null) {
                objectSink.addAll(pendingObjects);
            }
            if (attributeSink != null) {
                attributeSink.addAll(pendingAttributes);
            }
            if (workflowTaskSink != null) {
                workflowTaskSink.addAll(pendingWorkflowTasks);
            }
            if (metadataChangeSink != null) {
                metadataChangeSink.addAll(pendingMetadataChanges);
            }
            committed = true;
            inTransaction = false;
        }

        private void rollback() {
            pendingObjects.clear();
            pendingAttributes.clear();
            pendingWorkflowTasks.clear();
            pendingMetadataChanges.clear();
            rolledBack = true;
            inTransaction = false;
        }

        private void addObject(ObjectCatalogRecord record, List<ObjectCatalogRecord> sink) {
            objectSink = sink;
            if (inTransaction) {
                pendingObjects.add(record);
            } else {
                sink.add(record);
            }
        }

        private void addAttribute(AttributeCatalogRecord record, List<AttributeCatalogRecord> sink) {
            attributeSink = sink;
            if (inTransaction) {
                pendingAttributes.add(record);
            } else {
                sink.add(record);
            }
        }

        private void addWorkflowTask(WorkflowTaskRecord record, List<WorkflowTaskRecord> sink) {
            workflowTaskSink = sink;
            if (inTransaction) {
                pendingWorkflowTasks.add(record);
            } else {
                sink.add(record);
            }
        }

        private void addMetadataChange(MetadataChangeHistoryRecord record, List<MetadataChangeHistoryRecord> sink) {
            metadataChangeSink = sink;
            if (inTransaction) {
                pendingMetadataChanges.add(record);
            } else {
                sink.add(record);
            }
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
