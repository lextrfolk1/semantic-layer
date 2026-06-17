package com.lextr.semanticlayer.dao;

import com.lextr.semanticlayer.model.AttributeCatalogRecord;
import com.lextr.semanticlayer.model.AttributeCatalogWriteRequest;
import com.lextr.semanticlayer.model.MetadataChangeHistoryRecord;
import com.lextr.semanticlayer.model.MetadataChangeHistoryWriteRequest;
import com.lextr.semanticlayer.model.ObjectCatalogRecord;
import com.lextr.semanticlayer.model.ObjectCatalogWriteRequest;
import com.lextr.semanticlayer.model.WorkflowTaskRecord;
import com.lextr.semanticlayer.model.WorkflowTaskWriteRequest;

public interface ObjectRegistrationWriteDao {

    ObjectCatalogRecord insertDraftObject(ObjectCatalogWriteRequest request);

    AttributeCatalogRecord insertAttribute(AttributeCatalogWriteRequest request);

    WorkflowTaskRecord insertWorkflowTask(WorkflowTaskWriteRequest request);

    MetadataChangeHistoryRecord insertMetadataChangeHistory(MetadataChangeHistoryWriteRequest request);
}
