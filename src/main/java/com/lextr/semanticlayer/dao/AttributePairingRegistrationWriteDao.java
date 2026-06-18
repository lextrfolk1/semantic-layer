package com.lextr.semanticlayer.dao;

import com.lextr.semanticlayer.model.AttributePairingCatalogRecord;
import com.lextr.semanticlayer.model.AttributePairingCatalogWriteRequest;
import com.lextr.semanticlayer.model.FilterLookupMetadataChangeHistoryRecord;
import com.lextr.semanticlayer.model.FilterLookupMetadataChangeHistoryWriteRequest;
import com.lextr.semanticlayer.model.FilterLookupWorkflowTaskRecord;
import com.lextr.semanticlayer.model.FilterLookupWorkflowTaskWriteRequest;

public interface AttributePairingRegistrationWriteDao {

    AttributePairingCatalogRecord insertPairing(AttributePairingCatalogWriteRequest request);

    FilterLookupWorkflowTaskRecord insertWorkflowTask(FilterLookupWorkflowTaskWriteRequest request);

    FilterLookupMetadataChangeHistoryRecord insertMetadataChangeHistory(
            FilterLookupMetadataChangeHistoryWriteRequest request
    );
}
