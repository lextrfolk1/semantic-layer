package com.lextr.semanticlayer.dao;

import com.lextr.semanticlayer.model.TenantWorkspaceRecord;
import com.lextr.semanticlayer.model.WorkspaceObjectRecord;

import java.util.List;

public interface WorkspaceDao {

    List<TenantWorkspaceRecord> findAll(String tenantCd);

    TenantWorkspaceRecord insert(String workspaceCd, String tenantCd, String workspaceNm,
                                  String workspaceDesc, String workspaceStatusCd, String createdBy);

    List<WorkspaceObjectRecord> findObjectsByWorkspace(String workspaceCd);

    WorkspaceObjectRecord insertObject(String workspaceCd, String schemaCd, String objectCd, String addedBy);

    void deleteObject(String workspaceCd, String schemaCd, String objectCd);
}
