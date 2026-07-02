package com.lextr.semanticlayer.dao;

import com.lextr.semanticlayer.exception.SemanticLayerException;
import com.lextr.semanticlayer.model.AttributeAccessGrantRecord;
import com.lextr.semanticlayer.model.AttributeAccessGrantStatusUpdateRequest;
import com.lextr.semanticlayer.model.AttributeAccessGrantWriteRequest;
import com.lextr.semanticlayer.model.AttributeCatalogRecord;
import com.lextr.semanticlayer.model.AttributeCatalogWriteRequest;
import com.lextr.semanticlayer.model.AttributeClassificationRecord;
import com.lextr.semanticlayer.model.AttributeClassificationWriteRequest;
import com.lextr.semanticlayer.model.MetadataChangeHistoryRecord;
import com.lextr.semanticlayer.model.MetadataChangeHistoryWriteRequest;
import com.lextr.semanticlayer.model.ObjectCatalogRecord;
import com.lextr.semanticlayer.model.ObjectClassificationRecord;
import com.lextr.semanticlayer.model.ObjectClassificationWriteRequest;
import com.lextr.semanticlayer.model.ObjectCatalogWriteRequest;
import com.lextr.semanticlayer.model.WorkflowTaskRecord;
import com.lextr.semanticlayer.model.WorkflowTaskWriteRequest;

public interface ObjectRegistrationWriteDao {

    ObjectCatalogRecord insertDraftObject(ObjectCatalogWriteRequest request);

    AttributeCatalogRecord insertAttribute(AttributeCatalogWriteRequest request);

    default ObjectClassificationRecord updateObjectClassification(ObjectClassificationWriteRequest request) {
        throw new SemanticLayerException("ObjectRegistrationWriteDao does not support object classification updates");
    }

    default AttributeClassificationRecord updateAttributeClassification(AttributeClassificationWriteRequest request) {
        throw new SemanticLayerException("ObjectRegistrationWriteDao does not support attribute classification updates");
    }

    default AttributeAccessGrantRecord insertAttributeAccessGrant(AttributeAccessGrantWriteRequest request) {
        throw new SemanticLayerException("ObjectRegistrationWriteDao does not support attribute access grant inserts");
    }

    default AttributeAccessGrantRecord updateAttributeAccessGrantStatus(AttributeAccessGrantStatusUpdateRequest request) {
        throw new SemanticLayerException("ObjectRegistrationWriteDao does not support attribute access grant status updates");
    }

    WorkflowTaskRecord insertWorkflowTask(WorkflowTaskWriteRequest request);

    MetadataChangeHistoryRecord insertMetadataChangeHistory(MetadataChangeHistoryWriteRequest request);
}
