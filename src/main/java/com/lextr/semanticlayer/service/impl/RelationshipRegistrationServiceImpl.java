package com.lextr.semanticlayer.service.impl;

import com.lextr.semanticlayer.dao.ObjectExposureReadDao;
import com.lextr.semanticlayer.dao.ObjectRegistrationWriteDao;
import com.lextr.semanticlayer.dao.RegistryReadDao;
import com.lextr.semanticlayer.dao.RelationshipRegistrationWriteDao;
import com.lextr.semanticlayer.dto.RelationshipPolicyDecisionDto;
import com.lextr.semanticlayer.dto.RelationshipPolicyRequestDto;
import com.lextr.semanticlayer.dto.RelationshipRegistrationRequestDto;
import com.lextr.semanticlayer.dto.RelationshipRegistrationResponseDto;
import com.lextr.semanticlayer.exception.SemanticLayerException;
import com.lextr.semanticlayer.exception.PolicyViolationException;
import com.lextr.semanticlayer.exception.RelationshipAlreadyExistsException;
import com.lextr.semanticlayer.exception.RelationshipRegistrationServiceException;
import com.lextr.semanticlayer.model.DataConnectionRecord;
import com.lextr.semanticlayer.model.MetadataChangeHistoryWriteRequest;
import com.lextr.semanticlayer.model.ObjectExposureRecord;
import com.lextr.semanticlayer.model.RelationshipGraphProjectionRequest;
import com.lextr.semanticlayer.model.SemanticRelationshipCatalogRecord;
import com.lextr.semanticlayer.model.SemanticRelationshipCatalogWriteRequest;
import com.lextr.semanticlayer.model.SemanticRelationshipProjectionSyncWriteRequest;
import com.lextr.semanticlayer.model.WorkflowTaskWriteRequest;
import com.lextr.semanticlayer.service.RelationshipGraphProjectionClient;
import com.lextr.semanticlayer.service.RelationshipPolicyClient;
import com.lextr.semanticlayer.service.RelationshipRegistrationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Service
public class RelationshipRegistrationServiceImpl implements RelationshipRegistrationService {

    private static final String ACTIVE_LIFECYCLE_STATUS_CD = "DRAFT";
    private static final String WORKFLOW_TYPE_CD = "RELATIONSHIP_REGISTRATION";
    private static final String ENTITY_TYPE_CD = "RELATIONSHIP";
    private static final String TASK_STATUS_CD = "PENDING_APPROVAL";
    private static final String CHANGE_TYPE_CD = "REGISTERED";

    private final RelationshipRegistrationWriteDao relationshipRegistrationWriteDao;
    private final ObjectRegistrationWriteDao objectRegistrationWriteDao;
    private final ObjectExposureReadDao objectExposureReadDao;
    private final RegistryReadDao registryReadDao;
    private final RelationshipGraphProjectionClient relationshipGraphProjectionClient;
    private final RelationshipPolicyClient relationshipPolicyClient;
    private final TransactionOperations transactionOperations;

    @Autowired
    public RelationshipRegistrationServiceImpl(RelationshipRegistrationWriteDao relationshipRegistrationWriteDao,
                                               ObjectRegistrationWriteDao objectRegistrationWriteDao,
                                               ObjectExposureReadDao objectExposureReadDao,
                                               RegistryReadDao registryReadDao,
                                               ObjectProvider<RelationshipGraphProjectionClient> relationshipGraphProjectionClientProvider,
                                               ObjectProvider<RelationshipPolicyClient> relationshipPolicyClientProvider,
                                               @Qualifier("semanticLayerTransactionOperations")
                                               ObjectProvider<TransactionOperations> transactionOperationsProvider) {
        this(
                relationshipRegistrationWriteDao,
                objectRegistrationWriteDao,
                objectExposureReadDao,
                registryReadDao,
                relationshipGraphProjectionClientProvider.getIfAvailable(() -> request -> false),
                relationshipPolicyClientProvider.getIfAvailable(() -> request -> new RelationshipPolicyDecisionDto(true, null, null)),
                transactionOperationsProvider.getIfAvailable(NoOpTransactionOperations::new)
        );
    }

    RelationshipRegistrationServiceImpl(RelationshipRegistrationWriteDao relationshipRegistrationWriteDao,
                                        ObjectRegistrationWriteDao objectRegistrationWriteDao,
                                        ObjectExposureReadDao objectExposureReadDao,
                                        RegistryReadDao registryReadDao,
                                        RelationshipGraphProjectionClient relationshipGraphProjectionClient,
                                        RelationshipPolicyClient relationshipPolicyClient,
                                        TransactionOperations transactionOperations) {
        this.relationshipRegistrationWriteDao = relationshipRegistrationWriteDao;
        this.objectRegistrationWriteDao = objectRegistrationWriteDao;
        this.objectExposureReadDao = objectExposureReadDao;
        this.registryReadDao = registryReadDao;
        this.relationshipGraphProjectionClient = relationshipGraphProjectionClient;
        this.relationshipPolicyClient = relationshipPolicyClient;
        this.transactionOperations = transactionOperations;
    }

    @Override
    public RelationshipRegistrationResponseDto registerRelationship(RelationshipRegistrationRequestDto request) {
        ObjectExposureRecord parentObject = findRequiredObject(request.parent_schema_cd(), request.parent_object_cd(), "parent");
        ObjectExposureRecord childObject = findRequiredObject(request.child_schema_cd(), request.child_object_cd(), "child");
        String clientId = resolveClientId(parentObject, childObject);
        DataConnectionRecord parentConnection = findRequiredConnection(clientId, parentObject.connection_id(), "parent");
        DataConnectionRecord childConnection = findRequiredConnection(clientId, childObject.connection_id(), "child");
        boolean crossEngine = isCrossEngine(parentConnection, childConnection);

        validateRelationshipPolicy(request, clientId, parentConnection.engine_cd(), childConnection.engine_cd(), crossEngine);

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        UUID relationshipEntityId = relationshipEntityId(request.relationship_cd());
        UUID workflowTaskId = UUID.randomUUID();
        UUID changeHistoryId = UUID.randomUUID();

        try {
            return transactionOperations.execute(status -> persistRegistration(
                    request,
                    clientId,
                    crossEngine,
                    parentConnection.engine_cd(),
                    childConnection.engine_cd(),
                    now,
                    relationshipEntityId,
                    workflowTaskId,
                    changeHistoryId
            ));
        } catch (PolicyViolationException exception) {
            throw exception;
        } catch (DuplicateKeyException exception) {
            throw new RelationshipAlreadyExistsException(request.relationship_cd());
        } catch (RuntimeException exception) {
            throw new RelationshipRegistrationServiceException("Unable to register relationship", exception);
        }
    }

    private void validateRelationshipPolicy(RelationshipRegistrationRequestDto request,
                                            String clientId,
                                            String parentEngineCode,
                                            String childEngineCode,
                                            boolean crossEngine) {
        RelationshipPolicyDecisionDto decision = relationshipPolicyClient.validateCrossEngine(
                new RelationshipPolicyRequestDto(
                        clientId,
                        request.relationship_cd(),
                        parentEngineCode,
                        childEngineCode,
                        crossEngine
                )
        );
        if (!decision.allowed()) {
            throw new PolicyViolationException(decision.code(), decision.message());
        }
    }

    private RelationshipRegistrationResponseDto persistRegistration(RelationshipRegistrationRequestDto request,
                                                                    String clientId,
                                                                    boolean crossEngine,
                                                                    String parentEngineCode,
                                                                    String childEngineCode,
                                                                    OffsetDateTime now,
                                                                    UUID relationshipEntityId,
                                                                    UUID workflowTaskId,
                                                                    UUID changeHistoryId) {
        SemanticRelationshipCatalogRecord record = relationshipRegistrationWriteDao.insertRelationship(
                new SemanticRelationshipCatalogWriteRequest(
                        request.relationship_cd(),
                        request.parent_schema_cd(),
                        request.parent_object_cd(),
                        request.parent_attribute_cd(),
                        request.child_schema_cd(),
                        request.child_object_cd(),
                        request.child_attribute_cd(),
                        request.relationship_type_cd(),
                        request.cardinality_cd(),
                        request.join_type_cd(),
                        request.is_enforced_flg(),
                        request.is_nullable_flg(),
                        crossEngine,
                        request.relationship_desc(),
                        request.ai_join_guidance_txt(),
                        ACTIVE_LIFECYCLE_STATUS_CD,
                        now,
                        request.registered_by(),
                        now,
                        request.registered_by()
                )
        );

        objectRegistrationWriteDao.insertWorkflowTask(new WorkflowTaskWriteRequest(
                workflowTaskId,
                clientId,
                WORKFLOW_TYPE_CD,
                ENTITY_TYPE_CD,
                relationshipEntityId,
                TASK_STATUS_CD,
                now,
                request.registered_by(),
                now,
                request.registered_by()
        ));

        objectRegistrationWriteDao.insertMetadataChangeHistory(new MetadataChangeHistoryWriteRequest(
                changeHistoryId,
                clientId,
                ENTITY_TYPE_CD,
                relationshipEntityId,
                CHANGE_TYPE_CD,
                "Registered relationship " + request.relationship_cd(),
                now,
                request.registered_by()
        ));

        SemanticRelationshipCatalogRecord projectedRecord = projectRelationshipGraph(
                request,
                record,
                parentEngineCode,
                childEngineCode,
                now
        );

        return toResponse(projectedRecord);
    }

    private RelationshipRegistrationResponseDto toResponse(SemanticRelationshipCatalogRecord record) {
        return new RelationshipRegistrationResponseDto(
                record.id(),
                record.relationship_cd(),
                record.parent_schema_cd(),
                record.parent_object_cd(),
                record.parent_attribute_cd(),
                record.child_schema_cd(),
                record.child_object_cd(),
                record.child_attribute_cd(),
                record.relationship_type_cd(),
                record.cardinality_cd(),
                record.join_type_cd(),
                record.is_enforced_flg(),
                record.is_nullable_flg(),
                record.is_cross_engine_flg(),
                record.relationship_desc(),
                record.ai_join_guidance_txt(),
                record.neo4j_synced_ts(),
                record.lifecycle_status_cd()
        );
    }

    private ObjectExposureRecord findRequiredObject(String schemaCode, String objectCode, String role) {
        return objectExposureReadDao.findObject(schemaCode, objectCode)
                .orElseThrow(() -> new RelationshipRegistrationServiceException(
                        "Unable to resolve " + role + " object " + schemaCode + "." + objectCode
                ));
    }

    private DataConnectionRecord findRequiredConnection(String clientId, UUID connectionId, String role) {
        if (connectionId == null) {
            throw new RelationshipRegistrationServiceException("Unable to resolve " + role + " object connection");
        }
        return registryReadDao.findConnection(clientId, connectionId)
                .orElseThrow(() -> new RelationshipRegistrationServiceException(
                        "Unable to resolve " + role + " connection " + connectionId
                ));
    }

    private String resolveClientId(ObjectExposureRecord parentObject, ObjectExposureRecord childObject) {
        String parentClientId = parentObject.client_id();
        String childClientId = childObject.client_id();
        if (parentClientId == null || childClientId == null) {
            throw new RelationshipRegistrationServiceException("Relationship objects must resolve to a client_id");
        }
        if (!parentClientId.equals(childClientId)) {
            throw new RelationshipRegistrationServiceException("Relationship objects resolve to different client_id values");
        }
        return parentClientId;
    }

    private boolean isCrossEngine(DataConnectionRecord parentConnection, DataConnectionRecord childConnection) {
        if (parentConnection.engine_cd() == null || childConnection.engine_cd() == null) {
            throw new RelationshipRegistrationServiceException("Relationship connections must resolve to engine_cd values");
        }
        return !parentConnection.engine_cd().equalsIgnoreCase(childConnection.engine_cd());
    }

    private UUID relationshipEntityId(String relationshipCode) {
        try {
            return UUID.nameUUIDFromBytes(relationshipCode.getBytes(StandardCharsets.UTF_8));
        } catch (RuntimeException exception) {
            throw new SemanticLayerException("Unable to derive relationship entity identifier", exception);
        }
    }

    private SemanticRelationshipCatalogRecord projectRelationshipGraph(RelationshipRegistrationRequestDto request,
                                                                       SemanticRelationshipCatalogRecord record,
                                                                       String parentEngineCode,
                                                                       String childEngineCode,
                                                                       OffsetDateTime projectedAt) {
        boolean projected = relationshipGraphProjectionClient.projectRelationship(new RelationshipGraphProjectionRequest(
                record.relationship_cd(),
                record.parent_schema_cd(),
                record.parent_object_cd(),
                record.parent_attribute_cd(),
                parentEngineCode,
                record.child_schema_cd(),
                record.child_object_cd(),
                record.child_attribute_cd(),
                childEngineCode,
                record.relationship_type_cd(),
                record.cardinality_cd(),
                record.join_type_cd(),
                record.is_enforced_flg(),
                record.is_nullable_flg(),
                record.is_cross_engine_flg(),
                record.relationship_desc(),
                record.ai_join_guidance_txt(),
                record.lifecycle_status_cd(),
                projectedAt,
                request.registered_by()
        ));
        if (!projected) {
            return record;
        }
        return relationshipRegistrationWriteDao.updateNeo4jProjectionSync(new SemanticRelationshipProjectionSyncWriteRequest(
                record.relationship_cd(),
                projectedAt,
                projectedAt,
                request.registered_by()
        ));
    }

    private static final class NoOpTransactionOperations implements TransactionOperations {

        @Override
        public <T> T execute(TransactionCallback<T> action) {
            return action.doInTransaction(new NoOpTransactionStatus());
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
}
