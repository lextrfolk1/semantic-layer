package com.lextr.semanticlayer.dao.impl;

import com.lextr.semanticlayer.dao.DqRuleDao;
import com.lextr.semanticlayer.exception.SemanticLayerException;
import com.lextr.semanticlayer.model.DqRuleAttributeRecord;
import com.lextr.semanticlayer.model.DqRuleCatalogRecord;
import com.lextr.semanticlayer.model.DqRuleMetadataChangeHistoryRecord;
import com.lextr.semanticlayer.model.DqRuleMetadataChangeHistoryWriteRequest;
import com.lextr.semanticlayer.model.DqRuleRequestWorkflowTaskRecord;
import com.lextr.semanticlayer.model.DqRuleRequestWorkflowTaskWriteRequest;
import com.lextr.semanticlayer.model.DqRuleResultRecord;
import com.lextr.semanticlayer.model.DqRuleResultWriteRequest;
import com.lextr.semanticlayer.util.SQLQueryLoaderUtil;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

@Repository
public class JdbcDqRuleDao implements DqRuleDao {

    private final ObjectProvider<NamedParameterJdbcTemplate> jdbcTemplateProvider;
    private final SQLQueryLoaderUtil sqlQueryLoaderUtil;

    public JdbcDqRuleDao(ObjectProvider<NamedParameterJdbcTemplate> jdbcTemplateProvider,
                         SQLQueryLoaderUtil sqlQueryLoaderUtil) {
        this.jdbcTemplateProvider = jdbcTemplateProvider;
        this.sqlQueryLoaderUtil = sqlQueryLoaderUtil;
    }

    @Override
    public List<DqRuleCatalogRecord> findRules(String clientId, String ruleDimensionCode, String lifecycleStatusCode) {
        return jdbcTemplate().query(
                sqlQueryLoaderUtil.getQuery("dq_rule_catalog.find_all"),
                new MapSqlParameterSource()
                        .addValue("client_id", clientId)
                        .addValue("rule_dimension_cd", ruleDimensionCode)
                        .addValue("lifecycle_status_cd", lifecycleStatusCode),
                JdbcDqRuleDao::mapCatalogRow
        );
    }

    @Override
    public Optional<DqRuleCatalogRecord> findRule(String clientId, String ruleCode) {
        return jdbcTemplate().query(
                sqlQueryLoaderUtil.getQuery("dq_rule_catalog.find_by_code"),
                new MapSqlParameterSource()
                        .addValue("client_id", clientId)
                        .addValue("rule_cd", ruleCode),
                JdbcDqRuleDao::mapCatalogRow
        ).stream().findFirst();
    }

    @Override
    public List<DqRuleAttributeRecord> findRuleAttributes(String clientId, String ruleCode) {
        return jdbcTemplate().query(
                sqlQueryLoaderUtil.getQuery("dq_rule_attribute.find_by_rule_code"),
                new MapSqlParameterSource()
                        .addValue("client_id", clientId)
                        .addValue("rule_cd", ruleCode),
                JdbcDqRuleDao::mapAttributeRow
        );
    }

    @Override
    public List<DqRuleResultRecord> findResultsByLogicalAttribute(String clientId, String logicalAttributeCode) {
        return jdbcTemplate().query(
                sqlQueryLoaderUtil.getQuery("dq_result.find_by_attribute"),
                new MapSqlParameterSource()
                        .addValue("client_id", clientId)
                        .addValue("logical_attribute_cd", logicalAttributeCode),
                JdbcDqRuleDao::mapResultRow
        );
    }

    @Override
    public DqRuleRequestWorkflowTaskRecord insertWorkflowTask(DqRuleRequestWorkflowTaskWriteRequest request) {
        return jdbcTemplate().query(
                sqlQueryLoaderUtil.getQuery("dq_rule_request.insert_workflow_task"),
                new MapSqlParameterSource()
                        .addValue("workflow_type_cd", request.workflow_type_cd())
                        .addValue("entity_type_cd", request.entity_type_cd())
                        .addValue("rule_cd", request.rule_cd())
                        .addValue("task_status_cd", request.task_status_cd())
                        .addValue("created_by", request.submitted_by())
                        .addValue("created_ts", request.submitted_ts())
                        .addValue("assigned_to", request.assigned_to())
                        .addValue("due_dt", request.due_dt())
                        .addValue("description_txt", request.description_txt())
                        .addValue("client_id", request.client_id()),
                (resultSet, rowNum) -> new DqRuleRequestWorkflowTaskRecord(
                        resultSet.getObject("id", Long.class),
                        resultSet.getString("task_type_cd"),
                        resultSet.getString("entity_type_cd"),
                        resultSet.getString("rule_cd"),
                        resultSet.getString("task_status_cd"),
                        resultSet.getString("submitted_by"),
                        resultSet.getObject("submitted_ts", java.time.OffsetDateTime.class),
                        resultSet.getString("assigned_to"),
                        resultSet.getObject("due_dt", java.time.LocalDate.class),
                        resultSet.getString("description_txt"),
                        resultSet.getString("client_id"),
                        null,
                        null,
                        null
                )
        ).stream().findFirst().orElseThrow(() -> new SemanticLayerException("Insert DQ workflow task returned no rows"));
    }

    @Override
    public Optional<DqRuleRequestWorkflowTaskRecord> findRequest(String clientId, java.util.UUID workflowTaskId) {
        return jdbcTemplate().query(
                sqlQueryLoaderUtil.getQuery("dq_rule_request.find_by_id"),
                new MapSqlParameterSource()
                        .addValue("client_id", clientId)
                        .addValue("workflow_task_id", workflowTaskId),
                (resultSet, rowNum) -> new DqRuleRequestWorkflowTaskRecord(
                        resultSet.getObject("id", Long.class),
                        resultSet.getString("task_type_cd"),
                        resultSet.getString("entity_type_cd"),
                        resultSet.getString("rule_cd"),
                        resultSet.getString("task_status_cd"),
                        resultSet.getString("submitted_by"),
                        resultSet.getObject("submitted_ts", java.time.OffsetDateTime.class),
                        resultSet.getString("assigned_to"),
                        resultSet.getObject("due_dt", java.time.LocalDate.class),
                        resultSet.getString("description_txt"),
                        resultSet.getString("client_id"),
                        resultSet.getString("approved_by"),
                        resultSet.getObject("approved_ts", java.time.OffsetDateTime.class),
                        resultSet.getString("approval_note_txt")
                )
        ).stream().findFirst();
    }

    @Override
    public DqRuleResultRecord insertResult(DqRuleResultWriteRequest request) {
        return jdbcTemplate().query(
                sqlQueryLoaderUtil.getQuery("dq_result.insert_result"),
                new MapSqlParameterSource()
                        .addValue("rule_cd", request.rule_cd())
                        .addValue("logical_attribute_cd", request.logical_attribute_cd())
                        .addValue("client_id", request.client_id())
                        .addValue("observed_value_txt", request.observed_value_txt())
                        .addValue("expected_value_txt", request.expected_value_txt())
                        .addValue("result_status_cd", request.result_status_cd())
                        .addValue("result_reason_txt", request.result_reason_txt())
                        .addValue("observed_ts", request.observed_ts())
                        .addValue("created_ts", request.created_ts())
                        .addValue("created_by", request.created_by())
                        .addValue("updated_ts", request.updated_ts())
                        .addValue("updated_by", request.updated_by()),
                JdbcDqRuleDao::mapResultRow
        ).stream().findFirst().orElseThrow(() -> new SemanticLayerException("Insert DQ result returned no rows"));
    }

    @Override
    public DqRuleMetadataChangeHistoryRecord insertMetadataChangeHistory(DqRuleMetadataChangeHistoryWriteRequest request) {
        return jdbcTemplate().query(
                sqlQueryLoaderUtil.getQuery("dq_rule_request.insert_metadata_change_history"),
                new MapSqlParameterSource()
                        .addValue("entity_type_cd", request.entity_type_cd())
                        .addValue("entity_ref", request.entity_ref())
                        .addValue("change_type_cd", request.change_type_cd())
                        .addValue("change_summary_txt", request.change_summary_txt())
                        .addValue("created_by", request.created_by())
                        .addValue("created_ts", request.created_ts())
                        .addValue("client_id", request.client_id()),
                (resultSet, rowNum) -> new DqRuleMetadataChangeHistoryRecord(
                        resultSet.getObject("change_history_id", java.util.UUID.class),
                        resultSet.getString("client_id"),
                        resultSet.getString("entity_type_cd"),
                        resultSet.getString("entity_ref"),
                        resultSet.getString("change_type_cd"),
                        resultSet.getString("change_summary_txt"),
                        resultSet.getObject("created_ts", java.time.OffsetDateTime.class),
                        resultSet.getString("created_by")
                )
        ).stream().findFirst().orElseThrow(() -> new SemanticLayerException("Insert DQ metadata change history returned no rows"));
    }

    private NamedParameterJdbcTemplate jdbcTemplate() {
        NamedParameterJdbcTemplate jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
        if (jdbcTemplate == null) {
            throw new SemanticLayerException("NamedParameterJdbcTemplate is not configured");
        }
        return jdbcTemplate;
    }

    private static DqRuleCatalogRecord mapCatalogRow(ResultSet resultSet, int rowNum) throws SQLException {
        return new DqRuleCatalogRecord(
                resultSet.getObject("id", Long.class),
                resultSet.getString("rule_cd"),
                resultSet.getString("rule_nm"),
                resultSet.getString("rule_dimension_cd"),
                resultSet.getString("logical_attribute_cd"),
                resultSet.getString("rule_scope_cd"),
                resultSet.getString("rule_expression_txt"),
                resultSet.getString("severity_cd"),
                resultSet.getString("lifecycle_status_cd"),
                resultSet.getString("client_id"),
                resultSet.getObject("created_ts", java.time.OffsetDateTime.class),
                resultSet.getString("created_by"),
                resultSet.getObject("updated_ts", java.time.OffsetDateTime.class),
                resultSet.getString("updated_by")
        );
    }

    private static DqRuleAttributeRecord mapAttributeRow(ResultSet resultSet, int rowNum) throws SQLException {
        return new DqRuleAttributeRecord(
                resultSet.getObject("id", Long.class),
                resultSet.getString("rule_cd"),
                resultSet.getString("attribute_cd"),
                resultSet.getString("attribute_role_cd"),
                resultSet.getString("client_id"),
                resultSet.getObject("created_ts", java.time.OffsetDateTime.class),
                resultSet.getString("created_by"),
                resultSet.getObject("updated_ts", java.time.OffsetDateTime.class),
                resultSet.getString("updated_by")
        );
    }

    private static DqRuleResultRecord mapResultRow(ResultSet resultSet, int rowNum) throws SQLException {
        return new DqRuleResultRecord(
                resultSet.getObject("id", Long.class),
                resultSet.getString("rule_cd"),
                resultSet.getString("logical_attribute_cd"),
                resultSet.getString("client_id"),
                resultSet.getString("observed_value_txt"),
                resultSet.getString("expected_value_txt"),
                resultSet.getString("result_status_cd"),
                resultSet.getString("result_reason_txt"),
                resultSet.getObject("observed_ts", java.time.OffsetDateTime.class),
                resultSet.getObject("created_ts", java.time.OffsetDateTime.class),
                resultSet.getString("created_by"),
                resultSet.getObject("updated_ts", java.time.OffsetDateTime.class),
                resultSet.getString("updated_by")
        );
    }
}
