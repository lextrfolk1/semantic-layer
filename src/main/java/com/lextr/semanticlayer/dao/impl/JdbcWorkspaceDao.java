package com.lextr.semanticlayer.dao.impl;

import com.lextr.semanticlayer.dao.WorkspaceDao;
import com.lextr.semanticlayer.exception.SemanticLayerException;
import com.lextr.semanticlayer.model.TenantWorkspaceRecord;
import com.lextr.semanticlayer.model.WorkspaceObjectRecord;
import com.lextr.semanticlayer.util.SQLQueryLoaderUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Repository
public class JdbcWorkspaceDao implements WorkspaceDao {

    private static final Logger logger = LoggerFactory.getLogger(JdbcWorkspaceDao.class);

    private static final String FIND_ALL = "tenant_workspace.find_all";
    private static final String INSERT = "tenant_workspace.insert";
    private static final String FIND_OBJECTS = "tenant_workspace_object.find_by_workspace";
    private static final String INSERT_OBJECT = "tenant_workspace_object.insert";
    private static final String DELETE_OBJECT = "tenant_workspace_object.delete";

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final SQLQueryLoaderUtil sqlQueryLoaderUtil;

    @Autowired
    public JdbcWorkspaceDao(
            ObjectProvider<NamedParameterJdbcTemplate> jdbcTemplateProvider,
            SQLQueryLoaderUtil sqlQueryLoaderUtil
    ) {
        this.jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
        this.sqlQueryLoaderUtil = sqlQueryLoaderUtil;
    }

    private void checkJdbcTemplate() {
        if (jdbcTemplate == null) {
            throw new SemanticLayerException("NamedParameterJdbcTemplate is not configured");
        }
    }

    private final RowMapper<TenantWorkspaceRecord> workspaceRowMapper = (rs, rowNum) -> new TenantWorkspaceRecord(
            rs.getLong("id"),
            rs.getString("workspace_cd"),
            rs.getString("tenant_cd"),
            rs.getString("workspace_nm"),
            rs.getString("workspace_desc"),
            rs.getString("workspace_status_cd"),
            rs.getString("created_by"),
            getOffsetDateTime(rs, "created_ts"),
            rs.getString("updated_by"),
            getOffsetDateTime(rs, "updated_ts")
    );

    private final RowMapper<WorkspaceObjectRecord> objectRowMapper = (rs, rowNum) -> new WorkspaceObjectRecord(
            rs.getLong("id"),
            rs.getString("workspace_cd"),
            rs.getString("schema_cd"),
            rs.getString("object_cd"),
            rs.getString("added_by"),
            getOffsetDateTime(rs, "added_ts")
    );

    @Override
    public List<TenantWorkspaceRecord> findAll(String tenantCd) {
        checkJdbcTemplate();
        logger.debug("Executing workspace lookup. tenantCd={}, queryKey={}", tenantCd, FIND_ALL);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("tenant_cd", tenantCd);
        List<TenantWorkspaceRecord> workspaces = jdbcTemplate.query(sqlQueryLoaderUtil.getQuery(FIND_ALL), params, workspaceRowMapper);
        logger.debug("Workspace lookup completed. tenantCd={}, resultCount={}", tenantCd, workspaces.size());
        return workspaces;
    }

    @Override
    public TenantWorkspaceRecord insert(String workspaceCd, String tenantCd, String workspaceNm,
                                         String workspaceDesc, String workspaceStatusCd, String createdBy) {
        checkJdbcTemplate();
        logger.debug("Executing workspace insert. workspaceCd={}, tenantCd={}, workspaceStatusCd={}, queryKey={}", workspaceCd, tenantCd, workspaceStatusCd, INSERT);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("workspace_cd", workspaceCd)
                .addValue("tenant_cd", tenantCd)
                .addValue("workspace_nm", workspaceNm)
                .addValue("workspace_desc", workspaceDesc)
                .addValue("workspace_status_cd", workspaceStatusCd)
                .addValue("created_by", createdBy);
        TenantWorkspaceRecord record = jdbcTemplate.query(sqlQueryLoaderUtil.getQuery(INSERT), params, workspaceRowMapper)
                .stream().findFirst()
                .orElseThrow(() -> new SemanticLayerException("Failed to insert workspace: " + workspaceCd));
        logger.debug("Workspace insert completed. workspaceCd={}, tenantCd={}", workspaceCd, tenantCd);
        return record;
    }

    @Override
    public List<WorkspaceObjectRecord> findObjectsByWorkspace(String workspaceCd) {
        checkJdbcTemplate();
        logger.debug("Executing workspace object lookup. workspaceCd={}, queryKey={}", workspaceCd, FIND_OBJECTS);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("workspace_cd", workspaceCd);
        List<WorkspaceObjectRecord> objects = jdbcTemplate.query(sqlQueryLoaderUtil.getQuery(FIND_OBJECTS), params, objectRowMapper);
        logger.debug("Workspace object lookup completed. workspaceCd={}, resultCount={}", workspaceCd, objects.size());
        return objects;
    }

    @Override
    public WorkspaceObjectRecord insertObject(String workspaceCd, String schemaCd, String objectCd, String addedBy) {
        checkJdbcTemplate();
        logger.debug("Executing workspace object insert. workspaceCd={}, schemaCode={}, objectCode={}, queryKey={}", workspaceCd, schemaCd, objectCd, INSERT_OBJECT);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("workspace_cd", workspaceCd)
                .addValue("schema_cd", schemaCd)
                .addValue("object_cd", objectCd)
                .addValue("added_by", addedBy);
        WorkspaceObjectRecord record = jdbcTemplate.query(sqlQueryLoaderUtil.getQuery(INSERT_OBJECT), params, objectRowMapper)
                .stream().findFirst()
                .orElseThrow(() -> new SemanticLayerException("Failed to insert workspace object"));
        logger.debug("Workspace object insert completed. workspaceCd={}, schemaCode={}, objectCode={}", workspaceCd, schemaCd, objectCd);
        return record;
    }

    @Override
    public void deleteObject(String workspaceCd, String schemaCd, String objectCd) {
        checkJdbcTemplate();
        logger.debug("Executing workspace object delete. workspaceCd={}, schemaCode={}, objectCode={}, queryKey={}", workspaceCd, schemaCd, objectCd, DELETE_OBJECT);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("workspace_cd", workspaceCd)
                .addValue("schema_cd", schemaCd)
                .addValue("object_cd", objectCd);
        int affectedRows = jdbcTemplate.update(sqlQueryLoaderUtil.getQuery(DELETE_OBJECT), params);
        logger.debug("Workspace object delete completed. workspaceCd={}, schemaCode={}, objectCode={}, affectedRows={}", workspaceCd, schemaCd, objectCd, affectedRows);
    }

    private static OffsetDateTime getOffsetDateTime(java.sql.ResultSet rs, String col) throws java.sql.SQLException {
        Timestamp ts = rs.getTimestamp(col);
        return ts == null ? null : ts.toInstant().atOffset(ZoneOffset.UTC);
    }
}
