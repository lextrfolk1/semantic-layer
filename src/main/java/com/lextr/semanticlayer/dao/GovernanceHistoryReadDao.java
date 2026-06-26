package com.lextr.semanticlayer.dao;

import com.lextr.semanticlayer.model.GovernanceHistoryEventRecord;

import java.util.List;

public interface GovernanceHistoryReadDao {

    List<GovernanceHistoryEventRecord> findEvents(String clientId,
                                                  String entityTypeCode,
                                                  String entityRef,
                                                  String changeTypeCode);
}
