package com.lextr.semanticlayer.service.impl;

import com.lextr.semanticlayer.dao.ObjectRegistrationWriteDao;
import com.lextr.semanticlayer.dto.AttributeRegistrationRequestDto;
import com.lextr.semanticlayer.dto.AttributeRegistrationResponseDto;
import com.lextr.semanticlayer.dto.ObjectRegistrationRequestDto;
import com.lextr.semanticlayer.dto.ObjectRegistrationResponseDto;
import com.lextr.semanticlayer.dto.TaxonomyPolicyDecisionDto;
import com.lextr.semanticlayer.dto.TaxonomyPolicyRequestDto;
import com.lextr.semanticlayer.exception.ObjectRegistrationServiceException;
import com.lextr.semanticlayer.exception.PolicyViolationException;
import com.lextr.semanticlayer.model.AttributeCatalogRecord;
import com.lextr.semanticlayer.model.AttributeCatalogWriteRequest;
import com.lextr.semanticlayer.model.MetadataChangeHistoryRecord;
import com.lextr.semanticlayer.model.MetadataChangeHistoryWriteRequest;
import com.lextr.semanticlayer.model.ObjectCatalogRecord;
import com.lextr.semanticlayer.model.ObjectCatalogWriteRequest;
import com.lextr.semanticlayer.model.WorkflowTaskRecord;
import com.lextr.semanticlayer.model.WorkflowTaskWriteRequest;
import com.lextr.semanticlayer.service.ObjectRegistrationService;
import com.lextr.semanticlayer.service.TaxonomyPolicyClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ObjectRegistrationServiceImpl implements ObjectRegistrationService {

    private static final String WORKFLOW_TYPE_CD = "OBJECT_REGISTRATION";
    private static final String ENTITY_TYPE_CD = "OBJECT";
    private static final String TASK_STATUS_CD = "PENDING_APPROVAL";
    private static final String CHANGE_TYPE_CD = "REGISTERED";

    private final ObjectRegistrationWriteDao objectRegistrationWriteDao;
    private final TaxonomyPolicyClient taxonomyPolicyClient;
    private final TransactionOperations transactionOperations;

    @Autowired
    public ObjectRegistrationServiceImpl(ObjectRegistrationWriteDao objectRegistrationWriteDao,
                                         ObjectProvider<TaxonomyPolicyClient> taxonomyPolicyClientProvider,
                                         @Qualifier("semanticLayerTransactionOperations")
                                         ObjectProvider<TransactionOperations> transactionOperationsProvider) {
        this(
                objectRegistrationWriteDao,
                taxonomyPolicyClientProvider.getIfAvailable(() -> request -> new TaxonomyPolicyDecisionDto(true, null, null)),
                transactionOperationsProvider.getIfAvailable(NoOpTransactionOperations::new)
        );
    }

    ObjectRegistrationServiceImpl(ObjectRegistrationWriteDao objectRegistrationWriteDao,
                                  TaxonomyPolicyClient taxonomyPolicyClient,
                                  TransactionOperations transactionOperations) {
        this.objectRegistrationWriteDao = objectRegistrationWriteDao;
        this.taxonomyPolicyClient = taxonomyPolicyClient;
        this.transactionOperations = transactionOperations;
    }

    @Override
    public ObjectRegistrationResponseDto registerObject(ObjectRegistrationRequestDto request) {
        validateTaxonomyPolicy(request);

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        UUID objectId = UUID.randomUUID();
        UUID workflowTaskId = UUID.randomUUID();
        UUID changeHistoryId = UUID.randomUUID();

        try {
            return transactionOperations.execute(status -> persistRegistration(request, now, objectId, workflowTaskId, changeHistoryId));
        } catch (PolicyViolationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new ObjectRegistrationServiceException("Unable to register object", exception);
        }
    }

    private void validateTaxonomyPolicy(ObjectRegistrationRequestDto request) {
        request.attributes().forEach(attribute -> {
            TaxonomyPolicyDecisionDto decision = taxonomyPolicyClient.validateJurisdiction(new TaxonomyPolicyRequestDto(
                    request.client_id(),
                    attribute.taxonomy_cd(),
                    attribute.taxonomy_source_cd(),
                    attribute.taxonomy_jurisdiction_cd()
            ));
            if (!decision.allowed()) {
                throw new PolicyViolationException(decision.code(), decision.message());
            }
        });
    }

    private ObjectRegistrationResponseDto persistRegistration(ObjectRegistrationRequestDto request,
                                                              OffsetDateTime now,
                                                              UUID objectId,
                                                              UUID workflowTaskId,
                                                              UUID changeHistoryId) {
        ObjectCatalogRecord object = objectRegistrationWriteDao.insertDraftObject(new ObjectCatalogWriteRequest(
                objectId,
                request.client_id(),
                request.object_cd(),
                request.object_nm(),
                request.object_type_cd(),
                request.schema_cd(),
                request.connection_id(),
                now,
                request.registered_by(),
                now,
                request.registered_by()
        ));

        List<AttributeRegistrationResponseDto> attributes = request.attributes().stream()
                .collect(Collectors.toMap(
                        AttributeRegistrationRequestDto::attribute_cd,
                        Function.identity(),
                        (first, duplicate) -> first,
                        LinkedHashMap::new
                ))
                .values()
                .stream()
                .map(attribute -> objectRegistrationWriteDao.insertAttribute(new AttributeCatalogWriteRequest(
                        UUID.randomUUID(),
                        object.object_id(),
                        request.client_id(),
                        attribute.attribute_cd(),
                        attribute.attribute_nm(),
                        attribute.data_type_cd(),
                        attribute.taxonomy_cd(),
                        attribute.taxonomy_source_cd(),
                        attribute.taxonomy_jurisdiction_cd(),
                        now,
                        request.registered_by(),
                        now,
                        request.registered_by()
                )))
                .map(this::toAttributeResponse)
                .toList();

        WorkflowTaskRecord workflowTask = objectRegistrationWriteDao.insertWorkflowTask(new WorkflowTaskWriteRequest(
                workflowTaskId,
                request.client_id(),
                WORKFLOW_TYPE_CD,
                ENTITY_TYPE_CD,
                object.object_id(),
                TASK_STATUS_CD,
                now,
                request.registered_by(),
                now,
                request.registered_by()
        ));

        MetadataChangeHistoryRecord changeHistory = objectRegistrationWriteDao.insertMetadataChangeHistory(new MetadataChangeHistoryWriteRequest(
                changeHistoryId,
                request.client_id(),
                ENTITY_TYPE_CD,
                object.object_id(),
                CHANGE_TYPE_CD,
                "Registered draft object",
                now,
                request.registered_by()
        ));

        return new ObjectRegistrationResponseDto(
                object.object_id(),
                object.object_cd(),
                object.object_nm(),
                object.lifecycle_status_cd(),
                workflowTask.workflow_task_id(),
                workflowTask.task_status_cd(),
                changeHistory.change_history_id(),
                attributes
        );
    }

    private AttributeRegistrationResponseDto toAttributeResponse(AttributeCatalogRecord record) {
        return new AttributeRegistrationResponseDto(
                record.attribute_id(),
                record.attribute_cd(),
                record.attribute_nm(),
                record.taxonomy_cd(),
                record.taxonomy_source_cd(),
                record.taxonomy_jurisdiction_cd()
        );
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
