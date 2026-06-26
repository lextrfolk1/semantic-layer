package com.lextr.semanticlayer.service;

import com.lextr.semanticlayer.dto.LogicalPhysicalResolutionDto;

import java.util.List;

public interface LogicalPhysicalResolutionService {

    List<LogicalPhysicalResolutionDto> resolveAttributes(String clientId,
                                                         String schemaCode,
                                                         String objectCode,
                                                         List<String> logicalAttributeCodes);

    List<LogicalPhysicalResolutionDto> resolveOutboundGrain(String clientId, Long outboundId);
}
