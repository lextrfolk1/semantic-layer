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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger logger = LoggerFactory.getLogger(JdbcDqRuleDao.class);

    private final ObjectProvider<NamedParameterJdbcTemplate> jdbcTemplateProvider;
    private final SQLQueryLoaderUtil sqlQueryLoaderUtil;

    public JdbcDqRuleDao(ObjectProvider<NamedParameterJdbcTemplate> jdbcTemplateProvider,
                         SQLQueryLoaderUtil sqlQueryLoaderUtil) {
        this.jdbcTemplateProvider = jdbcTemplateProvider;
        this.sqlQueryLoaderUtil = sqlQueryLoaderUtil;
    }

    @Override
    public List<DqRuleCatalogRecord> findRules(String clientId, String ruleDimensionCode, String lifecycleStatusCode) {
        logger.debug("Executing DQ rule catalog lookup. clientId={}, ruleDimensionCode={}, lifecycleStatusCode={}",
                clientId, ruleDimensionCode, lifecycleStatusCode);
        List<DqRuleCatalogRecord> records = jdbcTemplate().query(
                sqlQueryLoaderUtil.getQuery("dq_rule_catalog.find_all"),
                new MapSqlParameterSource()
                        .addValue("client_id", clientId)
                        .addValue("rule_dimension_cd", ruleDimensionCode)
                        .addValue("lifecycle_status_cd", lifecycleStatusCode),
                JdbcDqRuleDao::mapCatalogRow
        );
        logger.debug("DQ rule catalog lookup completed. clientId={}, resultCount={}", clientId, records.size());
        return records;
    }

    @Override
    public Optional<DqRuleCatalogRecord> findRule(String clientId, String ruleCode) {
        logger.debug("Executing DQ rule lookup. clientId={}, ruleCode={}", clientId, ruleCode);
        Optional<DqRuleCatalogRecord> record = jdbcTemplate().query(
                sqlQueryLoaderUtil.getQuery("dq_rule_catalog.find_by_code"),
                new MapSqlParameterSource()
                        .addValue("client_id", clientId)
                        .addValue("rule_cd", ruleCode),
                JdbcDqRuleDao::mapCatalogRow
        ).stream().findFirst();
        logger.debug("DQ rule lookup completed. clientId={}, ruleCode={}, found={}", clientId, ruleCode, record.isPresent());
        return record;
    }

    @Override
    public List<DqRuleAttributeRecord> findRuleAttributes(String clientId, String ruleCode) {
        logger.debug("Executing DQ rule attribute lookup. clientId={}, ruleCode={}", clientId, ruleCode);
        List<DqRuleAttributeRecord> records = jdbcTemplate().query(
                sqlQueryLoaderUtil.getQuery("dq_rule_attribute.find_by_rule_code"),
                new MapSqlParameterSource()
                        .addValue("client_id", clientId)
                        .addValue("rule_cd", ruleCode),
                JdbcDqRuleDao::mapAttributeRow
        );
        logger.debug("DQ rule attribute lookup completed. clientId={}, ruleCode={}, resultCount={}",
                clientId, ruleCode, records.size());
        return records;
    }

    @Override
    public List<DqRuleResultRecord> findResultsByLogicalAttribute(String clientId, String logicalAttributeCode) {
        logger.debug("Executing DQ result lookup. clientId={}, logicalAttributeCode={}", clientId, logicalAttributeCode);
        List<DqRuleResultRecord> records = jdbcTemplate().query(
                sqlQueryLoaderUtil.getQuery("dq_result.find_by_attribute"),
                new MapSqlParameterSource()
                        .addValue("client_id", clientId)
                        .addValue("logical_attribute_cd", logicalAttributeCode),
                JdbcDqRuleDao::mapResultRow
        );
        logger.debug("DQ result lookup completed. clientId={}, logicalAttributeCode={}, resultCount={}",
                clientId, logicalAttributeCode, records.size());
        return records;
    }

    @Override
    public DqRuleRequestWorkflowTaskRecord insertWorkflowTask(DqRuleRequestWorkflowTaskWriteRequest request) {
        logger.debug("Executing DQ workflow task insert. clientId={}, ruleCode={}, workflowTypeCode={}",
                request.client_id(), request.rule_cd(), request.workflow_type_cd());
        DqRuleRequestWorkflowTaskRecord record = jdbcTemplate().query(
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
        logger.debug("DQ workflow task insert completed. clientId={}, ruleCode={}, taskId={}",
                request.client_id(), request.rule_cd(), record.id());
        return record;
    }

    @Override
    public Optional<DqRuleRequestWorkflowTaskRecord> findRequest(String clientId, java.util.UUID workflowTaskId) {
        logger.debug("Executing DQ workflow task lookup. clientId={}, workflowTaskId={}", clientId, workflowTaskId);
        Optional<DqRuleRequestWorkflowTaskRecord> record = jdbcTemplate().query(
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
        logger.debug("DQ workflow task lookup completed. clientId={}, workflowTaskId={}, found={}",
                clientId, workflowTaskId, record.isPresent());
        return record;
    }

    @Override
    public DqRuleResultRecord insertResult(DqRuleResultWriteRequest request) {
        logger.debug("Executing DQ result insert. clientId={}, ruleCode={}, logicalAttributeCode={}, resultStatusCode={}",
                request.client_id(), request.rule_cd(), request.logical_attribute_cd(), request.result_status_cd());
        DqRuleResultRecord record = jdbcTemplate().query(
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
        logger.debug("DQ result insert completed. clientId={}, ruleCode={}, resultId={}",
                request.client_id(), request.rule_cd(), record.id());
        return record;
    }

    @Override
    public DqRuleMetadataChangeHistoryRecord insertMetadataChangeHistory(DqRuleMetadataChangeHistoryWriteRequest request) {
        logger.debug("Executing DQ metadata change insert. clientId={}, entityRef={}, changeTypeCode={}",
                request.client_id(), request.entity_ref(), request.change_type_cd());
        DqRuleMetadataChangeHistoryRecord record = jdbcTemplate().query(
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
        logger.debug("DQ metadata change insert completed. clientId={}, entityRef={}, changeHistoryId={}",
                request.client_id(), request.entity_ref(), record.change_history_id());
        return record;
    }

    private NamedParameterJdbcTemplate jdbcTemplate() {
        NamedParameterJdbcTemplate jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
        if (jdbcTemplate == null) {
            logger.error("NamedParameterJdbcTemplate is not configured for DQ rule DAO.");
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
