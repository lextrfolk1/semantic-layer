package com.lextr.semanticlayer.api;

import com.lextr.semanticlayer.dto.TenantWorkspaceDto;
import com.lextr.semanticlayer.dto.TenantWorkspaceRequestDto;
import com.lextr.semanticlayer.dto.WorkspaceObjectRequestDto;
import com.lextr.semanticlayer.service.WorkspaceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
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

    private final WorkspaceService workspaceService;

    public WorkspaceController(WorkspaceService workspaceService) {
        this.workspaceService = workspaceService;
    }

    @GetMapping
    @Operation(summary = "List workspaces", description = "Returns all tenant workspaces, optionally filtered by tenant code.")
    public List<TenantWorkspaceDto> listWorkspaces(
            @Parameter(description = "Tenant code filter. Pass null or omit for all.") @RequestParam(value = "tenant_cd", required = false) String tenantCd) {
        return workspaceService.findAll(tenantCd);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create workspace", description = "Creates a new tenant workspace.")
    public TenantWorkspaceDto createWorkspace(@Valid @org.springframework.web.bind.annotation.RequestBody TenantWorkspaceRequestDto request) {
        return workspaceService.createWorkspace(
                request.workspace_cd(),
                request.tenant_cd(),
                request.workspace_nm(),
                request.workspace_desc(),
                request.workspace_status_cd() == null ? "ACTIVE" : request.workspace_status_cd(),
                request.created_by() == null ? "system" : request.created_by()
        );
    }

    @PostMapping("/{workspace_cd}/objects")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add workspace object", description = "Adds an object to an existing workspace.")
    public TenantWorkspaceDto.WorkspaceObjectDto addObjectToWorkspace(
            @Parameter(description = "Workspace code.") @PathVariable("workspace_cd") String workspaceCd,
            @Valid @RequestBody WorkspaceObjectRequestDto request) {
        return workspaceService.addObjectToWorkspace(workspaceCd, request);
    }
}
