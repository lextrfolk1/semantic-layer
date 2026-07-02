package com.lextr.semanticlayer.api;

import com.lextr.semanticlayer.dto.TenantWorkspaceDto;
import com.lextr.semanticlayer.dto.TenantWorkspaceRequestDto;
import com.lextr.semanticlayer.dto.WorkspaceObjectRequestDto;
import com.lextr.semanticlayer.service.WorkspaceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import jakarta.validation.Valid;

import java.util.List;

@RestController
@RequestMapping("/api/workspaces")
@Tag(name = "Workspaces", description = "Tenant workspace listing and creation operations.")
public class WorkspaceController {

    private static final Logger logger = LoggerFactory.getLogger(WorkspaceController.class);

    private final WorkspaceService workspaceService;

    public WorkspaceController(WorkspaceService workspaceService) {
        this.workspaceService = workspaceService;
    }

    @GetMapping
    @Operation(summary = "List workspaces", description = "Returns all tenant workspaces, optionally filtered by tenant code.")
    public List<TenantWorkspaceDto> listWorkspaces(
            @Parameter(description = "Tenant code filter. Pass null or omit for all.") @RequestParam(value = "tenant_cd", required = false) String tenantCd) {
        logger.debug("Listing workspaces. tenantCd={}", tenantCd);
        List<TenantWorkspaceDto> workspaces = workspaceService.findAll(tenantCd);
        logger.debug("Workspaces resolved. tenantCd={}, resultCount={}", tenantCd, workspaces.size());
        return workspaces;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create workspace", description = "Creates a new tenant workspace.")
    public TenantWorkspaceDto createWorkspace(@Valid @org.springframework.web.bind.annotation.RequestBody TenantWorkspaceRequestDto request) {
        String workspaceStatusCode = request.workspace_status_cd() == null ? "ACTIVE" : request.workspace_status_cd();
        logger.debug(
                "Creating workspace. workspaceCd={}, tenantCd={}, workspaceStatusCode={}",
                request.workspace_cd(),
                request.tenant_cd(),
                workspaceStatusCode
        );
        TenantWorkspaceDto workspace = workspaceService.createWorkspace(
                request.workspace_cd(),
                request.tenant_cd(),
                request.workspace_nm(),
                request.workspace_desc(),
                workspaceStatusCode,
                request.created_by() == null ? "system" : request.created_by()
        );
        logger.debug("Workspace created. workspaceCd={}, tenantCd={}", request.workspace_cd(), request.tenant_cd());
        return workspace;
    }

    @PostMapping("/{workspace_cd}/objects")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add workspace object", description = "Adds an object to an existing workspace.")
    public TenantWorkspaceDto.WorkspaceObjectDto addObjectToWorkspace(
            @Parameter(description = "Workspace code.") @PathVariable("workspace_cd") String workspaceCd,
            @Valid @RequestBody WorkspaceObjectRequestDto request) {
        logger.debug(
                "Adding object to workspace. workspaceCd={}, schemaCode={}, objectCode={}",
                workspaceCd,
                request.schema_cd(),
                request.object_cd()
        );
        TenantWorkspaceDto.WorkspaceObjectDto workspaceObject = workspaceService.addObjectToWorkspace(workspaceCd, request);
        logger.debug(
                "Workspace object added. workspaceCd={}, schemaCode={}, objectCode={}",
                workspaceCd,
                request.schema_cd(),
                request.object_cd()
        );
        return workspaceObject;
    }
}
