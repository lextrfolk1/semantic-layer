package com.lextr.semanticlayer.dao;

import com.lextr.semanticlayer.model.LogicalPhysicalResolutionRecord;

import java.util.List;

public interface LogicalPhysicalResolutionDao {

    List<LogicalPhysicalResolutionRecord> findByAttributes(String clientId,
                                                           String schemaCode,
                                                           String objectCode,
                                                           List<String> logicalAttributeCodes);

    List<LogicalPhysicalResolutionRecord> findByOutboundGrain(String clientId, Long outboundId);
}
