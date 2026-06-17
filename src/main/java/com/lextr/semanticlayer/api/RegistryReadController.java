package com.lextr.semanticlayer.api;

import com.lextr.semanticlayer.model.DataConnectionRecord;
import com.lextr.semanticlayer.model.SchemaCatalogRecord;
import com.lextr.semanticlayer.service.RegistryReadService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/registry")
public class RegistryReadController {

    private final RegistryReadService registryReadService;

    public RegistryReadController(RegistryReadService registryReadService) {
        this.registryReadService = registryReadService;
    }

    @GetMapping("/schemas")
    public List<SchemaCatalogRecord> findSchemas(
            @RequestParam("client_id") String clientId,
            @RequestParam(value = "lifecycle_status_cd", required = false) String lifecycleStatusCode) {
        return registryReadService.findSchemas(clientId, lifecycleStatusCode);
    }

    @GetMapping("/schemas/{schema_code}")
    public SchemaCatalogRecord findSchema(
            @RequestParam("client_id") String clientId,
            @PathVariable("schema_code") String schemaCode) {
        return registryReadService.findSchema(clientId, schemaCode);
    }

    @GetMapping("/connections")
    public List<DataConnectionRecord> findConnections(
            @RequestParam("client_id") String clientId,
            @RequestParam(value = "engine_cd", required = false) String engineCode,
            @RequestParam(value = "is_active_flg", required = false) Boolean activeFlag) {
        return registryReadService.findConnections(clientId, engineCode, activeFlag);
    }

    @GetMapping("/connections/{connection_id}")
    public DataConnectionRecord findConnection(
            @RequestParam("client_id") String clientId,
            @PathVariable("connection_id") UUID connectionId) {
        return registryReadService.findConnection(clientId, connectionId);
    }
}
