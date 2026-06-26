package com.lextr.semanticlayer.dao;

import com.lextr.semanticlayer.model.ProfilingResultRecord;

import java.util.List;

public interface ProfilingResultReadDao {

    List<ProfilingResultRecord> findMetrics(String clientId, String schemaCode, String objectCode, String profilingStatusCode);
}
