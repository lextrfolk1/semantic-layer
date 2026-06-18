package com.lextr.semanticlayer.dao;

import com.lextr.semanticlayer.model.FilterLookupExecutionLogRecord;
import com.lextr.semanticlayer.model.FilterLookupExecutionLogWriteRequest;

public interface FilterLookupExecutionLogWriteDao {

    FilterLookupExecutionLogRecord insertExecutionLog(FilterLookupExecutionLogWriteRequest request);
}
