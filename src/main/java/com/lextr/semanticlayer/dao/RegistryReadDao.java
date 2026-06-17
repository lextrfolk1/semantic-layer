package com.lextr.semanticlayer.dao;

import com.lextr.semanticlayer.model.DataConnectionRecord;
import com.lextr.semanticlayer.model.SchemaCatalogRecord;

import java.util.List;

public interface RegistryReadDao {

    List<SchemaCatalogRecord> findSchemas(String lifecycleStatusCode);

    List<DataConnectionRecord> findConnections(String engineCode, Boolean activeFlag);
}
