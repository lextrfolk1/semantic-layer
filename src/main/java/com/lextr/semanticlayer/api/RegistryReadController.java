package com.lextr.semanticlayer.api;

import com.lextr.semanticlayer.dto.DataConnectionDto;
import com.lextr.semanticlayer.dto.SchemaCatalogDto;
import com.lextr.semanticlayer.service.RegistryReadService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
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

    private final RegistryReadService registryReadService;

    public RegistryReadController(RegistryReadService registryReadService) {
        this.registryReadService = registryReadService;
    }

    @GetMapping("/schemas")
    @Operation(summary = "List schemas", description = "Returns schemas visible for the supplied client.")
    public List<SchemaCatalogDto> findSchemas(
            @Parameter(description = "Tenant identifier.") @RequestParam("client_id") String clientId,
            @Parameter(description = "Optional lifecycle status filter.") @RequestParam(value = "lifecycle_status_cd", required = false) String lifecycleStatusCode) {
        return registryReadService.findSchemas(clientId, lifecycleStatusCode);
    }

    @GetMapping("/schemas/{schema_code}")
    @Operation(summary = "Get schema", description = "Returns one schema by schema code for the supplied client.")
    public SchemaCatalogDto findSchema(
            @Parameter(description = "Tenant identifier.") @RequestParam("client_id") String clientId,
            @Parameter(description = "Schema code.") @PathVariable("schema_code") String schemaCode) {
        return registryReadService.findSchema(clientId, schemaCode);
    }

    @GetMapping("/connections")
    @Operation(summary = "List connections", description = "Returns data connections visible for the supplied client.")
    public List<DataConnectionDto> findConnections(
            @Parameter(description = "Tenant identifier.") @RequestParam("client_id") String clientId,
            @Parameter(description = "Optional engine filter.") @RequestParam(value = "engine_cd", required = false) String engineCode,
            @Parameter(description = "Optional active flag filter.") @RequestParam(value = "is_active_flg", required = false) Boolean activeFlag) {
        return registryReadService.findConnections(clientId, engineCode, activeFlag);
    }

    @GetMapping("/connections/{connection_id}")
    @Operation(summary = "Get connection", description = "Returns one data connection by identifier for the supplied client.")
    public DataConnectionDto findConnection(
            @Parameter(description = "Tenant identifier.") @RequestParam("client_id") String clientId,
            @Parameter(description = "Connection identifier.") @PathVariable("connection_id") UUID connectionId) {
        return registryReadService.findConnection(clientId, connectionId);
    }

    @GetMapping("/schemas/{schema_code}/tables/{table_code}/introspect")
    @Operation(summary = "Introspect database table columns", description = "Returns actual columns from information_schema for a given schema and table.")
    public List<java.util.Map<String, Object>> introspectTable(
            @Parameter(description = "Schema code.") @PathVariable("schema_code") String schemaCd,
            @Parameter(description = "Table/Object code.") @PathVariable("table_code") String tableCd) {
        return registryReadService.introspectColumns(schemaCd, tableCd);
    }

    @GetMapping("/schemas/{schema_code}/tables")
    @Operation(summary = "Introspect database tables in a schema", description = "Returns actual tables and views from information_schema for a given schema.")
    public List<java.util.Map<String, Object>> introspectTables(
            @Parameter(description = "Schema code.") @PathVariable("schema_code") String schemaCd) {
        return registryReadService.introspectTables(schemaCd);
    }
}
