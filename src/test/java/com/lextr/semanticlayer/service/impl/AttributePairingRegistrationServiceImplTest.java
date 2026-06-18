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
import com.lextr.semanticlayer.model.AttributeExposureRecord;
import com.lextr.semanticlayer.model.AttributePairingCatalogRecord;
import com.lextr.semanticlayer.model.AttributePairingCatalogWriteRequest;
import com.lextr.semanticlayer.model.FilterLookupMetadataChangeHistoryRecord;
import com.lextr.semanticlayer.model.FilterLookupMetadataChangeHistoryWriteRequest;
import com.lextr.semanticlayer.model.FilterLookupWorkflowTaskRecord;
import com.lextr.semanticlayer.model.FilterLookupWorkflowTaskWriteRequest;
import com.lextr.semanticlayer.model.ObjectExposureRecord;
import com.lextr.semanticlayer.service.AttributePairingPolicyClient;
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

class AttributePairingRegistrationServiceImplTest {

    @Test
    void registersPairingAtomicallyAndWritesWorkflowAndAuditRows() {
        TransactionHarness harness = new TransactionHarness();
        RecordingObjectExposureReadDao objectReadDao = new RecordingObjectExposureReadDao();
        objectReadDao.object = objectRecord();
        objectReadDao.attributes = List.of(attribute("customer_nm"), attribute("customer_id"));
        RecordingAttributePairingResolutionDao resolutionDao = new RecordingAttributePairingResolutionDao(true);
        RecordingAttributePairingRegistrationWriteDao writeDao = new RecordingAttributePairingRegistrationWriteDao(harness);
        RecordingAttributePairingPolicyClient policyClient = new RecordingAttributePairingPolicyClient(
                new AttributePairingPolicyDecisionDto(true, null, null)
        );
        AttributePairingRegistrationServiceImpl service = new AttributePairingRegistrationServiceImpl(
                objectReadDao,
                writeDao,
                resolutionDao,
                policyClient,
                new RecordingTransactionOperations(harness)
        );

        AttributePairingRegistrationResponseDto response = service.registerPairing(request(false));

        assertTrue(harness.committed);
        assertEquals(1, writeDao.committedPairings.size());
        assertEquals(1, writeDao.committedWorkflowTasks.size());
        assertEquals(1, writeDao.committedMetadataChanges.size());
        assertEquals("CUSTOMER_NAME_TO_ID", writeDao.pairingRequest.pairing_cd());
        assertEquals("customer_nm", writeDao.pairingRequest.display_attribute_cd());
        assertEquals("customer_id", writeDao.pairingRequest.filter_attribute_cd());
        assertEquals("ACTIVE", writeDao.pairingRequest.lifecycle_status_cd());
        assertEquals("PENDING", writeDao.workflowTaskRequest.task_status_cd());
        assertEquals("ATTRIBUTE_PAIRING_REGISTRATION", writeDao.workflowTaskRequest.task_type_cd());
        assertEquals("REGISTERED", writeDao.metadataChangeRequest.change_type_cd());
        assertTrue(writeDao.metadataChangeRequest.change_reason_txt().contains("CUSTOMER_NAME_TO_ID"));
        assertEquals(1, policyClient.requests.size());
        assertEquals("CUSTOMER_NAME_TO_ID", policyClient.requests.get(0).pairing_cd());
        assertEquals(false, policyClient.requests.get(0).is_cross_engine_flg());
        assertEquals("customer_nm", response.display_attribute_cd());
        assertEquals("customer_id", response.filter_attribute_cd());
        assertEquals("ACTIVE", response.lifecycle_status_cd());
    }

    @Test
    void indexGateBlocksActivationWhenFilterAttributeNotIndexed() {
        TransactionHarness harness = new TransactionHarness();
        RecordingObjectExposureReadDao objectReadDao = new RecordingObjectExposureReadDao();
        objectReadDao.object = objectRecord();
        objectReadDao.attributes = List.of(attribute("customer_nm"), attribute("customer_id"));
        RecordingAttributePairingResolutionDao resolutionDao = new RecordingAttributePairingResolutionDao(false);
        RecordingAttributePairingRegistrationWriteDao writeDao = new RecordingAttributePairingRegistrationWriteDao(harness);
        AttributePairingRegistrationServiceImpl service = new AttributePairingRegistrationServiceImpl(
                objectReadDao,
                writeDao,
                resolutionDao,
                request -> new AttributePairingPolicyDecisionDto(true, null, null),
                new RecordingTransactionOperations(harness)
        );

        AttributePairingRegistrationServiceException exception = assertThrows(
                AttributePairingRegistrationServiceException.class,
                () -> service.registerPairing(request(false))
        );

        assertTrue(exception.getMessage().contains("is not indexed"));
        assertEquals(0, writeDao.committedPairings.size());
        assertEquals(0, writeDao.committedWorkflowTasks.size());
        assertEquals(0, writeDao.committedMetadataChanges.size());
        assertTrue(!harness.committed);
    }

    @Test
    void policyBlockSurfacesCodeBeforePersistingWrites() {
        TransactionHarness harness = new TransactionHarness();
        RecordingObjectExposureReadDao objectReadDao = new RecordingObjectExposureReadDao();
        objectReadDao.object = objectRecord();
        objectReadDao.attributes = List.of(attribute("customer_nm"), attribute("customer_id"));
        RecordingAttributePairingRegistrationWriteDao writeDao = new RecordingAttributePairingRegistrationWriteDao(harness);
        RecordingAttributePairingPolicyClient policyClient = new RecordingAttributePairingPolicyClient(
                new AttributePairingPolicyDecisionDto(false, "POL-CE-002", "POL-CE-002: cross-engine pairing is not allowed")
        );
        AttributePairingRegistrationServiceImpl service = new AttributePairingRegistrationServiceImpl(
                objectReadDao,
                writeDao,
                new RecordingAttributePairingResolutionDao(true),
                policyClient,
                new RecordingTransactionOperations(harness)
        );

        PolicyViolationException exception = assertThrows(
                PolicyViolationException.class,
                () -> service.registerPairing(request(true))
        );

        assertEquals("POL-CE-002", exception.code());
        assertEquals("POL-CE-002: cross-engine pairing is not allowed", exception.getMessage());
        assertEquals(0, writeDao.committedPairings.size());
        assertEquals(0, writeDao.committedWorkflowTasks.size());
        assertEquals(0, writeDao.committedMetadataChanges.size());
        assertEquals(1, policyClient.requests.size());
        assertEquals(true, policyClient.requests.get(0).is_cross_engine_flg());
    }

    private static AttributePairingRegistrationRequestDto request(boolean crossEngine) {
        return new AttributePairingRegistrationRequestDto(
                "client-a",
                "CUSTOMER_NAME_TO_ID",
                "Customer Name To Id",
                "meta",
                "customer",
                "customer_nm",
                "customer_id",
                "DISPLAY_TO_FILTER",
                "CACHED_LOOKUP",
                "{\"Acme Corp\":\"CUST-100\"}",
                null,
                true,
                3600,
                "ONE_TO_ONE",
                false,
                crossEngine,
                true,
                "BTREE",
                20,
                "Resolve customer name to id",
                "producer"
        );
    }

    private static ObjectExposureRecord objectRecord() {
        return new ObjectExposureRecord(
                UUID.fromString("00000000-0000-0000-0000-000000000301"),
                "client-a",
                "customer",
                "Customer",
                "Customer",
                "TABLE",
                "meta",
                UUID.fromString("00000000-0000-0000-0000-000000000401"),
                "ACTIVE",
                OffsetDateTime.parse("2026-06-18T10:15:30Z"),
                "producer",
                OffsetDateTime.parse("2026-06-18T10:15:30Z"),
                "producer"
        );
    }

    private static AttributeExposureRecord attribute(String attributeCode) {
        return new AttributeExposureRecord(
                UUID.randomUUID(),
                UUID.fromString("00000000-0000-0000-0000-000000000301"),
                "client-a",
                attributeCode,
                attributeCode,
                attributeCode,
                "VARCHAR",
                "TAX",
                "SRC",
                "US",
                OffsetDateTime.parse("2026-06-18T10:15:30Z"),
                "producer",
                OffsetDateTime.parse("2026-06-18T10:15:30Z"),
                "producer"
        );
    }

    private static final class RecordingObjectExposureReadDao implements ObjectExposureReadDao {

        private ObjectExposureRecord object;
        private List<AttributeExposureRecord> attributes = List.of();

        @Override
        public List<ObjectExposureRecord> findObjects(String clientId, String schemaCode, String lifecycleStatusCode) {
            return List.of();
        }

        @Override
        public Optional<ObjectExposureRecord> findObject(String clientId, UUID objectId) {
            return Optional.empty();
        }

        @Override
        public Optional<ObjectExposureRecord> findObject(String schemaCode, String objectCode) {
            return Optional.ofNullable(object);
        }

        @Override
        public List<AttributeExposureRecord> findAttributes(String clientId, UUID objectId) {
            return attributes;
        }
    }

    private static final class RecordingAttributePairingResolutionDao implements AttributePairingResolutionDao {

        private final boolean indexed;

        private RecordingAttributePairingResolutionDao(boolean indexed) {
            this.indexed = indexed;
        }

        @Override
        public Optional<AttributePairingCatalogRecord> findPairing(String clientId, String pairingCode) {
            return Optional.empty();
        }

        @Override
        public Optional<AttributePairingCatalogRecord> findActivePairing(String clientId,
                                                                         String schemaCode,
                                                                         String objectCode,
                                                                         String displayAttributeCode) {
            return Optional.empty();
        }

        @Override
        public boolean isAttributeIndexed(String schemaCode, String objectCode, String attributeCode) {
            return indexed;
        }

        @Override
        public Optional<com.lextr.semanticlayer.model.AttributePairingValueCacheRecord> findCachedValue(
                String pairingCode,
                String clientId,
                String displayValue,
                OffsetDateTime asOfTimestamp
        ) {
            return Optional.empty();
        }

        @Override
        public com.lextr.semanticlayer.model.AttributePairingValueCacheRecord upsertCachedValue(
                com.lextr.semanticlayer.model.AttributePairingValueCacheWriteRequest request
        ) {
            throw new UnsupportedOperationException();
        }

        @Override
        public com.lextr.semanticlayer.model.AttributePairingValueCacheRecord recordCacheHit(
                com.lextr.semanticlayer.model.AttributePairingCacheHitWriteRequest request
        ) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class RecordingAttributePairingRegistrationWriteDao implements AttributePairingRegistrationWriteDao {

        private final TransactionHarness harness;
        private final List<AttributePairingCatalogRecord> committedPairings = new ArrayList<>();
        private final List<FilterLookupWorkflowTaskRecord> committedWorkflowTasks = new ArrayList<>();
        private final List<FilterLookupMetadataChangeHistoryRecord> committedMetadataChanges = new ArrayList<>();
        private AttributePairingCatalogWriteRequest pairingRequest;
        private FilterLookupWorkflowTaskWriteRequest workflowTaskRequest;
        private FilterLookupMetadataChangeHistoryWriteRequest metadataChangeRequest;

        private RecordingAttributePairingRegistrationWriteDao(TransactionHarness harness) {
            this.harness = harness;
        }

        @Override
        public AttributePairingCatalogRecord insertPairing(AttributePairingCatalogWriteRequest request) {
            pairingRequest = request;
            AttributePairingCatalogRecord record = new AttributePairingCatalogRecord(
                    101L,
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
                    request.lookup_cache_enabled_flg(),
                    request.lookup_cache_ttl_seconds_nbr(),
                    request.cardinality_cd(),
                    request.is_bidirectional_flg(),
                    request.is_cross_engine_flg(),
                    request.filter_attribute_indexed_flg(),
                    request.filter_attribute_index_type_cd(),
                    request.performance_gain_pct_est_nbr(),
                    request.ai_context_txt(),
                    request.client_id(),
                    request.lifecycle_status_cd(),
                    request.governance_review_status_cd(),
                    request.version_nbr(),
                    request.created_ts(),
                    request.created_by(),
                    request.updated_ts(),
                    request.updated_by()
            );
            harness.addPairing(record, committedPairings);
            return record;
        }

        @Override
        public FilterLookupWorkflowTaskRecord insertWorkflowTask(FilterLookupWorkflowTaskWriteRequest request) {
            workflowTaskRequest = request;
            FilterLookupWorkflowTaskRecord record = new FilterLookupWorkflowTaskRecord(
                    201L,
                    request.task_type_cd(),
                    request.entity_type_cd(),
                    request.entity_ref(),
                    request.task_status_cd(),
                    request.submitted_by(),
                    request.submitted_ts(),
                    request.assigned_to(),
                    request.due_dt(),
                    request.description_txt(),
                    request.client_id(),
                    request.approved_by(),
                    request.approved_ts(),
                    request.approval_note_txt()
            );
            harness.addWorkflowTask(record, committedWorkflowTasks);
            return record;
        }

        @Override
        public FilterLookupMetadataChangeHistoryRecord insertMetadataChangeHistory(
                FilterLookupMetadataChangeHistoryWriteRequest request
        ) {
            metadataChangeRequest = request;
            FilterLookupMetadataChangeHistoryRecord record = new FilterLookupMetadataChangeHistoryRecord(
                    301L,
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

    private static final class RecordingAttributePairingPolicyClient implements AttributePairingPolicyClient {

        private final AttributePairingPolicyDecisionDto decision;
        private final List<AttributePairingPolicyRequestDto> requests = new ArrayList<>();

        private RecordingAttributePairingPolicyClient(AttributePairingPolicyDecisionDto decision) {
            this.decision = decision;
        }

        @Override
        public AttributePairingPolicyDecisionDto validateCrossEngine(AttributePairingPolicyRequestDto request) {
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
        private final List<AttributePairingCatalogRecord> pendingPairings = new ArrayList<>();
        private final List<FilterLookupWorkflowTaskRecord> pendingWorkflowTasks = new ArrayList<>();
        private final List<FilterLookupMetadataChangeHistoryRecord> pendingMetadataChanges = new ArrayList<>();
        private List<AttributePairingCatalogRecord> pairingSink;
        private List<FilterLookupWorkflowTaskRecord> workflowTaskSink;
        private List<FilterLookupMetadataChangeHistoryRecord> metadataChangeSink;

        private void begin() {
            committed = false;
            rolledBack = false;
            pendingPairings.clear();
            pendingWorkflowTasks.clear();
            pendingMetadataChanges.clear();
        }

        private void commit() {
            if (pairingSink != null) {
                pairingSink.addAll(pendingPairings);
            }
            if (workflowTaskSink != null) {
                workflowTaskSink.addAll(pendingWorkflowTasks);
            }
            if (metadataChangeSink != null) {
                metadataChangeSink.addAll(pendingMetadataChanges);
            }
            committed = true;
        }

        private void rollback() {
            pendingPairings.clear();
            pendingWorkflowTasks.clear();
            pendingMetadataChanges.clear();
            rolledBack = true;
        }

        private void addPairing(AttributePairingCatalogRecord record, List<AttributePairingCatalogRecord> sink) {
            pairingSink = sink;
            pendingPairings.add(record);
        }

        private void addWorkflowTask(FilterLookupWorkflowTaskRecord record, List<FilterLookupWorkflowTaskRecord> sink) {
            workflowTaskSink = sink;
            pendingWorkflowTasks.add(record);
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
