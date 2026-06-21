package com.lextr.semanticlayer.service.impl;

import com.lextr.semanticlayer.dao.AttributePairingRegistrationWriteDao;
import com.lextr.semanticlayer.dao.AttributePairingResolutionDao;
import com.lextr.semanticlayer.dao.ObjectExposureReadDao;
import com.lextr.semanticlayer.dto.AttributePairingPolicyDecisionDto;
import com.lextr.semanticlayer.dto.AttributePairingPolicyRequestDto;
import com.lextr.semanticlayer.dto.AttributePairingRegistrationRequestDto;
import com.lextr.semanticlayer.dto.AttributePairingRegistrationResponseDto;
import com.lextr.semanticlayer.exception.AttributePairingRegistrationServiceException;
import com.lextr.semanticlayer.exception.PolicyViolationException;
import com.lextr.semanticlayer.exception.RegistryResourceNotFoundException;
import com.lextr.semanticlayer.exception.SemanticLayerException;
import com.lextr.semanticlayer.model.AttributeExposureRecord;
import com.lextr.semanticlayer.model.AttributePairingCatalogRecord;
import com.lextr.semanticlayer.model.AttributePairingCatalogWriteRequest;
import com.lextr.semanticlayer.model.FilterLookupMetadataChangeHistoryWriteRequest;
import com.lextr.semanticlayer.model.FilterLookupWorkflowTaskWriteRequest;
import com.lextr.semanticlayer.model.ObjectExposureRecord;
import com.lextr.semanticlayer.service.AttributePairingPolicyClient;
import com.lextr.semanticlayer.service.AttributePairingRegistrationService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

@Service
public class AttributePairingRegistrationServiceImpl implements AttributePairingRegistrationService {

    private static final String ENTITY_TYPE_CD = "ATTRIBUTE_PAIRING";
    private static final String CHANGE_TYPE_CD = "REGISTERED";
    private static final String TASK_TYPE_CD = "ATTRIBUTE_PAIRING_REGISTRATION";
    private static final String TASK_STATUS_CD = "PENDING";
    private static final String LIFECYCLE_STATUS_CD = "DRAFT";
    private static final String GOVERNANCE_REVIEW_STATUS_CD = "PENDING";

    private final ObjectExposureReadDao objectExposureReadDao;
    private final AttributePairingRegistrationWriteDao attributePairingRegistrationWriteDao;
    private final AttributePairingResolutionDao attributePairingResolutionDao;
    private final AttributePairingPolicyClient attributePairingPolicyClient;
    private final TransactionOperations transactionOperations;

    @Autowired
    public AttributePairingRegistrationServiceImpl(
            ObjectProvider<ObjectExposureReadDao> objectExposureReadDaoProvider,
            ObjectProvider<AttributePairingRegistrationWriteDao> attributePairingRegistrationWriteDaoProvider,
            ObjectProvider<AttributePairingResolutionDao> attributePairingResolutionDaoProvider,
            ObjectProvider<AttributePairingPolicyClient> attributePairingPolicyClientProvider,
            @Qualifier("semanticLayerTransactionOperations")
            ObjectProvider<TransactionOperations> transactionOperationsProvider
    ) {
        this(
                objectExposureReadDaoProvider.getIfAvailable(MissingObjectExposureReadDao::new),
                attributePairingRegistrationWriteDaoProvider.getIfAvailable(MissingAttributePairingRegistrationWriteDao::new),
                attributePairingResolutionDaoProvider.getIfAvailable(MissingAttributePairingResolutionDao::new),
                attributePairingPolicyClientProvider.getIfAvailable(
                        () -> request -> new AttributePairingPolicyDecisionDto(true, null, null)
                ),
                transactionOperationsProvider.getIfAvailable(NoOpTransactionOperations::new)
        );
    }

    AttributePairingRegistrationServiceImpl(ObjectExposureReadDao objectExposureReadDao,
                                            AttributePairingRegistrationWriteDao attributePairingRegistrationWriteDao,
                                            AttributePairingResolutionDao attributePairingResolutionDao,
                                            AttributePairingPolicyClient attributePairingPolicyClient,
                                            TransactionOperations transactionOperations) {
        this.objectExposureReadDao = objectExposureReadDao;
        this.attributePairingRegistrationWriteDao = attributePairingRegistrationWriteDao;
        this.attributePairingResolutionDao = attributePairingResolutionDao;
        this.attributePairingPolicyClient = attributePairingPolicyClient;
        this.transactionOperations = transactionOperations;
    }

    @Override
    public AttributePairingRegistrationResponseDto registerPairing(AttributePairingRegistrationRequestDto request) {
        ObjectExposureRecord object = findRequiredObject(request.schema_cd(), request.object_cd(), request.client_id());
        List<AttributeExposureRecord> attributes = objectExposureReadDao.findAttributes(request.client_id(), object.object_id());

        ensureAttributeExists(attributes, request.display_attribute_cd(), "display");
        ensureAttributeExists(attributes, request.filter_attribute_cd(), "filter");
        enforceIndexGate(request);
        validateCrossEnginePolicy(request);

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        try {
            return transactionOperations.execute(status -> persistRegistration(request, now));
        } catch (PolicyViolationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new AttributePairingRegistrationServiceException("Unable to register attribute pairing", exception);
        }
    }

    private ObjectExposureRecord findRequiredObject(String schemaCode, String objectCode, String clientId) {
        ObjectExposureRecord object = objectExposureReadDao.findObject(schemaCode, objectCode)
                .orElseThrow(() -> new RegistryResourceNotFoundException("object", schemaCode + "." + objectCode));
        if (object.client_id() != null && !object.client_id().equals(clientId)) {
            throw new RegistryResourceNotFoundException("object", schemaCode + "." + objectCode);
        }
        return object;
    }

    private void ensureAttributeExists(List<AttributeExposureRecord> attributes, String attributeCode, String role) {
        boolean exists = attributes.stream().anyMatch(attribute -> attributeCode.equals(attribute.attribute_cd()));
        if (!exists) {
            throw new RegistryResourceNotFoundException(role + " attribute", attributeCode);
        }
    }

    private void enforceIndexGate(AttributePairingRegistrationRequestDto request) {
        boolean requestFlag = Boolean.TRUE.equals(request.filter_attribute_indexed_flg());
        boolean databaseFlag = attributePairingResolutionDao.isAttributeIndexed(
                request.schema_cd(),
                request.object_cd(),
                request.filter_attribute_cd()
        );
        if (!requestFlag || !databaseFlag) {
            throw new AttributePairingRegistrationServiceException(
                    "Filter attribute " + request.filter_attribute_cd() + " is not indexed and cannot be activated"
            );
        }
    }

    private void validateCrossEnginePolicy(AttributePairingRegistrationRequestDto request) {
        AttributePairingPolicyDecisionDto decision = attributePairingPolicyClient.validateCrossEngine(
                new AttributePairingPolicyRequestDto(
                        request.client_id(),
                        request.pairing_cd(),
                        request.is_cross_engine_flg()
                )
        );
        if (!decision.allowed()) {
            throw new PolicyViolationException(decision.code(), decision.message());
        }
    }

    private AttributePairingRegistrationResponseDto persistRegistration(AttributePairingRegistrationRequestDto request,
                                                                        OffsetDateTime now) {
        AttributePairingCatalogRecord record = attributePairingRegistrationWriteDao.insertPairing(
                new AttributePairingCatalogWriteRequest(
                        request.pairing_cd(),
                        request.pairing_nm(),
                        request.schema_cd(),
                        request.object_cd(),
                        request.display_attribute_cd(),
                        request.filter_attribute_cd(),
                        request.pairing_type_cd(),
                        request.lookup_strategy_cd(),
                        request.lookup_inline_map_jsonb(),
                        request.lookup_sql_template_txt(),
                        request.lookup_cache_enabled_flg() == null || request.lookup_cache_enabled_flg(),
                        request.lookup_cache_ttl_seconds_nbr() == null ? 3600 : request.lookup_cache_ttl_seconds_nbr(),
                        request.cardinality_cd(),
                        request.is_bidirectional_flg(),
                        request.is_cross_engine_flg(),
                        true,
                        request.filter_attribute_index_type_cd(),
                        request.performance_gain_pct_est_nbr(),
                        request.ai_context_txt(),
                        request.client_id(),
                        LIFECYCLE_STATUS_CD,
                        GOVERNANCE_REVIEW_STATUS_CD,
                        1,
                        now,
                        request.registered_by(),
                        now,
                        request.registered_by()
                )
        );

        attributePairingRegistrationWriteDao.insertWorkflowTask(new FilterLookupWorkflowTaskWriteRequest(
                TASK_TYPE_CD,
                ENTITY_TYPE_CD,
                request.pairing_cd(),
                TASK_STATUS_CD,
                request.registered_by(),
                now,
                null,
                null,
                "Review attribute pairing " + request.pairing_cd(),
                request.client_id(),
                null,
                null,
                null
        ));

        attributePairingRegistrationWriteDao.insertMetadataChangeHistory(new FilterLookupMetadataChangeHistoryWriteRequest(
                ENTITY_TYPE_CD,
                request.pairing_cd(),
                CHANGE_TYPE_CD,
                request.registered_by(),
                now,
                null,
                null,
                "Registered attribute pairing " + request.pairing_cd() + " for " + request.object_cd()
        ));

        return new AttributePairingRegistrationResponseDto(
                record.id(),
                record.pairing_cd(),
                record.pairing_nm(),
                record.schema_cd(),
                record.object_cd(),
                record.display_attribute_cd(),
                record.filter_attribute_cd(),
                record.lifecycle_status_cd(),
                record.governance_review_status_cd()
        );
    }

    private static final class MissingObjectExposureReadDao implements ObjectExposureReadDao {

        @Override
        public List<ObjectExposureRecord> findObjects(String clientId, String schemaCode, String lifecycleStatusCode) {
            throw new SemanticLayerException("ObjectExposureReadDao is not configured");
        }

        @Override
        public Optional<ObjectExposureRecord> findObject(String clientId, java.util.UUID objectId) {
            throw new SemanticLayerException("ObjectExposureReadDao is not configured");
        }

        @Override
        public Optional<ObjectExposureRecord> findObject(String schemaCode, String objectCode) {
            throw new SemanticLayerException("ObjectExposureReadDao is not configured");
        }

        @Override
        public List<AttributeExposureRecord> findAttributes(String clientId, java.util.UUID objectId) {
            throw new SemanticLayerException("ObjectExposureReadDao is not configured");
        }
    }

    private static final class MissingAttributePairingRegistrationWriteDao implements AttributePairingRegistrationWriteDao {

        @Override
        public AttributePairingCatalogRecord insertPairing(AttributePairingCatalogWriteRequest request) {
            throw new SemanticLayerException("AttributePairingRegistrationWriteDao is not configured");
        }

        @Override
        public com.lextr.semanticlayer.model.FilterLookupWorkflowTaskRecord insertWorkflowTask(
                FilterLookupWorkflowTaskWriteRequest request
        ) {
            throw new SemanticLayerException("AttributePairingRegistrationWriteDao is not configured");
        }

        @Override
        public com.lextr.semanticlayer.model.FilterLookupMetadataChangeHistoryRecord insertMetadataChangeHistory(
                FilterLookupMetadataChangeHistoryWriteRequest request
        ) {
            throw new SemanticLayerException("AttributePairingRegistrationWriteDao is not configured");
        }
    }

    private static final class MissingAttributePairingResolutionDao implements AttributePairingResolutionDao {

        @Override
        public Optional<AttributePairingCatalogRecord> findPairing(String clientId, String pairingCode) {
            throw new SemanticLayerException("AttributePairingResolutionDao is not configured");
        }

        @Override
        public Optional<AttributePairingCatalogRecord> findActivePairing(String clientId,
                                                                         String schemaCode,
                                                                         String objectCode,
                                                                         String displayAttributeCode) {
            throw new SemanticLayerException("AttributePairingResolutionDao is not configured");
        }

        @Override
        public boolean isAttributeIndexed(String schemaCode, String objectCode, String attributeCode) {
            throw new SemanticLayerException("AttributePairingResolutionDao is not configured");
        }

        @Override
        public Optional<com.lextr.semanticlayer.model.AttributePairingValueCacheRecord> findCachedValue(
                String pairingCode,
                String clientId,
                String displayValue,
                OffsetDateTime asOfTimestamp
        ) {
            throw new SemanticLayerException("AttributePairingResolutionDao is not configured");
        }

        @Override
        public com.lextr.semanticlayer.model.AttributePairingValueCacheRecord upsertCachedValue(
                com.lextr.semanticlayer.model.AttributePairingValueCacheWriteRequest request
        ) {
            throw new SemanticLayerException("AttributePairingResolutionDao is not configured");
        }

        @Override
        public com.lextr.semanticlayer.model.AttributePairingValueCacheRecord recordCacheHit(
                com.lextr.semanticlayer.model.AttributePairingCacheHitWriteRequest request
        ) {
            throw new SemanticLayerException("AttributePairingResolutionDao is not configured");
        }
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
