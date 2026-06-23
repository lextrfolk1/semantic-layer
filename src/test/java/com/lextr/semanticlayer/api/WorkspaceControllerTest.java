package com.lextr.semanticlayer.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lextr.semanticlayer.dao.WorkspaceDao;
import com.lextr.semanticlayer.model.TenantWorkspaceRecord;
import com.lextr.semanticlayer.model.WorkspaceObjectRecord;
import com.lextr.semanticlayer.service.WorkspaceService;
import com.lextr.semanticlayer.service.impl.WorkspaceServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class WorkspaceControllerTest {

    @Test
    void listsWorkspacesSuccessfully() throws Exception {
        RecordingWorkspaceDao dao = new RecordingWorkspaceDao();
        dao.workspaces.add(new TenantWorkspaceRecord(
                1L, "WS-TEST", "test-tenant", "Test Workspace", "Desc", "ACTIVE",
                "system", OffsetDateTime.parse("2026-06-23T10:00:00+05:30"), null, null
        ));
        MockMvc mockMvc = mockMvc(dao);

        mockMvc.perform(get("/api/workspaces")
                        .queryParam("tenant_cd", "test-tenant"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].workspace_cd").value("WS-TEST"))
                .andExpect(jsonPath("$[0].tenant_cd").value("test-tenant"));

        assertEquals("test-tenant", dao.lastTenantCdQuery);
    }

    @Test
    void createsWorkspaceSuccessfully() throws Exception {
        RecordingWorkspaceDao dao = new RecordingWorkspaceDao();
        MockMvc mockMvc = mockMvc(dao);

        Map<String, String> body = new HashMap<>();
        body.put("workspace_cd", "WS-NEW");
        body.put("tenant_cd", "new-tenant");
        body.put("workspace_nm", "New Workspace");
        body.put("workspace_desc", "Description");

        mockMvc.perform(post("/api/workspaces")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new ObjectMapper().writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.workspace_cd").value("WS-NEW"))
                .andExpect(jsonPath("$.tenant_cd").value("new-tenant"));

        assertEquals("WS-NEW", dao.lastWorkspaceCdInserted);
        assertEquals("new-tenant", dao.lastTenantCdInserted);
    }

    private static MockMvc mockMvc(WorkspaceDao dao) {
        WorkspaceService service = new WorkspaceServiceImpl(dao);
        WorkspaceController controller = new WorkspaceController(service);
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        return MockMvcBuilders.standaloneSetup(controller)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    private static final class RecordingWorkspaceDao implements WorkspaceDao {
        private final List<TenantWorkspaceRecord> workspaces = new ArrayList<>();
        private String lastTenantCdQuery;
        private String lastWorkspaceCdInserted;
        private String lastTenantCdInserted;

        @Override
        public List<TenantWorkspaceRecord> findAll(String tenantCd) {
            lastTenantCdQuery = tenantCd;
            return workspaces;
        }

        @Override
        public TenantWorkspaceRecord insert(String workspaceCd, String tenantCd, String workspaceNm,
                                             String workspaceDesc, String workspaceStatusCd, String createdBy) {
            lastWorkspaceCdInserted = workspaceCd;
            lastTenantCdInserted = tenantCd;
            return new TenantWorkspaceRecord(
                    2L, workspaceCd, tenantCd, workspaceNm, workspaceDesc, workspaceStatusCd,
                    createdBy, OffsetDateTime.now(), null, null
            );
        }

        @Override
        public List<WorkspaceObjectRecord> findObjectsByWorkspace(String workspaceCd) {
            return List.of();
        }

        @Override
        public WorkspaceObjectRecord insertObject(String workspaceCd, String schemaCd, String objectCd, String addedBy) {
            return null;
        }

        @Override
        public void deleteObject(String workspaceCd, String schemaCd, String objectCd) {
        }
    }
}
