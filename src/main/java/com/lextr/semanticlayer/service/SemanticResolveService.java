package com.lextr.semanticlayer.service;

import com.lextr.semanticlayer.dto.LogicalPhysicalResolutionDto;
import com.lextr.semanticlayer.dto.SemanticResolveRequestDto;

import java.util.List;

public interface SemanticResolveService {

    List<LogicalPhysicalResolutionDto> resolveAttributes(SemanticResolveRequestDto request,
                                                         String actorId,
                                                         String roleCode,
                                                         String purposeCode);

    List<LogicalPhysicalResolutionDto> resolveOutboundGrain(String clientId,
                                                            String actorId,
                                                            String roleCode,
                                                            String purposeCode,
                                                            Long outboundId);
}
