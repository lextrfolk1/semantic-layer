package com.lextr.semanticlayer.governance;

import com.lextr.semanticlayer.util.SQLQueryLoaderUtil;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import static org.junit.jupiter.api.Assertions.assertTrue;

class LogicalHierarchyQueryAssetsTest {

    @Test
    void loadsLogicalHierarchyQueriesFromProperties() {
        SQLQueryLoaderUtil loader = new SQLQueryLoaderUtil(new DefaultResourceLoader());

        String hierarchyListQuery = loader.getQuery("logical_hierarchy.find_all");
        String hierarchyInsertQuery = loader.getQuery("logical_hierarchy.insert");
        String levelListQuery = loader.getQuery("logical_hierarchy.find_levels");
        String levelInsertQuery = loader.getQuery("logical_hierarchy.insert_level");

        assertTrue(hierarchyListQuery.contains("FROM meta.logical_hierarchy"));
        assertTrue(hierarchyListQuery.contains("SELECT lh.id, lh.hierarchy_cd, lh.hierarchy_nm, lh.tenant_cd"));
        assertTrue(hierarchyListQuery.contains("hierarchy_status_cd"));
        assertTrue(hierarchyListQuery.contains("created_by"));
        assertTrue(hierarchyListQuery.contains("updated_ts"));
        assertTrue(hierarchyListQuery.contains("CAST(:tenant_cd AS varchar) IS NULL"));
        assertTrue(hierarchyListQuery.contains("lh.tenant_cd = :tenant_cd"));
        assertTrue(hierarchyListQuery.contains("lh.tenant_cd = 'GLOBAL'"));
        assertTrue(hierarchyListQuery.contains("ORDER BY lh.hierarchy_cd"));

        assertTrue(hierarchyInsertQuery.contains("INSERT INTO meta.logical_hierarchy"));
        assertTrue(hierarchyInsertQuery.contains(":hierarchy_cd"));
        assertTrue(hierarchyInsertQuery.contains(":hierarchy_nm"));
        assertTrue(hierarchyInsertQuery.contains(":tenant_cd"));
        assertTrue(hierarchyInsertQuery.contains(":hierarchy_status_cd"));
        assertTrue(hierarchyInsertQuery.contains(":created_by"));
        assertTrue(hierarchyInsertQuery.contains("RETURNING id, hierarchy_cd, hierarchy_nm, tenant_cd, hierarchy_status_cd, created_by, created_ts, updated_by, updated_ts"));

        assertTrue(levelListQuery.contains("FROM meta.logical_hierarchy_level"));
        assertTrue(levelListQuery.contains("SELECT lhl.id, lhl.hierarchy_cd, lhl.level_nbr, lhl.level_label, lhl.attribute_cd, lhl.code_cd, lhl.object_ref"));
        assertTrue(levelListQuery.contains("lhl.hierarchy_cd = :hierarchy_cd"));
        assertTrue(levelListQuery.contains("ORDER BY lhl.level_nbr"));

        assertTrue(levelInsertQuery.contains("INSERT INTO meta.logical_hierarchy_level"));
        assertTrue(levelInsertQuery.contains(":hierarchy_cd"));
        assertTrue(levelInsertQuery.contains(":level_nbr"));
        assertTrue(levelInsertQuery.contains(":level_label"));
        assertTrue(levelInsertQuery.contains(":attribute_cd"));
        assertTrue(levelInsertQuery.contains(":code_cd"));
        assertTrue(levelInsertQuery.contains(":object_ref"));
        assertTrue(levelInsertQuery.contains("RETURNING id, hierarchy_cd, level_nbr, level_label, attribute_cd, code_cd, object_ref"));
    }

    @Test
    void returnsExpectedSnakeCaseColumnsForTheScreen() {
        SQLQueryLoaderUtil loader = new SQLQueryLoaderUtil(new DefaultResourceLoader());

        String hierarchyListQuery = loader.getQuery("logical_hierarchy.find_all");
        String levelListQuery = loader.getQuery("logical_hierarchy.find_levels");

        assertTrue(hierarchyListQuery.contains("lh.id, lh.hierarchy_cd, lh.hierarchy_nm, lh.tenant_cd, lh.hierarchy_status_cd, lh.created_by, lh.created_ts, lh.updated_by, lh.updated_ts"));
        assertTrue(levelListQuery.contains("lhl.id, lhl.hierarchy_cd, lhl.level_nbr, lhl.level_label, lhl.attribute_cd, lhl.code_cd, lhl.object_ref"));
    }
}
