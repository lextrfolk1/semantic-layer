package com.lextr.semanticlayer.service.impl;

import com.lextr.semanticlayer.dao.ObjectRegistrationWriteDao;
import com.lextr.semanticlayer.dto.AttributeRegistrationRequestDto;
import com.lextr.semanticlayer.dto.ObjectRegistrationRequestDto;
import com.lextr.semanticlayer.dto.ObjectRegistrationResponseDto;
import com.lextr.semanticlayer.exception.ObjectRegistrationServiceException;
import com.lextr.semanticlayer.model.AttributeCatalogRecord;
import com.lextr.semanticlayer.model.AttributeCatalogWriteRequest;
import com.lextr.semanticlayer.model.MetadataChangeHistoryRecord;
import com.lextr.semanticlayer.model.MetadataChangeHistoryWriteRequest;
import com.lextr.semanticlayer.model.ObjectCatalogRecord;
import com.lextr.semanticlayer.model.ObjectCatalogWriteRequest;
import com.lextr.semanticlayer.model.WorkflowTaskRecord;
import com.lextr.semanticlayer.model.WorkflowTaskWriteRequest;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ObjectRegistrationServiceImplTest {

    @Test
    void registersObjectAndDelegatesToWriteDao() {
        RecordingObjectRegistrationWriteDao dao = new RecordingObjectRegistrationWriteDao();
        ObjectRegistrationServiceImpl service = new ObjectRegistrationServiceImpl(dao);

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
                        "US"
                ))
        ));

        assertEquals("GL_BALANCE", dao.objectRequest.object_cd());
        assertEquals("client-a", dao.objectRequest.client_id());
        assertEquals("AMOUNT", dao.attributeRequests.get(0).attribute_cd());
        assertEquals("PENDING_APPROVAL", dao.workflowTaskRequest.task_status_cd());
        assertEquals("REGISTERED", dao.metadataChangeHistoryRequest.change_type_cd());
        assertEquals("DRAFT", result.lifecycle_status_cd());
        assertEquals("PENDING_APPROVAL", result.workflow_status_cd());
        assertEquals("AMOUNT", result.attributes().get(0).attribute_cd());
        assertNotNull(result.object_id());
        assertNotNull(result.workflow_task_id());
        assertNotNull(result.change_history_id());
    }

    @Test
    void wrapsDaoFailuresInServiceException() {
        FailingObjectRegistrationWriteDao dao = new FailingObjectRegistrationWriteDao();
        ObjectRegistrationServiceImpl service = new ObjectRegistrationServiceImpl(dao);

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
                        "US"
                ))
        )));
    }

    private static final class RecordingObjectRegistrationWriteDao implements ObjectRegistrationWriteDao {

        private ObjectCatalogWriteRequest objectRequest;
        private final List<AttributeCatalogWriteRequest> attributeRequests = new ArrayList<>();
        private WorkflowTaskWriteRequest workflowTaskRequest;
        private MetadataChangeHistoryWriteRequest metadataChangeHistoryRequest;

        @Override
        public ObjectCatalogRecord insertDraftObject(ObjectCatalogWriteRequest request) {
            objectRequest = request;
            return new ObjectCatalogRecord(
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
        }

        @Override
        public AttributeCatalogRecord insertAttribute(AttributeCatalogWriteRequest request) {
            attributeRequests.add(request);
            return new AttributeCatalogRecord(
                    request.attribute_id(),
                    request.object_id(),
                    request.client_id(),
                    request.attribute_cd(),
                    request.attribute_nm(),
                    request.data_type_cd(),
                    request.taxonomy_cd(),
                    request.taxonomy_source_cd(),
                    request.taxonomy_jurisdiction_cd(),
                    request.created_ts(),
                    request.created_by(),
                    request.updated_ts(),
                    request.updated_by()
            );
        }

        @Override
        public WorkflowTaskRecord insertWorkflowTask(WorkflowTaskWriteRequest request) {
            workflowTaskRequest = request;
            return new WorkflowTaskRecord(
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
        }

        @Override
        public MetadataChangeHistoryRecord insertMetadataChangeHistory(MetadataChangeHistoryWriteRequest request) {
            metadataChangeHistoryRequest = request;
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

    private static final class FailingObjectRegistrationWriteDao implements ObjectRegistrationWriteDao {

        @Override
        public ObjectCatalogRecord insertDraftObject(ObjectCatalogWriteRequest request) {
            throw new IllegalStateException("db write failed");
        }

        @Override
        public AttributeCatalogRecord insertAttribute(AttributeCatalogWriteRequest request) {
            throw new UnsupportedOperationException();
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
}
