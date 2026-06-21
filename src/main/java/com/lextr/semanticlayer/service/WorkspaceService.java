package com.lextr.semanticlayer.service;

import com.lextr.semanticlayer.dto.TenantWorkspaceDto;

import java.util.List;

public interface WorkspaceService {

    List<TenantWorkspaceDto> findAll(String tenantCd);

    TenantWorkspaceDto createWorkspace(String workspaceCd, String tenantCd, String workspaceNm,
                                        String workspaceDesc, String workspaceStatusCd, String createdBy);
}
