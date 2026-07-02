package com.lextr.semanticlayer.service.impl;

import com.lextr.semanticlayer.dao.WorkspaceDao;
import com.lextr.semanticlayer.dto.TenantWorkspaceDto;
import com.lextr.semanticlayer.dto.WorkspaceObjectRequestDto;
import com.lextr.semanticlayer.model.TenantWorkspaceRecord;
import com.lextr.semanticlayer.model.WorkspaceObjectRecord;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WorkspaceServiceImplTest {

    @Test
    void addObjectToWorkspaceDelegatesToDaoAndMapsResponse() {
        RecordingWorkspaceDao dao = new RecordingWorkspaceDao();
        dao.insertedObject = new WorkspaceObjectRecord(
                55L,
                "WS-1",
                "meta",
                "gl_balance",
                "tester",
                OffsetDateTime.parse("2026-06-24T10:15:30Z")
        );

        WorkspaceServiceImpl service = new WorkspaceServiceImpl(dao);

        TenantWorkspaceDto.WorkspaceObjectDto response = service.addObjectToWorkspace(
                "WS-1",
                new WorkspaceObjectRequestDto("meta", "gl_balance", "tester")
        );

        assertEquals("WS-1", dao.lastWorkspaceCdAdded);
        assertEquals("meta", dao.lastSchemaCdAdded);
        assertEquals("gl_balance", dao.lastObjectCdAdded);
        assertEquals("tester", dao.lastAddedByAdded);
        assertEquals("meta", response.schema_cd());
        assertEquals("gl_balance", response.object_cd());
        assertEquals("tester", response.added_by());
    }

    private static final class RecordingWorkspaceDao implements WorkspaceDao {
        private final List<TenantWorkspaceRecord> workspaces = new ArrayList<>();
        private WorkspaceObjectRecord insertedObject;
        private String lastTenantCdQuery;
        private String lastWorkspaceCdAdded;
        private String lastSchemaCdAdded;
        private String lastObjectCdAdded;
        private String lastAddedByAdded;

        @Override
        public List<TenantWorkspaceRecord> findAll(String tenantCd) {
            lastTenantCdQuery = tenantCd;
            return workspaces;
        }

        @Override
        public TenantWorkspaceRecord insert(String workspaceCd, String tenantCd, String workspaceNm,
                                             String workspaceDesc, String workspaceStatusCd, String createdBy) {
            return null;
        }

        @Override
        public List<WorkspaceObjectRecord> findObjectsByWorkspace(String workspaceCd) {
            return List.of();
        }

        @Override
        public WorkspaceObjectRecord insertObject(String workspaceCd, String schemaCd, String objectCd, String addedBy) {
            lastWorkspaceCdAdded = workspaceCd;
            lastSchemaCdAdded = schemaCd;
            lastObjectCdAdded = objectCd;
            lastAddedByAdded = addedBy;
            return insertedObject;
        }

        @Override
        public void deleteObject(String workspaceCd, String schemaCd, String objectCd) {
        }
    }
}
