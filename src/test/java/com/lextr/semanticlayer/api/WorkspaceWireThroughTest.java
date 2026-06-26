package com.lextr.semanticlayer.api;

import com.lextr.semanticlayer.dao.WorkspaceDao;
import com.lextr.semanticlayer.model.TenantWorkspaceRecord;
import com.lextr.semanticlayer.model.WorkspaceObjectRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener;
import org.springframework.test.context.support.DirtiesContextBeforeModesTestExecutionListener;
import org.springframework.test.context.support.DirtiesContextTestExecutionListener;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringJUnitWebConfig(classes = {
        com.lextr.semanticlayer.SemanticLayerApplication.class,
        WorkspaceWireThroughTest.WorkspaceWireThroughTestConfiguration.class
})
@TestExecutionListeners(
        listeners = {
                DependencyInjectionTestExecutionListener.class,
                DirtiesContextBeforeModesTestExecutionListener.class,
                DirtiesContextTestExecutionListener.class
        },
        mergeMode = TestExecutionListeners.MergeMode.REPLACE_DEFAULTS
)
class WorkspaceWireThroughTest {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private RecordingWorkspaceDao workspaceDao;

    @BeforeEach
    void resetDependencies() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        workspaceDao.reset();
    }

    @Test
    void routesWorkspaceListingThroughSpringAndMapsNestedObjects() throws Exception {
        OffsetDateTime timestamp = OffsetDateTime.parse("2026-06-24T10:15:30Z");
        workspaceDao.workspaces.add(new TenantWorkspaceRecord(
                11L,
                "WS-ALL",
                "client-a",
                "All Workspaces",
                "Shared workspace catalog",
                "ACTIVE",
                "platform",
                timestamp,
                "platform",
                timestamp
        ));
        workspaceDao.objectsByWorkspace.put("WS-ALL", List.of(
                new WorkspaceObjectRecord(101L, "WS-ALL", "meta", "gl_balance", "platform", timestamp),
                new WorkspaceObjectRecord(102L, "WS-ALL", "meta", "gl_account", "platform", timestamp)
        ));

        mockMvc.perform(get("/api/workspaces")
                        .queryParam("tenant_cd", "client-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].workspace_cd").value("WS-ALL"))
                .andExpect(jsonPath("$[0].tenant_cd").value("client-a"))
                .andExpect(jsonPath("$[0].workspace_status_cd").value("ACTIVE"))
                .andExpect(jsonPath("$[0].objects[0].schema_cd").value("meta"))
                .andExpect(jsonPath("$[0].objects[0].object_cd").value("gl_balance"))
                .andExpect(jsonPath("$[0].objects[1].object_cd").value("gl_account"));

        assertEquals("client-a", workspaceDao.lastTenantCdQuery);
        assertEquals(List.of("WS-ALL"), workspaceDao.workspaceLookups);
    }

    @Test
    void routesWorkspaceCreationThroughSpringAndDefaultsFields() throws Exception {
        OffsetDateTime timestamp = OffsetDateTime.parse("2026-06-24T10:15:30Z");
        workspaceDao.insertedWorkspace = new TenantWorkspaceRecord(
                12L,
                "WS-BHC-A",
                "client-b",
                "BHC Workspace",
                "Business hierarchy workspace",
                "ACTIVE",
                "system",
                timestamp,
                "system",
                timestamp
        );
        workspaceDao.objectsByWorkspace.put("WS-BHC-A", List.of(
                new WorkspaceObjectRecord(201L, "WS-BHC-A", "meta", "gl_balance", "system", timestamp)
        ));

        mockMvc.perform(post("/api/workspaces")
                        .contentType("application/json")
                        .content("""
                                {
                                  "workspace_cd": "WS-BHC-A",
                                  "tenant_cd": "client-b",
                                  "workspace_nm": "BHC Workspace",
                                  "workspace_desc": "Business hierarchy workspace"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.workspace_cd").value("WS-BHC-A"))
                .andExpect(jsonPath("$.tenant_cd").value("client-b"))
                .andExpect(jsonPath("$.workspace_status_cd").value("ACTIVE"))
                .andExpect(jsonPath("$.created_by").value("system"))
                .andExpect(jsonPath("$.objects[0].object_cd").value("gl_balance"));

        assertEquals("WS-BHC-A", workspaceDao.lastWorkspaceCdInserted);
        assertEquals("client-b", workspaceDao.lastTenantCdInserted);
        assertEquals("BHC Workspace", workspaceDao.lastWorkspaceNmInserted);
        assertEquals("Business hierarchy workspace", workspaceDao.lastWorkspaceDescInserted);
        assertEquals("ACTIVE", workspaceDao.lastWorkspaceStatusCdInserted);
        assertEquals("system", workspaceDao.lastCreatedByInserted);
        assertEquals(List.of("WS-BHC-A"), workspaceDao.workspaceLookups);
    }

    @TestConfiguration
    static class WorkspaceWireThroughTestConfiguration {

        @Bean
        @Primary
        RecordingWorkspaceDao recordingWorkspaceDao() {
            return new RecordingWorkspaceDao();
        }
    }

    static final class RecordingWorkspaceDao implements WorkspaceDao {

        private final List<TenantWorkspaceRecord> workspaces = new ArrayList<>();
        private final Map<String, List<WorkspaceObjectRecord>> objectsByWorkspace = new HashMap<>();
        private final List<String> workspaceLookups = new ArrayList<>();
        private String lastTenantCdQuery;
        private String lastWorkspaceCdInserted;
        private String lastTenantCdInserted;
        private String lastWorkspaceNmInserted;
        private String lastWorkspaceDescInserted;
        private String lastWorkspaceStatusCdInserted;
        private String lastCreatedByInserted;
        private TenantWorkspaceRecord insertedWorkspace;

        void reset() {
            workspaces.clear();
            objectsByWorkspace.clear();
            workspaceLookups.clear();
            lastTenantCdQuery = null;
            lastWorkspaceCdInserted = null;
            lastTenantCdInserted = null;
            lastWorkspaceNmInserted = null;
            lastWorkspaceDescInserted = null;
            lastWorkspaceStatusCdInserted = null;
            lastCreatedByInserted = null;
            insertedWorkspace = null;
        }

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
            lastWorkspaceNmInserted = workspaceNm;
            lastWorkspaceDescInserted = workspaceDesc;
            lastWorkspaceStatusCdInserted = workspaceStatusCd;
            lastCreatedByInserted = createdBy;
            return insertedWorkspace;
        }

        @Override
        public List<WorkspaceObjectRecord> findObjectsByWorkspace(String workspaceCd) {
            workspaceLookups.add(workspaceCd);
            return objectsByWorkspace.getOrDefault(workspaceCd, List.of());
        }

        @Override
        public WorkspaceObjectRecord insertObject(String workspaceCd, String schemaCd, String objectCd, String addedBy) {
            throw new UnsupportedOperationException("Not used");
        }

        @Override
        public void deleteObject(String workspaceCd, String schemaCd, String objectCd) {
            throw new UnsupportedOperationException("Not used");
        }
    }
}
