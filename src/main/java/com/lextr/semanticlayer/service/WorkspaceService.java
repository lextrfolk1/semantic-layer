package com.lextr.semanticlayer.service;

import com.lextr.semanticlayer.dto.TenantWorkspaceDto;
import com.lextr.semanticlayer.dto.WorkspaceObjectRequestDto;

import java.util.List;

public interface WorkspaceService {

    List<TenantWorkspaceDto> findAll(String tenantCd);

    TenantWorkspaceDto createWorkspace(String workspaceCd, String tenantCd, String workspaceNm,
                                        String workspaceDesc, String workspaceStatusCd, String createdBy);

    TenantWorkspaceDto.WorkspaceObjectDto addObjectToWorkspace(String workspaceCd, WorkspaceObjectRequestDto request);
}
