package com.lextr.semanticlayer.api;

import com.lextr.semanticlayer.dto.LogicalHierarchyDto;
import com.lextr.semanticlayer.service.HierarchyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/hierarchies")
@Tag(name = "Hierarchies", description = "Logical hierarchy listing and creation operations.")
public class HierarchyController {

    private final HierarchyService hierarchyService;

    public HierarchyController(HierarchyService hierarchyService) {
        this.hierarchyService = hierarchyService;
    }

    @GetMapping
    @Operation(summary = "List hierarchies", description = "Returns all logical hierarchies, optionally filtered by tenant code.")
    public List<LogicalHierarchyDto> listHierarchies(
            @Parameter(description = "Tenant code filter. Pass null or omit for all.") @RequestParam(value = "tenant_cd", required = false) String tenantCd) {
        return hierarchyService.findAll(tenantCd);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create hierarchy", description = "Creates a new logical hierarchy.")
    public LogicalHierarchyDto createHierarchy(@RequestBody Map<String, String> body) {
        return hierarchyService.createHierarchy(
                body.get("hierarchy_cd"),
                body.get("hierarchy_nm"),
                body.getOrDefault("tenant_cd", "GLOBAL"),
                body.getOrDefault("hierarchy_status_cd", "ACTIVE"),
                body.getOrDefault("created_by", "system")
        );
    }
}
