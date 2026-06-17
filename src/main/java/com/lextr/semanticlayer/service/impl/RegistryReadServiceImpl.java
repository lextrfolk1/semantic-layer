package com.lextr.semanticlayer.service.impl;

import com.lextr.semanticlayer.dao.RegistryReadDao;
import com.lextr.semanticlayer.exception.RegistryResourceNotFoundException;
import com.lextr.semanticlayer.model.DataConnectionRecord;
import com.lextr.semanticlayer.model.SchemaCatalogRecord;
import com.lextr.semanticlayer.service.RegistryReadService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class RegistryReadServiceImpl implements RegistryReadService {

    private final RegistryReadDao registryReadDao;

    public RegistryReadServiceImpl(RegistryReadDao registryReadDao) {
        this.registryReadDao = registryReadDao;
    }

    @Override
    public List<SchemaCatalogRecord> findSchemas(String clientId, String lifecycleStatusCode) {
        return registryReadDao.findSchemas(clientId, lifecycleStatusCode);
    }

    @Override
    public SchemaCatalogRecord findSchema(String clientId, String schemaCode) {
        return registryReadDao.findSchema(clientId, schemaCode)
                .orElseThrow(() -> new RegistryResourceNotFoundException("schema", schemaCode));
    }

    @Override
    public List<DataConnectionRecord> findConnections(String clientId, String engineCode, Boolean activeFlag) {
        return registryReadDao.findConnections(clientId, engineCode, activeFlag);
    }

    @Override
    public DataConnectionRecord findConnection(String clientId, UUID connectionId) {
        return registryReadDao.findConnection(clientId, connectionId)
                .orElseThrow(() -> new RegistryResourceNotFoundException("connection", connectionId.toString()));
    }
}
