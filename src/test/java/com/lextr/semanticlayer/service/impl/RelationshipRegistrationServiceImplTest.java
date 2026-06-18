package com.lextr.semanticlayer.service.impl;

import com.lextr.semanticlayer.dao.ObjectExposureReadDao;
import com.lextr.semanticlayer.dao.ObjectRegistrationWriteDao;
import com.lextr.semanticlayer.dao.RegistryReadDao;
import com.lextr.semanticlayer.dao.RelationshipRegistrationWriteDao;
import com.lextr.semanticlayer.dto.RelationshipPolicyDecisionDto;
import com.lextr.semanticlayer.dto.RelationshipPolicyRequestDto;
import com.lextr.semanticlayer.dto.RelationshipRegistrationRequestDto;
import com.lextr.semanticlayer.dto.RelationshipRegistrationResponseDto;
import com.lextr.semanticlayer.model.AttributeCatalogRecord;
import com.lextr.semanticlayer.model.AttributeCatalogWriteRequest;
import com.lextr.semanticlayer.model.DataConnectionRecord;
import com.lextr.semanticlayer.model.MetadataChangeHistoryRecord;
import com.lextr.semanticlayer.model.MetadataChangeHistoryWriteRequest;
import com.lextr.semanticlayer.model.ObjectCatalogRecord;
import com.lextr.semanticlayer.model.ObjectCatalogWriteRequest;
import com.lextr.semanticlayer.model.ObjectExposureRecord;
import com.lextr.semanticlayer.exception.PolicyViolationException;
import com.lextr.semanticlayer.exception.RelationshipRegistrationServiceException;
import com.lextr.semanticlayer.model.SemanticRelationshipCatalogRecord;
import com.lextr.semanticlayer.model.SemanticRelationshipCatalogWriteRequest;
import com.lextr.semanticlayer.model.SemanticRelationshipProjectionSyncWriteRequest;
import com.lextr.semanticlayer.model.WorkflowTaskRecord;
import com.lextr.semanticlayer.model.WorkflowTaskWriteRequest;
import com.lextr.semanticlayer.service.RelationshipPolicyClient;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RelationshipRegistrationServiceImplTest {

    @Test
    void registersRelationshipAtomicallyAndWritesAuditRow() {
        TransactionHarness harness = new TransactionHarness();
        RecordingRelationshipRegistrationWriteDao relationshipDao = new RecordingRelationshipRegistrationWriteDao(harness);
        relationshipDao.response = new SemanticRelationshipCatalogRecord(
                101L,
                "GL_TO_LEDGER",
                "meta",
                "gl_balance",
                "ledger_id",
                "meta",
                "ledger",
                "ledger_id",
                "FOREIGN_KEY",
                "MANY_TO_ONE",
                "INNER",
                true,
                false,
                false,
                "GL balances map to ledger master rows",
                "Join on ledger identifier",
                null,
                "ACTIVE",
                OffsetDateTime.parse("2026-06-17T10:15:30Z"),
                "producer",
                OffsetDateTime.parse("2026-06-17T10:15:30Z"),
                "producer"
        );
        RecordingObjectRegistrationWriteDao sideEffectDao = new RecordingObjectRegistrationWriteDao(harness);
        RecordingObjectExposureReadDao objectReadDao = new RecordingObjectExposureReadDao(
                objectRecord("client-a", "meta", "gl_balance", UUID.fromString("00000000-0000-0000-0000-000000000201")),
                objectRecord("client-a", "meta", "ledger", UUID.fromString("00000000-0000-0000-0000-000000000202"))
        );
        RecordingRegistryReadDao registryReadDao = new RecordingRegistryReadDao(
                connectionRecord(UUID.fromString("00000000-0000-0000-0000-000000000201"), "POSTGRES"),
                connectionRecord(UUID.fromString("00000000-0000-0000-0000-000000000202"), "POSTGRES")
        );
        RecordingRelationshipPolicyClient policyClient = new RecordingRelationshipPolicyClient(
                new RelationshipPolicyDecisionDto(true, null, null)
        );
        RelationshipRegistrationServiceImpl service = new RelationshipRegistrationServiceImpl(
                relationshipDao,
                sideEffectDao,
                objectReadDao,
                registryReadDao,
                policyClient,
                new RecordingTransactionOperations(harness)
        );

        RelationshipRegistrationResponseDto response = service.registerRelationship(new RelationshipRegistrationRequestDto(
                "GL_TO_LEDGER",
                "meta",
                "gl_balance",
                "ledger_id",
                "meta",
                "ledger",
                "ledger_id",
                "FOREIGN_KEY",
                "MANY_TO_ONE",
                "INNER",
                true,
                false,
                false,
                "GL balances map to ledger master rows",
                "Join on ledger identifier",
                "producer"
        ));

        assertTrue(harness.committed);
        assertEquals(1, relationshipDao.committedRelationships.size());
        assertEquals(1, sideEffectDao.committedWorkflowTasks.size());
        assertEquals(1, sideEffectDao.committedMetadataChanges.size());
        assertEquals("GL_TO_LEDGER", relationshipDao.lastRequest.relationship_cd());
        assertEquals("ACTIVE", relationshipDao.lastRequest.lifecycle_status_cd());
        assertEquals("producer", relationshipDao.lastRequest.created_by());
        assertNotNull(relationshipDao.lastRequest.created_ts());
        assertEquals("client-a", policyClient.lastRequest.client_id());
        assertEquals("POSTGRES", policyClient.lastRequest.parent_engine_cd());
        assertEquals("POSTGRES", policyClient.lastRequest.child_engine_cd());
        assertTrue(!policyClient.lastRequest.is_cross_engine_flg());
        assertEquals("PENDING_APPROVAL", sideEffectDao.workflowTaskRequest.task_status_cd());
        assertEquals("REGISTERED", sideEffectDao.metadataChangeHistoryRequest.change_type_cd());
        assertTrue(sideEffectDao.metadataChangeHistoryRequest.change_summary_txt().contains("GL_TO_LEDGER"));
        assertEquals(101L, response.id());
        assertEquals("ACTIVE", response.lifecycle_status_cd());
        assertEquals("FOREIGN_KEY", response.relationship_type_cd());
    }

    @Test
    void blocksCrossEngineRelationshipBeforePersistingWrites() {
        TransactionHarness harness = new TransactionHarness();
        RecordingRelationshipRegistrationWriteDao relationshipDao = new RecordingRelationshipRegistrationWriteDao(harness);
        RecordingObjectRegistrationWriteDao sideEffectDao = new RecordingObjectRegistrationWriteDao(harness);
        RecordingObjectExposureReadDao objectReadDao = new RecordingObjectExposureReadDao(
                objectRecord("client-a", "meta", "gl_balance", UUID.fromString("00000000-0000-0000-0000-000000000201")),
                objectRecord("client-a", "meta", "ledger", UUID.fromString("00000000-0000-0000-0000-000000000202"))
        );
        RecordingRegistryReadDao registryReadDao = new RecordingRegistryReadDao(
                connectionRecord(UUID.fromString("00000000-0000-0000-0000-000000000201"), "POSTGRES"),
                connectionRecord(UUID.fromString("00000000-0000-0000-0000-000000000202"), "CLICKHOUSE")
        );
        RecordingRelationshipPolicyClient policyClient = new RecordingRelationshipPolicyClient(
                new RelationshipPolicyDecisionDto(false, "POL-CE-001", "Cross-engine relationships are not allowed")
        );
        RelationshipRegistrationServiceImpl service = new RelationshipRegistrationServiceImpl(
                relationshipDao,
                sideEffectDao,
                objectReadDao,
                registryReadDao,
                policyClient,
                new RecordingTransactionOperations(harness)
        );

        PolicyViolationException exception = assertThrows(PolicyViolationException.class, () -> service.registerRelationship(new RelationshipRegistrationRequestDto(
                "GL_TO_LEDGER",
                "meta",
                "gl_balance",
                "ledger_id",
                "meta",
                "ledger",
                "ledger_id",
                "FOREIGN_KEY",
                "MANY_TO_ONE",
                "INNER",
                true,
                false,
                true,
                "GL balances map to ledger master rows",
                "Join on ledger identifier",
                "producer"
        )));

        assertEquals("POL-CE-001", exception.code());
        assertTrue(policyClient.lastRequest.is_cross_engine_flg());
        assertEquals(0, relationshipDao.committedRelationships.size());
        assertEquals(0, sideEffectDao.committedWorkflowTasks.size());
        assertEquals(0, sideEffectDao.committedMetadataChanges.size());
        assertTrue(!harness.committed);
    }

    @Test
    void wrapsSideEffectFailuresAndRollsBackWrites() {
        TransactionHarness harness = new TransactionHarness();
        RecordingRelationshipRegistrationWriteDao relationshipDao = new RecordingRelationshipRegistrationWriteDao(harness);
        relationshipDao.response = new SemanticRelationshipCatalogRecord(
                101L,
                "GL_TO_LEDGER",
                "meta",
                "gl_balance",
                "ledger_id",
                "meta",
                "ledger",
                "ledger_id",
                "FOREIGN_KEY",
                "MANY_TO_ONE",
                "INNER",
                true,
                false,
                false,
                "GL balances map to ledger master rows",
                "Join on ledger identifier",
                null,
                "ACTIVE",
                OffsetDateTime.parse("2026-06-17T10:15:30Z"),
                "producer",
                OffsetDateTime.parse("2026-06-17T10:15:30Z"),
                "producer"
        );
        FailingObjectRegistrationWriteDao sideEffectDao = new FailingObjectRegistrationWriteDao(harness);
        RecordingObjectExposureReadDao objectReadDao = new RecordingObjectExposureReadDao(
                objectRecord("client-a", "meta", "gl_balance", UUID.fromString("00000000-0000-0000-0000-000000000201")),
                objectRecord("client-a", "meta", "ledger", UUID.fromString("00000000-0000-0000-0000-000000000202"))
        );
        RecordingRegistryReadDao registryReadDao = new RecordingRegistryReadDao(
                connectionRecord(UUID.fromString("00000000-0000-0000-0000-000000000201"), "POSTGRES"),
                connectionRecord(UUID.fromString("00000000-0000-0000-0000-000000000202"), "POSTGRES")
        );
        RelationshipRegistrationServiceImpl service = new RelationshipRegistrationServiceImpl(
                relationshipDao,
                sideEffectDao,
                objectReadDao,
                registryReadDao,
                request -> new RelationshipPolicyDecisionDto(true, null, null),
                new RecordingTransactionOperations(harness)
        );

        RelationshipRegistrationServiceException exception = assertThrows(RelationshipRegistrationServiceException.class, () -> service.registerRelationship(new RelationshipRegistrationRequestDto(
                "GL_TO_LEDGER",
                "meta",
                "gl_balance",
                "ledger_id",
                "meta",
                "ledger",
                "ledger_id",
                "FOREIGN_KEY",
                "MANY_TO_ONE",
                "INNER",
                true,
                false,
                false,
                "GL balances map to ledger master rows",
                "Join on ledger identifier",
                "producer"
        )));

        assertTrue(exception.getMessage().contains("Unable to register relationship"));
        assertTrue(harness.rolledBack);
        assertEquals(0, relationshipDao.committedRelationships.size());
        assertEquals(0, sideEffectDao.committedWorkflowTasks.size());
        assertEquals(0, sideEffectDao.committedMetadataChanges.size());
    }

    private static final class RecordingRelationshipRegistrationWriteDao implements RelationshipRegistrationWriteDao {

        private final TransactionHarness harness;
        private SemanticRelationshipCatalogWriteRequest lastRequest;
        private SemanticRelationshipCatalogRecord response;
        private final List<SemanticRelationshipCatalogRecord> committedRelationships = new ArrayList<>();

        private RecordingRelationshipRegistrationWriteDao(TransactionHarness harness) {
            this.harness = harness;
        }

        @Override
        public SemanticRelationshipCatalogRecord insertRelationship(SemanticRelationshipCatalogWriteRequest request) {
            lastRequest = request;
            harness.addRelationship(response, committedRelationships);
            return response;
        }

        @Override
        public SemanticRelationshipCatalogRecord updateNeo4jProjectionSync(SemanticRelationshipProjectionSyncWriteRequest request) {
            throw new UnsupportedOperationException("Not used in relationship service tests");
        }
    }

    private static class RecordingObjectRegistrationWriteDao implements ObjectRegistrationWriteDao {

        private final TransactionHarness harness;
        WorkflowTaskWriteRequest workflowTaskRequest;
        MetadataChangeHistoryWriteRequest metadataChangeHistoryRequest;
        final List<WorkflowTaskRecord> committedWorkflowTasks = new ArrayList<>();
        final List<MetadataChangeHistoryRecord> committedMetadataChanges = new ArrayList<>();

        private RecordingObjectRegistrationWriteDao(TransactionHarness harness) {
            this.harness = harness;
        }

        @Override
        public ObjectCatalogRecord insertDraftObject(ObjectCatalogWriteRequest request) {
            throw new UnsupportedOperationException("Not used in relationship tests");
        }

        @Override
        public AttributeCatalogRecord insertAttribute(AttributeCatalogWriteRequest request) {
            throw new UnsupportedOperationException("Not used in relationship tests");
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

    private static final class FailingObjectRegistrationWriteDao extends RecordingObjectRegistrationWriteDao {

        private FailingObjectRegistrationWriteDao(TransactionHarness harness) {
            super(harness);
        }

        @Override
        public MetadataChangeHistoryRecord insertMetadataChangeHistory(MetadataChangeHistoryWriteRequest request) {
            throw new IllegalStateException("audit failed");
        }
    }

    private static final class RecordingObjectExposureReadDao implements ObjectExposureReadDao {

        private final ObjectExposureRecord parentObject;
        private final ObjectExposureRecord childObject;

        private RecordingObjectExposureReadDao(ObjectExposureRecord parentObject, ObjectExposureRecord childObject) {
            this.parentObject = parentObject;
            this.childObject = childObject;
        }

        @Override
        public List<ObjectExposureRecord> findObjects(String clientId, String schemaCode, String lifecycleStatusCode) {
            throw new UnsupportedOperationException("Not used in relationship tests");
        }

        @Override
        public Optional<ObjectExposureRecord> findObject(String clientId, UUID objectId) {
            throw new UnsupportedOperationException("Not used in relationship tests");
        }

        @Override
        public Optional<ObjectExposureRecord> findObject(String schemaCode, String objectCode) {
            if (parentObject.schema_cd().equals(schemaCode) && parentObject.object_cd().equals(objectCode)) {
                return Optional.of(parentObject);
            }
            if (childObject.schema_cd().equals(schemaCode) && childObject.object_cd().equals(objectCode)) {
                return Optional.of(childObject);
            }
            return Optional.empty();
        }

        @Override
        public List<com.lextr.semanticlayer.model.AttributeExposureRecord> findAttributes(String clientId, UUID objectId) {
            throw new UnsupportedOperationException("Not used in relationship tests");
        }
    }

    private static final class RecordingRegistryReadDao implements RegistryReadDao {

        private final DataConnectionRecord parentConnection;
        private final DataConnectionRecord childConnection;

        private RecordingRegistryReadDao(DataConnectionRecord parentConnection, DataConnectionRecord childConnection) {
            this.parentConnection = parentConnection;
            this.childConnection = childConnection;
        }

        @Override
        public List<com.lextr.semanticlayer.model.SchemaCatalogRecord> findSchemas(String clientId, String lifecycleStatusCode) {
            throw new UnsupportedOperationException("Not used in relationship tests");
        }

        @Override
        public Optional<com.lextr.semanticlayer.model.SchemaCatalogRecord> findSchema(String clientId, String schemaCode) {
            throw new UnsupportedOperationException("Not used in relationship tests");
        }

        @Override
        public List<DataConnectionRecord> findConnections(String clientId, String engineCode, Boolean activeFlag) {
            throw new UnsupportedOperationException("Not used in relationship tests");
        }

        @Override
        public Optional<DataConnectionRecord> findConnection(String clientId, UUID connectionId) {
            if (parentConnection.connection_id().equals(connectionId)) {
                return Optional.of(parentConnection);
            }
            if (childConnection.connection_id().equals(connectionId)) {
                return Optional.of(childConnection);
            }
            return Optional.empty();
        }
    }

    private static final class RecordingRelationshipPolicyClient implements RelationshipPolicyClient {

        private final RelationshipPolicyDecisionDto decision;
        private RelationshipPolicyRequestDto lastRequest;

        private RecordingRelationshipPolicyClient(RelationshipPolicyDecisionDto decision) {
            this.decision = decision;
        }

        @Override
        public RelationshipPolicyDecisionDto validateCrossEngine(RelationshipPolicyRequestDto request) {
            lastRequest = request;
            return decision;
        }
    }

    private static final class TransactionHarness {

        private final List<Runnable> commitActions = new ArrayList<>();
        private boolean committed;
        private boolean rolledBack;

        private void addRelationship(SemanticRelationshipCatalogRecord record, List<SemanticRelationshipCatalogRecord> committedRecords) {
            commitActions.add(() -> committedRecords.add(record));
        }

        private void addWorkflowTask(WorkflowTaskRecord record, List<WorkflowTaskRecord> committedRecords) {
            commitActions.add(() -> committedRecords.add(record));
        }

        private void addMetadataChange(MetadataChangeHistoryRecord record, List<MetadataChangeHistoryRecord> committedRecords) {
            commitActions.add(() -> committedRecords.add(record));
        }
    }

    private static final class RecordingTransactionOperations implements TransactionOperations {

        private final TransactionHarness harness;

        private RecordingTransactionOperations(TransactionHarness harness) {
            this.harness = harness;
        }

        @Override
        public <T> T execute(TransactionCallback<T> action) {
            try {
                T result = action.doInTransaction(new NoOpTransactionStatus());
                harness.commitActions.forEach(Runnable::run);
                harness.committed = true;
                return result;
            } catch (RuntimeException exception) {
                harness.commitActions.clear();
                harness.rolledBack = true;
                throw exception;
            }
        }
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

    private static ObjectExposureRecord objectRecord(String clientId, String schemaCode, String objectCode, UUID connectionId) {
        return new ObjectExposureRecord(
                UUID.nameUUIDFromBytes((schemaCode + "." + objectCode).getBytes()),
                clientId,
                objectCode,
                objectCode,
                objectCode,
                "TABLE",
                schemaCode,
                connectionId,
                "ACTIVE",
                OffsetDateTime.parse("2026-06-17T10:15:30Z"),
                "producer",
                OffsetDateTime.parse("2026-06-17T10:15:30Z"),
                "producer"
        );
    }

    private static DataConnectionRecord connectionRecord(UUID connectionId, String engineCode) {
        return new DataConnectionRecord(
                connectionId,
                engineCode + "_CD",
                engineCode + " Connection",
                engineCode + " Connection",
                engineCode,
                "DATABASE",
                "DIRECT",
                "localhost",
                5432,
                "semantic",
                "meta",
                false,
                true,
                OffsetDateTime.parse("2026-06-17T10:15:30Z"),
                "producer",
                OffsetDateTime.parse("2026-06-17T10:15:30Z"),
                "producer"
        );
    }
}
