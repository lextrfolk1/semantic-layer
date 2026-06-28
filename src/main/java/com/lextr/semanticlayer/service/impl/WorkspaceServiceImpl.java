package com.lextr.semanticlayer.service.impl;

import com.lextr.semanticlayer.dao.WorkspaceDao;
import com.lextr.semanticlayer.dto.TenantWorkspaceDto;
import com.lextr.semanticlayer.dto.WorkspaceObjectRequestDto;
import com.lextr.semanticlayer.model.TenantWorkspaceRecord;
import com.lextr.semanticlayer.model.WorkspaceObjectRecord;
import com.lextr.semanticlayer.service.WorkspaceService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class WorkspaceServiceImpl implements WorkspaceService {

    private final WorkspaceDao workspaceDao;

    public WorkspaceServiceImpl(WorkspaceDao workspaceDao) {
        this.workspaceDao = workspaceDao;
    }

    @Override
    public List<TenantWorkspaceDto> findAll(String tenantCd) {
        List<TenantWorkspaceRecord> workspaces = workspaceDao.findAll(tenantCd);
        return workspaces.stream().map(this::toDto).toList();
    }

    @Override
    public TenantWorkspaceDto createWorkspace(String workspaceCd, String tenantCd, String workspaceNm,
                                               String workspaceDesc, String workspaceStatusCd, String createdBy) {
        TenantWorkspaceRecord record = workspaceDao.insert(workspaceCd, tenantCd, workspaceNm,
                workspaceDesc, workspaceStatusCd, createdBy);
        return toDto(record);
    }

    @Override
    public TenantWorkspaceDto.WorkspaceObjectDto addObjectToWorkspace(String workspaceCd, WorkspaceObjectRequestDto request) {
        WorkspaceObjectRecord record = workspaceDao.insertObject(
                workspaceCd,
                request.schema_cd(),
                request.object_cd(),
                request.added_by() == null || request.added_by().isBlank() ? "system" : request.added_by()
        );
        return new TenantWorkspaceDto.WorkspaceObjectDto(
                record.schema_cd(),
                record.object_cd(),
                record.added_by(),
                record.added_ts()
        );
    }

    private TenantWorkspaceDto toDto(TenantWorkspaceRecord record) {
        List<WorkspaceObjectRecord> objects = workspaceDao.findObjectsByWorkspace(record.workspace_cd());
        List<TenantWorkspaceDto.WorkspaceObjectDto> objectDtos = objects.stream()
                .map(o -> new TenantWorkspaceDto.WorkspaceObjectDto(
                        o.schema_cd(), o.object_cd(), o.added_by(), o.added_ts()))
                .toList();

        return new TenantWorkspaceDto(
                record.id(),
                record.workspace_cd(),
                record.tenant_cd(),
                record.workspace_nm(),
                record.workspace_desc(),
                record.workspace_status_cd(),
                record.created_by(),
                record.created_ts(),
                record.updated_by(),
                record.updated_ts(),
                objectDtos
        );
    }
}
