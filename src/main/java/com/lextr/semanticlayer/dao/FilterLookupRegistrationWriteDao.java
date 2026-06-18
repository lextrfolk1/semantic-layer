package com.lextr.semanticlayer.dao;

import com.lextr.semanticlayer.exception.SemanticLayerException;
import com.lextr.semanticlayer.model.FilterLookupBindingRecord;
import com.lextr.semanticlayer.model.FilterLookupBindingWriteRequest;
import com.lextr.semanticlayer.model.FilterLookupCertificationWriteRequest;
import com.lextr.semanticlayer.model.FilterLookupMetadataChangeHistoryRecord;
import com.lextr.semanticlayer.model.FilterLookupMetadataChangeHistoryWriteRequest;
import com.lextr.semanticlayer.model.FilterLookupWorkflowTaskRecord;
import com.lextr.semanticlayer.model.FilterLookupWorkflowTaskWriteRequest;
import com.lextr.semanticlayer.model.SemanticFilterLookupRecord;
import com.lextr.semanticlayer.model.SemanticFilterLookupWriteRequest;

public interface FilterLookupRegistrationWriteDao {

    SemanticFilterLookupRecord insertLookup(SemanticFilterLookupWriteRequest request);

    default SemanticFilterLookupRecord certifyLookup(FilterLookupCertificationWriteRequest request) {
        throw new SemanticLayerException("FilterLookupRegistrationWriteDao does not support filter lookup certification");
    }

    default FilterLookupBindingRecord insertBinding(FilterLookupBindingWriteRequest request) {
        throw new SemanticLayerException("FilterLookupRegistrationWriteDao does not support filter lookup binding");
    }

    FilterLookupWorkflowTaskRecord insertWorkflowTask(FilterLookupWorkflowTaskWriteRequest request);

    FilterLookupMetadataChangeHistoryRecord insertMetadataChangeHistory(
            FilterLookupMetadataChangeHistoryWriteRequest request
    );
}
