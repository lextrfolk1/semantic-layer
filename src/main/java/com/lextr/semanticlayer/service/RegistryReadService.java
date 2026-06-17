package com.lextr.semanticlayer.service;

import com.lextr.semanticlayer.model.DataConnectionRecord;
import com.lextr.semanticlayer.model.SchemaCatalogRecord;

import java.util.List;
import java.util.UUID;

public interface RegistryReadService {

    List<SchemaCatalogRecord> findSchemas(String clientId, String lifecycleStatusCode);

    SchemaCatalogRecord findSchema(String clientId, String schemaCode);

    List<DataConnectionRecord> findConnections(String clientId, String engineCode, Boolean activeFlag);

    DataConnectionRecord findConnection(String clientId, UUID connectionId);
}
