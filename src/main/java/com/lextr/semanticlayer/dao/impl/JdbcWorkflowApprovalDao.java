package com.lextr.semanticlayer.dao.impl;

import com.lextr.semanticlayer.dao.WorkflowApprovalDao;
import com.lextr.semanticlayer.exception.SemanticLayerException;
import com.lextr.semanticlayer.model.FilterLookupWorkflowTaskRecord;
import com.lextr.semanticlayer.util.SQLQueryLoaderUtil;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Repository
public class JdbcWorkflowApprovalDao implements WorkflowApprovalDao {

    private static final String FIND_TASK_BY_ID = "workflow_approval.find_task_by_id";
    private static final String APPROVE_TASK = "workflow_approval.approve_task";
    private static final String APPROVE_LOOKUP = "workflow_approval.approve_lookup";
    private static final String APPROVE_ATTRIBUTE_OVERRIDE = "workflow_approval.approve_attribute_override";
    private static final String APPROVE_FILTER_LOOKUP_VALUE = "workflow_approval.approve_filter_lookup_value";

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final SQLQueryLoaderUtil sqlQueryLoaderUtil;

    @Autowired
    public JdbcWorkflowApprovalDao(
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

    private final RowMapper<FilterLookupWorkflowTaskRecord> taskRowMapper = (resultSet, rowNum) -> new FilterLookupWorkflowTaskRecord(
            resultSet.getLong("id"),
            resultSet.getString("task_type_cd"),
            resultSet.getString("entity_type_cd"),
            resultSet.getString("entity_ref"),
            resultSet.getString("task_status_cd"),
            resultSet.getString("submitted_by"),
            getOffsetDateTime(resultSet, "submitted_ts"),
            resultSet.getString("assigned_to"),
            getLocalDate(resultSet, "due_dt"),
            resultSet.getString("description_txt"),
            resultSet.getString("client_id"),
            resultSet.getString("approved_by"),
            getOffsetDateTime(resultSet, "approved_ts"),
            resultSet.getString("approval_note_txt")
    );

    @Override
    public FilterLookupWorkflowTaskRecord findTaskById(String clientId, Long id) {
        checkJdbcTemplate();
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("client_id", clientId)
                .addValue("id", id);
        return jdbcTemplate.query(
                sqlQueryLoaderUtil.getQuery(FIND_TASK_BY_ID),
                params,
                taskRowMapper
        ).stream().findFirst().orElse(null);
    }

    @Override
    public FilterLookupWorkflowTaskRecord approveTask(String clientId, Long id, String approvedBy, OffsetDateTime approvedTs, String approvalNote) {
        checkJdbcTemplate();
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("client_id", clientId)
                .addValue("id", id)
                .addValue("task_status_cd", "APPROVED")
                .addValue("approved_by", approvedBy)
                .addValue("approved_ts", approvedTs)
                .addValue("approval_note_txt", approvalNote);
        return jdbcTemplate.query(
                sqlQueryLoaderUtil.getQuery(APPROVE_TASK),
                params,
                taskRowMapper
        ).stream().findFirst().orElseThrow(() -> new SemanticLayerException("Failed to approve task or task not pending: " + id));
    }

    @Override
    public void approveLookup(String clientId, String lookupCd, String governanceStatus, OffsetDateTime updatedTs, String updatedBy) {
        checkJdbcTemplate();
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("client_id", clientId)
                .addValue("lookup_cd", lookupCd)
                .addValue("governance_status_cd", governanceStatus)
                .addValue("updated_ts", updatedTs)
                .addValue("updated_by", updatedBy);
        jdbcTemplate.update(sqlQueryLoaderUtil.getQuery(APPROVE_LOOKUP), params);
    }

    @Override
    public void approveAttributeOverride(String clientId, Long id, String lifecycleStatus, OffsetDateTime updatedTs, String updatedBy) {
        checkJdbcTemplate();
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("client_id", clientId)
                .addValue("id", id)
                .addValue("lifecycle_status_cd", lifecycleStatus)
                .addValue("updated_ts", updatedTs)
                .addValue("updated_by", updatedBy);
        jdbcTemplate.update(sqlQueryLoaderUtil.getQuery(APPROVE_ATTRIBUTE_OVERRIDE), params);
    }

    @Override
    public void approveFilterLookupValue(String lookupCd, String valueCd, String lifecycleStatus, boolean validated, OffsetDateTime updatedTs) {
        checkJdbcTemplate();
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("lookup_cd", lookupCd)
                .addValue("value_cd", valueCd)
                .addValue("lifecycle_status_cd", lifecycleStatus)
                .addValue("validated_flg", validated)
                .addValue("updated_ts", updatedTs);
        jdbcTemplate.update(sqlQueryLoaderUtil.getQuery(APPROVE_FILTER_LOOKUP_VALUE), params);
    }

    private static OffsetDateTime getOffsetDateTime(java.sql.ResultSet resultSet, String columnName) throws java.sql.SQLException {
        Timestamp timestamp = resultSet.getTimestamp(columnName);
        return timestamp == null ? null : timestamp.toInstant().atOffset(ZoneOffset.UTC);
    }

    private static LocalDate getLocalDate(java.sql.ResultSet resultSet, String columnName) throws java.sql.SQLException {
        java.sql.Date date = resultSet.getDate(columnName);
        return date == null ? null : date.toLocalDate();
    }
}
