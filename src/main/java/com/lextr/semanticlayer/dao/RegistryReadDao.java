package com.lextr.semanticlayer.dao;

import com.lextr.semanticlayer.model.DataConnectionRecord;
import com.lextr.semanticlayer.model.SchemaCatalogRecord;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RegistryReadDao {

    List<SchemaCatalogRecord> findSchemas(String clientId, String lifecycleStatusCode);

    Optional<SchemaCatalogRecord> findSchema(String clientId, String schemaCode);

    List<DataConnectionRecord> findConnections(String clientId, String engineCode, Boolean activeFlag);

    Optional<DataConnectionRecord> findConnection(String clientId, UUID connectionId);

    default List<java.util.Map<String, Object>> introspectColumns(String schemaCd, String tableCd) {
        return java.util.Collections.emptyList();
    }

    default List<java.util.Map<String, Object>> introspectTables(String schemaCd) {
        return java.util.Collections.emptyList();
    }
}
