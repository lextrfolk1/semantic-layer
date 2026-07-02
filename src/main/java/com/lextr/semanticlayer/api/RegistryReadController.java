package com.lextr.semanticlayer.api;

import com.lextr.semanticlayer.dto.DataConnectionDto;
import com.lextr.semanticlayer.dto.SchemaCatalogDto;
import com.lextr.semanticlayer.service.RegistryReadService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/registry")
@Tag(name = "Registry", description = "Schema and connection registry read operations.")
public class RegistryReadController {

    private static final Logger logger = LoggerFactory.getLogger(RegistryReadController.class);

    private final RegistryReadService registryReadService;

    public RegistryReadController(RegistryReadService registryReadService) {
        this.registryReadService = registryReadService;
    }

    @GetMapping("/schemas")
    @Operation(summary = "List schemas", description = "Returns schemas visible for the supplied client.")
    public List<SchemaCatalogDto> findSchemas(
            @Parameter(description = "Tenant identifier.") @RequestParam("client_id") String clientId,
            @Parameter(description = "Optional lifecycle status filter.") @RequestParam(value = "lifecycle_status_cd", required = false) String lifecycleStatusCode) {
        logger.debug("Listing schemas. clientId={}, lifecycleStatusCode={}", clientId, lifecycleStatusCode);
        List<SchemaCatalogDto> schemas = registryReadService.findSchemas(clientId, lifecycleStatusCode);
        logger.debug("Schemas resolved. clientId={}, resultCount={}", clientId, schemas.size());
        return schemas;
    }

    @GetMapping("/schemas/{schema_code}")
    @Operation(summary = "Get schema", description = "Returns one schema by schema code for the supplied client.")
    public SchemaCatalogDto findSchema(
            @Parameter(description = "Tenant identifier.") @RequestParam("client_id") String clientId,
            @Parameter(description = "Schema code.") @PathVariable("schema_code") String schemaCode) {
        logger.debug("Fetching schema. clientId={}, schemaCode={}", clientId, schemaCode);
        SchemaCatalogDto schema = registryReadService.findSchema(clientId, schemaCode);
        logger.debug("Schema resolved. clientId={}, schemaCode={}", clientId, schemaCode);
        return schema;
    }

    @GetMapping("/connections")
    @Operation(summary = "List connections", description = "Returns data connections visible for the supplied client.")
    public List<DataConnectionDto> findConnections(
            @Parameter(description = "Tenant identifier.") @RequestParam("client_id") String clientId,
            @Parameter(description = "Optional engine filter.") @RequestParam(value = "engine_cd", required = false) String engineCode,
            @Parameter(description = "Optional active flag filter.") @RequestParam(value = "is_active_flg", required = false) Boolean activeFlag) {
        logger.debug("Listing connections. clientId={}, engineCode={}, activeFlag={}", clientId, engineCode, activeFlag);
        List<DataConnectionDto> connections = registryReadService.findConnections(clientId, engineCode, activeFlag);
        logger.debug("Connections resolved. clientId={}, resultCount={}", clientId, connections.size());
        return connections;
    }

    @GetMapping("/connections/{connection_id}")
    @Operation(summary = "Get connection", description = "Returns one data connection by identifier for the supplied client.")
    public DataConnectionDto findConnection(
            @Parameter(description = "Tenant identifier.") @RequestParam("client_id") String clientId,
            @Parameter(description = "Connection identifier.") @PathVariable("connection_id") UUID connectionId) {
        logger.debug("Fetching connection. clientId={}, connectionId={}", clientId, connectionId);
        DataConnectionDto connection = registryReadService.findConnection(clientId, connectionId);
        logger.debug("Connection resolved. clientId={}, connectionId={}", clientId, connectionId);
        return connection;
    }

    @GetMapping("/schemas/{schema_code}/tables/{table_code}/introspect")
    @Operation(summary = "Introspect database table columns", description = "Returns actual columns from information_schema for a given schema and table.")
    public List<java.util.Map<String, Object>> introspectTable(
            @Parameter(description = "Schema code.") @PathVariable("schema_code") String schemaCd,
            @Parameter(description = "Table/Object code.") @PathVariable("table_code") String tableCd) {
        logger.debug("Introspecting table columns. schemaCode={}, tableCode={}", schemaCd, tableCd);
        List<java.util.Map<String, Object>> columns = registryReadService.introspectColumns(schemaCd, tableCd);
        logger.debug("Table introspection resolved. schemaCode={}, tableCode={}, resultCount={}", schemaCd, tableCd, columns.size());
        return columns;
    }

    @GetMapping("/schemas/{schema_code}/tables")
    @Operation(summary = "Introspect database tables in a schema", description = "Returns actual tables and views from information_schema for a given schema.")
    public List<java.util.Map<String, Object>> introspectTables(
            @Parameter(description = "Schema code.") @PathVariable("schema_code") String schemaCd) {
        logger.debug("Introspecting schema tables. schemaCode={}", schemaCd);
        List<java.util.Map<String, Object>> tables = registryReadService.introspectTables(schemaCd);
        logger.debug("Schema table introspection resolved. schemaCode={}, resultCount={}", schemaCd, tables.size());
        return tables;
    }
}
