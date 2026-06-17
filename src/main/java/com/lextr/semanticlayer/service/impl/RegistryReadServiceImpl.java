package com.lextr.semanticlayer.service.impl;

import com.lextr.semanticlayer.dao.RegistryReadDao;
import com.lextr.semanticlayer.dto.DataConnectionDto;
import com.lextr.semanticlayer.dto.SchemaCatalogDto;
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
    public List<SchemaCatalogDto> findSchemas(String clientId, String lifecycleStatusCode) {
        return registryReadDao.findSchemas(clientId, lifecycleStatusCode).stream()
                .map(this::toSchemaCatalogDto)
                .toList();
    }

    @Override
    public SchemaCatalogDto findSchema(String clientId, String schemaCode) {
        return registryReadDao.findSchema(clientId, schemaCode)
                .map(this::toSchemaCatalogDto)
                .orElseThrow(() -> new RegistryResourceNotFoundException("schema", schemaCode));
    }

    @Override
    public List<DataConnectionDto> findConnections(String clientId, String engineCode, Boolean activeFlag) {
        return registryReadDao.findConnections(clientId, engineCode, activeFlag).stream()
                .map(this::toDataConnectionDto)
                .toList();
    }

    @Override
    public DataConnectionDto findConnection(String clientId, UUID connectionId) {
        return registryReadDao.findConnection(clientId, connectionId)
                .map(this::toDataConnectionDto)
                .orElseThrow(() -> new RegistryResourceNotFoundException("connection", connectionId.toString()));
    }

    private SchemaCatalogDto toSchemaCatalogDto(SchemaCatalogRecord record) {
        return new SchemaCatalogDto(
                record.schema_cd(),
                effectiveValue(record.effective_schema_nm(), record.schema_nm()),
                record.schema_purpose_txt(),
                record.lifecycle_status_cd(),
                record.created_ts(),
                record.created_by(),
                record.updated_ts(),
                record.updated_by()
        );
    }

    private DataConnectionDto toDataConnectionDto(DataConnectionRecord record) {
        return new DataConnectionDto(
                record.connection_id(),
                record.connection_cd(),
                effectiveValue(record.effective_connection_nm(), record.connection_nm()),
                record.engine_cd(),
                record.connection_type_cd(),
                record.source_mode_cd(),
                record.host_nm(),
                record.port_nbr(),
                record.database_nm(),
                record.schema_nm_default(),
                record.is_default_flg(),
                record.is_active_flg(),
                record.created_ts(),
                record.created_by(),
                record.updated_ts(),
                record.updated_by()
        );
    }

    private String effectiveValue(String effectiveValue, String baseValue) {
        return effectiveValue == null || effectiveValue.isBlank() ? baseValue : effectiveValue;
    }
}
