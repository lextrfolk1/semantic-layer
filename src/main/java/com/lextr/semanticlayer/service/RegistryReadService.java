package com.lextr.semanticlayer.service;

import com.lextr.semanticlayer.dto.DataConnectionDto;
import com.lextr.semanticlayer.dto.SchemaCatalogDto;

import java.util.List;
import java.util.UUID;

public interface RegistryReadService {

    List<SchemaCatalogDto> findSchemas(String clientId, String lifecycleStatusCode);

    SchemaCatalogDto findSchema(String clientId, String schemaCode);

    List<DataConnectionDto> findConnections(String clientId, String engineCode, Boolean activeFlag);

    DataConnectionDto findConnection(String clientId, UUID connectionId);

    default List<java.util.Map<String, Object>> introspectColumns(String schemaCd, String tableCd) {
        return java.util.Collections.emptyList();
    }

    default List<java.util.Map<String, Object>> introspectTables(String schemaCd) {
        return java.util.Collections.emptyList();
    }
}
