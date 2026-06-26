package com.lextr.semanticlayer.dao;

import com.lextr.semanticlayer.model.ExternalRuleResultRecord;
import com.lextr.semanticlayer.model.ExternalRuleResultWriteRequest;
import com.lextr.semanticlayer.model.ObjectExposureAccessAuditWriteRequest;

public interface RuleResultDao {

    ExternalRuleResultRecord insertResult(ExternalRuleResultWriteRequest request);

    void insertMetadataChangeHistory(ObjectExposureAccessAuditWriteRequest request);
}
