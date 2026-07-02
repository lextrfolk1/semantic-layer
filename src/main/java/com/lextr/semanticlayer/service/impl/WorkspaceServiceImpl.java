package com.lextr.semanticlayer.service.impl;

import com.lextr.semanticlayer.dao.WorkspaceDao;
import com.lextr.semanticlayer.dto.TenantWorkspaceDto;
import com.lextr.semanticlayer.dto.WorkspaceObjectRequestDto;
import com.lextr.semanticlayer.model.TenantWorkspaceRecord;
import com.lextr.semanticlayer.model.WorkspaceObjectRecord;
import com.lextr.semanticlayer.service.WorkspaceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class WorkspaceServiceImpl implements WorkspaceService {

    private static final Logger logger = LoggerFactory.getLogger(WorkspaceServiceImpl.class);

    private final WorkspaceDao workspaceDao;

    public WorkspaceServiceImpl(WorkspaceDao workspaceDao) {
        this.workspaceDao = workspaceDao;
    }

    @Override
    public List<TenantWorkspaceDto> findAll(String tenantCd) {
        logger.debug("Finding workspaces. tenantCd={}", tenantCd);
        List<TenantWorkspaceRecord> workspaces = workspaceDao.findAll(tenantCd);
        List<TenantWorkspaceDto> workspaceDtos = workspaces.stream().map(this::toDto).toList();
        logger.debug("Workspaces loaded. tenantCd={}, resultCount={}", tenantCd, workspaceDtos.size());
        return workspaceDtos;
    }

    @Override
    public TenantWorkspaceDto createWorkspace(String workspaceCd, String tenantCd, String workspaceNm,
                                               String workspaceDesc, String workspaceStatusCd, String createdBy) {
        logger.debug("Creating workspace. workspaceCd={}, tenantCd={}, workspaceStatusCd={}", workspaceCd, tenantCd, workspaceStatusCd);
        TenantWorkspaceRecord record = workspaceDao.insert(workspaceCd, tenantCd, workspaceNm,
                workspaceDesc, workspaceStatusCd, createdBy);
        TenantWorkspaceDto workspace = toDto(record);
        logger.info("Workspace created. workspaceCd={}, tenantCd={}", workspaceCd, tenantCd);
        return workspace;
    }

    @Override
    public TenantWorkspaceDto.WorkspaceObjectDto addObjectToWorkspace(String workspaceCd, WorkspaceObjectRequestDto request) {
        logger.debug(
                "Adding workspace object. workspaceCd={}, schemaCode={}, objectCode={}",
                workspaceCd,
                request.schema_cd(),
                request.object_cd()
        );
        WorkspaceObjectRecord record = workspaceDao.insertObject(
                workspaceCd,
                request.schema_cd(),
                request.object_cd(),
                request.added_by() == null || request.added_by().isBlank() ? "system" : request.added_by()
        );
        TenantWorkspaceDto.WorkspaceObjectDto workspaceObject = new TenantWorkspaceDto.WorkspaceObjectDto(
                record.schema_cd(),
                record.object_cd(),
                record.added_by(),
                record.added_ts()
        );
        logger.info(
                "Workspace object added. workspaceCd={}, schemaCode={}, objectCode={}",
                workspaceCd,
                request.schema_cd(),
                request.object_cd()
        );
        return workspaceObject;
    }

    private TenantWorkspaceDto toDto(TenantWorkspaceRecord record) {
        List<WorkspaceObjectRecord> objects = workspaceDao.findObjectsByWorkspace(record.workspace_cd());
        logger.debug("Loaded workspace objects. workspaceCd={}, resultCount={}", record.workspace_cd(), objects.size());
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
