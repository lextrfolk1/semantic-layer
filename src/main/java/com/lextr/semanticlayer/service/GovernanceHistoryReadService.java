package com.lextr.semanticlayer.service;

import com.lextr.semanticlayer.dto.GovernanceHistoryEventDto;

import java.util.List;

public interface GovernanceHistoryReadService {

    List<GovernanceHistoryEventDto> findHistory(String clientId,
                                                String entityTypeCode,
                                                String entityRef,
                                                String changeTypeCode);
}
