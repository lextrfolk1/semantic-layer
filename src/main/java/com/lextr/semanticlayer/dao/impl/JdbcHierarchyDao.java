package com.lextr.semanticlayer.dao.impl;

import com.lextr.semanticlayer.dao.HierarchyDao;
import com.lextr.semanticlayer.exception.SemanticLayerException;
import com.lextr.semanticlayer.model.LogicalHierarchyLevelRecord;
import com.lextr.semanticlayer.model.LogicalHierarchyRecord;
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
public class JdbcHierarchyDao implements HierarchyDao {

    private static final String FIND_ALL = "logical_hierarchy.find_all";
    private static final String INSERT = "logical_hierarchy.insert";
    private static final String FIND_LEVELS = "logical_hierarchy.find_levels";
    private static final String INSERT_LEVEL = "logical_hierarchy.insert_level";

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final SQLQueryLoaderUtil sqlQueryLoaderUtil;

    @Autowired
    public JdbcHierarchyDao(
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

    private final RowMapper<LogicalHierarchyRecord> hierarchyRowMapper = (rs, rowNum) -> new LogicalHierarchyRecord(
            rs.getLong("id"),
            rs.getString("hierarchy_cd"),
            rs.getString("hierarchy_nm"),
            rs.getString("tenant_cd"),
            rs.getString("hierarchy_status_cd"),
            rs.getString("created_by"),
            getOffsetDateTime(rs, "created_ts"),
            rs.getString("updated_by"),
            getOffsetDateTime(rs, "updated_ts")
    );

    private final RowMapper<LogicalHierarchyLevelRecord> levelRowMapper = (rs, rowNum) -> new LogicalHierarchyLevelRecord(
            rs.getLong("id"),
            rs.getString("hierarchy_cd"),
            rs.getInt("level_nbr"),
            rs.getString("level_label"),
            rs.getString("attribute_cd"),
            rs.getString("code_cd"),
            rs.getString("object_ref")
    );

    @Override
    public List<LogicalHierarchyRecord> findAll(String tenantCd) {
        checkJdbcTemplate();
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("tenant_cd", tenantCd);
        return jdbcTemplate.query(sqlQueryLoaderUtil.getQuery(FIND_ALL), params, hierarchyRowMapper);
    }

    @Override
    public LogicalHierarchyRecord insert(String hierarchyCd, String hierarchyNm, String tenantCd,
                                          String hierarchyStatusCd, String createdBy) {
        checkJdbcTemplate();
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("hierarchy_cd", hierarchyCd)
                .addValue("hierarchy_nm", hierarchyNm)
                .addValue("tenant_cd", tenantCd)
                .addValue("hierarchy_status_cd", hierarchyStatusCd)
                .addValue("created_by", createdBy);
        return jdbcTemplate.query(sqlQueryLoaderUtil.getQuery(INSERT), params, hierarchyRowMapper)
                .stream().findFirst()
                .orElseThrow(() -> new SemanticLayerException("Failed to insert hierarchy: " + hierarchyCd));
    }

    @Override
    public List<LogicalHierarchyLevelRecord> findLevels(String hierarchyCd) {
        checkJdbcTemplate();
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("hierarchy_cd", hierarchyCd);
        return jdbcTemplate.query(sqlQueryLoaderUtil.getQuery(FIND_LEVELS), params, levelRowMapper);
    }

    @Override
    public LogicalHierarchyLevelRecord insertLevel(String hierarchyCd, Integer levelNbr, String levelLabel,
                                                    String attributeCd, String codeCd, String objectRef) {
        checkJdbcTemplate();
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("hierarchy_cd", hierarchyCd)
                .addValue("level_nbr", levelNbr)
                .addValue("level_label", levelLabel)
                .addValue("attribute_cd", attributeCd)
                .addValue("code_cd", codeCd)
                .addValue("object_ref", objectRef);
        return jdbcTemplate.query(sqlQueryLoaderUtil.getQuery(INSERT_LEVEL), params, levelRowMapper)
                .stream().findFirst()
                .orElseThrow(() -> new SemanticLayerException("Failed to insert hierarchy level"));
    }

    private static OffsetDateTime getOffsetDateTime(java.sql.ResultSet rs, String col) throws java.sql.SQLException {
        Timestamp ts = rs.getTimestamp(col);
        return ts == null ? null : ts.toInstant().atOffset(ZoneOffset.UTC);
    }
}
