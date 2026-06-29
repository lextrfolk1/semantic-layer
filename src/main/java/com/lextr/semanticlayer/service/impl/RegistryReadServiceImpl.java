package com.lextr.semanticlayer.service.impl;

import com.lextr.semanticlayer.dao.RegistryReadDao;
import com.lextr.semanticlayer.dto.DataConnectionDto;
import com.lextr.semanticlayer.dto.SchemaCatalogDto;
import com.lextr.semanticlayer.exception.RegistryResourceNotFoundException;
import com.lextr.semanticlayer.model.DataConnectionRecord;
import com.lextr.semanticlayer.model.SchemaCatalogRecord;
import com.lextr.semanticlayer.service.RegistryReadService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class RegistryReadServiceImpl implements RegistryReadService {

    private static final Logger logger = LoggerFactory.getLogger(RegistryReadServiceImpl.class);

    private final RegistryReadDao registryReadDao;

    public RegistryReadServiceImpl(RegistryReadDao registryReadDao) {
        this.registryReadDao = registryReadDao;
    }

    @Override
    public List<SchemaCatalogDto> findSchemas(String clientId, String lifecycleStatusCode) {
        logger.debug("Finding schemas. clientId={}, lifecycleStatusCode={}", clientId, lifecycleStatusCode);
        List<SchemaCatalogDto> schemas = registryReadDao.findSchemas(clientId, lifecycleStatusCode).stream()
                .map(this::toSchemaCatalogDto)
                .toList();
        logger.debug("Schemas mapped. clientId={}, resultCount={}", clientId, schemas.size());
        return schemas;
    }

    @Override
    public SchemaCatalogDto findSchema(String clientId, String schemaCode) {
        logger.debug("Finding schema. clientId={}, schemaCode={}", clientId, schemaCode);
        return registryReadDao.findSchema(clientId, schemaCode)
                .map(this::toSchemaCatalogDto)
                .orElseThrow(() -> {
                    logger.warn("Schema not found. clientId={}, schemaCode={}", clientId, schemaCode);
                    return new RegistryResourceNotFoundException("schema", schemaCode);
                });
    }

    @Override
    public List<DataConnectionDto> findConnections(String clientId, String engineCode, Boolean activeFlag) {
        logger.debug("Finding connections. clientId={}, engineCode={}, activeFlag={}", clientId, engineCode, activeFlag);
        List<DataConnectionDto> connections = registryReadDao.findConnections(clientId, engineCode, activeFlag).stream()
                .map(this::toDataConnectionDto)
                .toList();
        logger.debug("Connections mapped. clientId={}, resultCount={}", clientId, connections.size());
        return connections;
    }

    @Override
    public DataConnectionDto findConnection(String clientId, UUID connectionId) {
        logger.debug("Finding connection. clientId={}, connectionId={}", clientId, connectionId);
        return registryReadDao.findConnection(clientId, connectionId)
                .map(this::toDataConnectionDto)
                .orElseThrow(() -> {
                    logger.warn("Connection not found. clientId={}, connectionId={}", clientId, connectionId);
                    return new RegistryResourceNotFoundException("connection", connectionId.toString());
                });
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

    @Override
    public List<java.util.Map<String, Object>> introspectColumns(String schemaCd, String tableCd) {
        logger.debug("Introspecting columns. schemaCode={}, tableCode={}", schemaCd, tableCd);
        List<java.util.Map<String, Object>> columns = registryReadDao.introspectColumns(schemaCd, tableCd);
        logger.debug("Column introspection completed. schemaCode={}, tableCode={}, resultCount={}", schemaCd, tableCd, columns.size());
        return columns;
    }

    @Override
    public List<java.util.Map<String, Object>> introspectTables(String schemaCd) {
        logger.debug("Introspecting tables. schemaCode={}", schemaCd);
        List<java.util.Map<String, Object>> tables = registryReadDao.introspectTables(schemaCd);
        logger.debug("Table introspection completed. schemaCode={}, resultCount={}", schemaCd, tables.size());
        return tables;
    }
}
