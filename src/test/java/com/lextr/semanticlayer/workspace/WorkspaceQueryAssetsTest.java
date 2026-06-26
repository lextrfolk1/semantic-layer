package com.lextr.semanticlayer.workspace;

import com.lextr.semanticlayer.util.SQLQueryLoaderUtil;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkspaceQueryAssetsTest {

    @Test
    void loadsTenantWorkspaceQueriesFromProperties() {
        SQLQueryLoaderUtil loader = new SQLQueryLoaderUtil(new DefaultResourceLoader());

        String workspaceListQuery = loader.getQuery("tenant_workspace.find_all");
        String workspaceInsertQuery = loader.getQuery("tenant_workspace.insert");
        String workspaceObjectListQuery = loader.getQuery("tenant_workspace_object.find_by_workspace");
        String workspaceObjectInsertQuery = loader.getQuery("tenant_workspace_object.insert");
        String workspaceObjectDeleteQuery = loader.getQuery("tenant_workspace_object.delete");

        assertTrue(workspaceListQuery.contains("FROM meta.tenant_workspace"));
        assertTrue(workspaceListQuery.contains("workspace_cd"));
        assertTrue(workspaceListQuery.contains("tenant_cd"));
        assertTrue(workspaceListQuery.contains("workspace_nm"));
        assertTrue(workspaceListQuery.contains("workspace_desc"));
        assertTrue(workspaceListQuery.contains("workspace_status_cd"));
        assertTrue(workspaceListQuery.contains("created_by"));
        assertTrue(workspaceListQuery.contains("created_ts"));
        assertTrue(workspaceListQuery.contains("updated_by"));
        assertTrue(workspaceListQuery.contains("updated_ts"));
        assertTrue(workspaceListQuery.contains(":tenant_cd"));
        assertTrue(workspaceListQuery.contains("OR tw.tenant_cd = 'GLOBAL'"));
        assertTrue(workspaceListQuery.contains("ORDER BY tw.workspace_cd"));

        assertTrue(workspaceInsertQuery.contains("INSERT INTO meta.tenant_workspace"));
        assertTrue(workspaceInsertQuery.contains(":workspace_cd"));
        assertTrue(workspaceInsertQuery.contains(":tenant_cd"));
        assertTrue(workspaceInsertQuery.contains(":workspace_nm"));
        assertTrue(workspaceInsertQuery.contains(":workspace_desc"));
        assertTrue(workspaceInsertQuery.contains(":workspace_status_cd"));
        assertTrue(workspaceInsertQuery.contains(":created_by"));
        assertTrue(workspaceInsertQuery.contains("RETURNING id, workspace_cd, tenant_cd, workspace_nm, workspace_desc, workspace_status_cd, created_by, created_ts, updated_by, updated_ts"));

        assertTrue(workspaceObjectListQuery.contains("FROM meta.tenant_workspace_object"));
        assertTrue(workspaceObjectListQuery.contains("workspace_cd = :workspace_cd"));
        assertTrue(workspaceObjectListQuery.contains("id"));
        assertTrue(workspaceObjectListQuery.contains("workspace_cd"));
        assertTrue(workspaceObjectListQuery.contains("schema_cd"));
        assertTrue(workspaceObjectListQuery.contains("object_cd"));
        assertTrue(workspaceObjectListQuery.contains("added_by"));
        assertTrue(workspaceObjectListQuery.contains("added_ts"));
        assertTrue(workspaceObjectListQuery.contains("ORDER BY two.schema_cd, two.object_cd"));

        assertTrue(workspaceObjectInsertQuery.contains("INSERT INTO meta.tenant_workspace_object"));
        assertTrue(workspaceObjectInsertQuery.contains(":workspace_cd"));
        assertTrue(workspaceObjectInsertQuery.contains(":schema_cd"));
        assertTrue(workspaceObjectInsertQuery.contains(":object_cd"));
        assertTrue(workspaceObjectInsertQuery.contains(":added_by"));
        assertTrue(workspaceObjectInsertQuery.contains("RETURNING id, workspace_cd, schema_cd, object_cd, added_by, added_ts"));

        assertTrue(workspaceObjectDeleteQuery.contains("DELETE FROM meta.tenant_workspace_object"));
        assertTrue(workspaceObjectDeleteQuery.contains("workspace_cd = :workspace_cd"));
        assertTrue(workspaceObjectDeleteQuery.contains("schema_cd = :schema_cd"));
        assertTrue(workspaceObjectDeleteQuery.contains("object_cd = :object_cd"));
    }
}
