package com.lextr.semanticlayer.dao.impl;

import com.lextr.semanticlayer.dao.WorkspaceDao;
import com.lextr.semanticlayer.exception.SemanticLayerException;
import com.lextr.semanticlayer.model.TenantWorkspaceRecord;
import com.lextr.semanticlayer.model.WorkspaceObjectRecord;
import com.lextr.semanticlayer.util.SQLQueryLoaderUtil;
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

    private static final String FIND_ALL = "tenant_workspace.find_all";
    private static final String INSERT = "tenant_workspace.insert";
    private static final String FIND_OBJECTS = "workspace_object.find_by_workspace";
    private static final String INSERT_OBJECT = "workspace_object.insert";
    private static final String DELETE_OBJECT = "workspace_object.delete";

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
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("tenant_cd", tenantCd);
        return jdbcTemplate.query(sqlQueryLoaderUtil.getQuery(FIND_ALL), params, workspaceRowMapper);
    }

    @Override
    public TenantWorkspaceRecord insert(String workspaceCd, String tenantCd, String workspaceNm,
                                         String workspaceDesc, String workspaceStatusCd, String createdBy) {
        checkJdbcTemplate();
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("workspace_cd", workspaceCd)
                .addValue("tenant_cd", tenantCd)
                .addValue("workspace_nm", workspaceNm)
                .addValue("workspace_desc", workspaceDesc)
                .addValue("workspace_status_cd", workspaceStatusCd)
                .addValue("created_by", createdBy);
        return jdbcTemplate.query(sqlQueryLoaderUtil.getQuery(INSERT), params, workspaceRowMapper)
                .stream().findFirst()
                .orElseThrow(() -> new SemanticLayerException("Failed to insert workspace: " + workspaceCd));
    }

    @Override
    public List<WorkspaceObjectRecord> findObjectsByWorkspace(String workspaceCd) {
        checkJdbcTemplate();
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("workspace_cd", workspaceCd);
        return jdbcTemplate.query(sqlQueryLoaderUtil.getQuery(FIND_OBJECTS), params, objectRowMapper);
    }

    @Override
    public WorkspaceObjectRecord insertObject(String workspaceCd, String schemaCd, String objectCd, String addedBy) {
        checkJdbcTemplate();
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("workspace_cd", workspaceCd)
                .addValue("schema_cd", schemaCd)
                .addValue("object_cd", objectCd)
                .addValue("added_by", addedBy);
        return jdbcTemplate.query(sqlQueryLoaderUtil.getQuery(INSERT_OBJECT), params, objectRowMapper)
                .stream().findFirst()
                .orElseThrow(() -> new SemanticLayerException("Failed to insert workspace object"));
    }

    @Override
    public void deleteObject(String workspaceCd, String schemaCd, String objectCd) {
        checkJdbcTemplate();
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("workspace_cd", workspaceCd)
                .addValue("schema_cd", schemaCd)
                .addValue("object_cd", objectCd);
        jdbcTemplate.update(sqlQueryLoaderUtil.getQuery(DELETE_OBJECT), params);
    }

    private static OffsetDateTime getOffsetDateTime(java.sql.ResultSet rs, String col) throws java.sql.SQLException {
        Timestamp ts = rs.getTimestamp(col);
        return ts == null ? null : ts.toInstant().atOffset(ZoneOffset.UTC);
    }
}
