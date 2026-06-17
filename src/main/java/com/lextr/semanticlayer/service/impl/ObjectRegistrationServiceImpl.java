package com.lextr.semanticlayer.service.impl;

import com.lextr.semanticlayer.dao.ObjectRegistrationWriteDao;
import com.lextr.semanticlayer.dto.AttributeRegistrationResponseDto;
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
import com.lextr.semanticlayer.service.ObjectRegistrationService;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Service
public class ObjectRegistrationServiceImpl implements ObjectRegistrationService {

    private static final String WORKFLOW_TYPE_CD = "OBJECT_REGISTRATION";
    private static final String ENTITY_TYPE_CD = "OBJECT";
    private static final String TASK_STATUS_CD = "PENDING_APPROVAL";
    private static final String CHANGE_TYPE_CD = "REGISTERED";

    private final ObjectRegistrationWriteDao objectRegistrationWriteDao;

    public ObjectRegistrationServiceImpl(ObjectRegistrationWriteDao objectRegistrationWriteDao) {
        this.objectRegistrationWriteDao = objectRegistrationWriteDao;
    }

    @Override
    public ObjectRegistrationResponseDto registerObject(ObjectRegistrationRequestDto request) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        UUID objectId = UUID.randomUUID();
        UUID workflowTaskId = UUID.randomUUID();
        UUID changeHistoryId = UUID.randomUUID();

        try {
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
                    .map(attribute -> objectRegistrationWriteDao.insertAttribute(new AttributeCatalogWriteRequest(
                            UUID.randomUUID(),
                            objectId,
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
                    objectId,
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
                    objectId,
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
        } catch (RuntimeException exception) {
            throw new ObjectRegistrationServiceException("Unable to register object", exception);
        }
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
}
