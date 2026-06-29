package com.lextr.semanticlayer.dao.impl;

import com.lextr.semanticlayer.dao.FilterLookupRegistrationWriteDao;
import com.lextr.semanticlayer.exception.SemanticLayerException;
import com.lextr.semanticlayer.model.FilterLookupBindingRecord;
import com.lextr.semanticlayer.model.FilterLookupBindingWriteRequest;
import com.lextr.semanticlayer.model.FilterLookupCertificationWriteRequest;
import com.lextr.semanticlayer.model.FilterLookupMetadataChangeHistoryRecord;
import com.lextr.semanticlayer.model.FilterLookupMetadataChangeHistoryWriteRequest;
import com.lextr.semanticlayer.model.FilterLookupWorkflowTaskRecord;
import com.lextr.semanticlayer.model.FilterLookupWorkflowTaskWriteRequest;
import com.lextr.semanticlayer.model.SemanticFilterLookupRecord;
import com.lextr.semanticlayer.model.SemanticFilterLookupWriteRequest;
import com.lextr.semanticlayer.util.SQLQueryLoaderUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Repository
public class JdbcFilterLookupRegistrationWriteDao implements FilterLookupRegistrationWriteDao {

    private static final Logger logger = LoggerFactory.getLogger(JdbcFilterLookupRegistrationWriteDao.class);

    static final String INSERT_LOOKUP = "filter_lookup_registration.insert_lookup";
    static final String CERTIFY_LOOKUP = "filter_lookup_registration.certify_lookup";
    static final String INSERT_BINDING = "filter_lookup_registration.insert_binding";
    static final String INSERT_WORKFLOW_TASK = "filter_lookup_registration.insert_workflow_task";
    static final String INSERT_METADATA_CHANGE_HISTORY = "filter_lookup_registration.insert_metadata_change_history";

    private final ObjectProvider<NamedParameterJdbcTemplate> jdbcTemplateProvider;
    private final SQLQueryLoaderUtil sqlQueryLoaderUtil;

    public JdbcFilterLookupRegistrationWriteDao(ObjectProvider<NamedParameterJdbcTemplate> jdbcTemplateProvider,
                                                SQLQueryLoaderUtil sqlQueryLoaderUtil) {
        this.jdbcTemplateProvider = jdbcTemplateProvider;
        this.sqlQueryLoaderUtil = sqlQueryLoaderUtil;
    }

    @Override
    public SemanticFilterLookupRecord insertLookup(SemanticFilterLookupWriteRequest request) {
        logger.debug("Executing filter lookup insert. clientId={}, lookupCode={}, constructionTypeCode={}",
                request.client_id(), request.lookup_cd(), request.construction_type_cd());
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("lookup_cd", request.lookup_cd())
                .addValue("construction_type_cd", request.construction_type_cd())
                .addValue("manual_subtype_cd", request.manual_subtype_cd())
                .addValue("filter_obj", request.filter_obj())
                .addValue("filter_condition_txt", request.filter_condition_txt())
                .addValue("filter_attr_cd", request.filter_attr_cd())
                .addValue("validation_obj", request.validation_obj())
                .addValue("validation_attr_cd", request.validation_attr_cd())
                .addValue("suggested_target_attr_cd", request.suggested_target_attr_cd())
                .addValue("execution_strategy_cd", request.execution_strategy_cd())
                .addValue("max_input_set_size", request.max_input_set_size())
                .addValue("max_output_rows", request.max_output_rows())
                .addValue("cache_ttl_min", request.cache_ttl_min())
                .addValue("review_period_days_override", request.review_period_days_override())
                .addValue("rules_eligible_flg", request.rules_eligible_flg())
                .addValue("qs_eligible_flg", request.qs_eligible_flg())
                .addValue("ai_eligible_flg", request.ai_eligible_flg())
                .addValue("replicate_to_ch_flg", request.replicate_to_ch_flg())
                .addValue("description_txt", request.description_txt())
                .addValue("client_id", request.client_id())
                .addValue("governance_status_cd", request.governance_status_cd())
                .addValue("health_status_cd", request.health_status_cd())
                .addValue("next_review_due_dt", request.next_review_due_dt())
                .addValue("lifecycle_status_cd", request.lifecycle_status_cd())
                .addValue("created_ts", request.created_ts())
                .addValue("created_by", request.created_by())
                .addValue("updated_ts", request.updated_ts())
                .addValue("updated_by", request.updated_by());
        SemanticFilterLookupRecord record = jdbcTemplate().query(
                sqlQueryLoaderUtil.getQuery(INSERT_LOOKUP),
                parameters,
                JdbcFilterLookupRegistrationWriteDao::mapFilterLookupRow
        ).stream().findFirst().orElseThrow(() -> new SemanticLayerException("Insert filter lookup returned no rows"));
        logger.debug("Filter lookup insert completed. clientId={}, lookupCode={}, id={}",
                request.client_id(), record.lookup_cd(), record.id());
        return record;
    }

    @Override
    public SemanticFilterLookupRecord certifyLookup(FilterLookupCertificationWriteRequest request) {
        logger.debug("Executing filter lookup certify update. clientId={}, lookupCode={}, healthStatusCode={}",
                request.client_id(), request.lookup_cd(), request.health_status_cd());
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("client_id", request.client_id())
                .addValue("lookup_cd", request.lookup_cd())
                .addValue("health_status_cd", request.health_status_cd())
                .addValue("last_certified_ts", request.last_certified_ts())
                .addValue("last_certified_by", request.last_certified_by())
                .addValue("next_review_due_dt", request.next_review_due_dt())
                .addValue("updated_ts", request.updated_ts())
                .addValue("updated_by", request.updated_by());
        SemanticFilterLookupRecord record = jdbcTemplate().query(
                sqlQueryLoaderUtil.getQuery(CERTIFY_LOOKUP),
                parameters,
                JdbcFilterLookupRegistrationWriteDao::mapFilterLookupRow
        ).stream().findFirst().orElseThrow(() -> new SemanticLayerException("Certify filter lookup returned no rows"));
        logger.debug("Filter lookup certify update completed. clientId={}, lookupCode={}, nextReviewDueDate={}",
                request.client_id(), record.lookup_cd(), record.next_review_due_dt());
        return record;
    }

    @Override
    public FilterLookupBindingRecord insertBinding(FilterLookupBindingWriteRequest request) {
        logger.debug("Executing filter lookup binding insert. lookupCode={}, boundObject={}, bindingContextCode={}",
                request.lookup_cd(), request.bound_obj(), request.binding_context_cd());
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("lookup_cd", request.lookup_cd())
                .addValue("bound_obj", request.bound_obj())
                .addValue("bound_attr_cd", request.bound_attr_cd())
                .addValue("binding_context_cd", request.binding_context_cd())
                .addValue("binding_ref", request.binding_ref())
                .addValue("bound_by", request.bound_by())
                .addValue("bound_ts", request.bound_ts())
                .addValue("is_active_flg", request.is_active_flg());
        FilterLookupBindingRecord record = jdbcTemplate().query(
                sqlQueryLoaderUtil.getQuery(INSERT_BINDING),
                parameters,
                JdbcFilterLookupRegistrationWriteDao::mapFilterLookupBindingRow
        ).stream().findFirst().orElseThrow(() -> new SemanticLayerException("Insert filter lookup binding returned no rows"));
        logger.debug("Filter lookup binding insert completed. lookupCode={}, bindingId={}", record.lookup_cd(), record.id());
        return record;
    }

    private static FilterLookupBindingRecord mapFilterLookupBindingRow(ResultSet resultSet, int rowNum) throws SQLException {
        return new FilterLookupBindingRecord(
                getLong(resultSet, "id"),
                resultSet.getString("lookup_cd"),
                resultSet.getString("bound_obj"),
                resultSet.getString("bound_attr_cd"),
                resultSet.getString("binding_context_cd"),
                resultSet.getString("binding_ref"),
                resultSet.getString("bound_by"),
                getOffsetDateTime(resultSet, "bound_ts"),
                resultSet.getBoolean("is_active_flg")
        );
    }

    @Override
    public FilterLookupWorkflowTaskRecord insertWorkflowTask(FilterLookupWorkflowTaskWriteRequest request) {
        logger.debug("Executing filter lookup workflow task insert. entityRef={}, clientId={}, taskTypeCode={}",
                request.entity_ref(), request.client_id(), request.task_type_cd());
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("task_type_cd", request.task_type_cd())
                .addValue("entity_type_cd", request.entity_type_cd())
                .addValue("entity_ref", request.entity_ref())
                .addValue("task_status_cd", request.task_status_cd())
                .addValue("submitted_by", request.submitted_by())
                .addValue("submitted_ts", request.submitted_ts())
                .addValue("assigned_to", request.assigned_to())
                .addValue("due_dt", request.due_dt())
                .addValue("description_txt", request.description_txt())
                .addValue("client_id", request.client_id())
                .addValue("approved_by", request.approved_by())
                .addValue("approved_ts", request.approved_ts())
                .addValue("approval_note_txt", request.approval_note_txt());
        FilterLookupWorkflowTaskRecord record = jdbcTemplate().query(
                sqlQueryLoaderUtil.getQuery(INSERT_WORKFLOW_TASK),
                parameters,
                (resultSet, rowNum) -> new FilterLookupWorkflowTaskRecord(
                        getLong(resultSet, "id"),
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
                )
        ).stream().findFirst().orElseThrow(() -> new SemanticLayerException("Insert filter lookup workflow task returned no rows"));
        logger.debug("Filter lookup workflow task insert completed. entityRef={}, taskId={}", record.entity_ref(), record.id());
        return record;
    }

    @Override
    public FilterLookupMetadataChangeHistoryRecord insertMetadataChangeHistory(
            FilterLookupMetadataChangeHistoryWriteRequest request
    ) {
        logger.debug("Executing filter lookup metadata change insert. entityRef={}, changeTypeCode={}",
                request.entity_ref(), request.change_type_cd());
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("entity_type_cd", request.entity_type_cd())
                .addValue("entity_ref", request.entity_ref())
                .addValue("change_type_cd", request.change_type_cd())
                .addValue("changed_by", request.changed_by())
                .addValue("changed_ts", request.changed_ts())
                .addValue("old_value_json", request.old_value_json())
                .addValue("new_value_json", request.new_value_json())
                .addValue("change_reason_txt", request.change_reason_txt());
        FilterLookupMetadataChangeHistoryRecord record = jdbcTemplate().query(
                sqlQueryLoaderUtil.getQuery(INSERT_METADATA_CHANGE_HISTORY),
                parameters,
                (resultSet, rowNum) -> new FilterLookupMetadataChangeHistoryRecord(
                        getLong(resultSet, "id"),
                        resultSet.getString("entity_type_cd"),
                        resultSet.getString("entity_ref"),
                        resultSet.getString("change_type_cd"),
                        resultSet.getString("changed_by"),
                        getOffsetDateTime(resultSet, "changed_ts"),
                        resultSet.getString("old_value_json"),
                        resultSet.getString("new_value_json"),
                        resultSet.getString("change_reason_txt")
                )
        ).stream().findFirst().orElseThrow(() -> new SemanticLayerException("Insert filter lookup metadata change history returned no rows"));
        logger.debug("Filter lookup metadata change insert completed. entityRef={}, changeHistoryId={}",
                record.entity_ref(), record.id());
        return record;
    }

    private NamedParameterJdbcTemplate jdbcTemplate() {
        NamedParameterJdbcTemplate jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
        if (jdbcTemplate == null) {
            logger.error("NamedParameterJdbcTemplate is not configured for filter lookup registration DAO.");
            throw new SemanticLayerException("NamedParameterJdbcTemplate is not configured");
        }
        return jdbcTemplate;
    }

    private static SemanticFilterLookupRecord mapFilterLookupRow(ResultSet resultSet, int rowNum) throws SQLException {
        return new SemanticFilterLookupRecord(
                getLong(resultSet, "id"),
                resultSet.getString("lookup_cd"),
                resultSet.getString("construction_type_cd"),
                resultSet.getString("manual_subtype_cd"),
                resultSet.getString("filter_obj"),
                resultSet.getString("filter_condition_txt"),
                resultSet.getString("filter_attr_cd"),
                resultSet.getString("validation_obj"),
                resultSet.getString("validation_attr_cd"),
                resultSet.getString("suggested_target_attr_cd"),
                resultSet.getString("execution_strategy_cd"),
                getInteger(resultSet, "max_input_set_size"),
                getInteger(resultSet, "max_output_rows"),
                getInteger(resultSet, "cache_ttl_min"),
                getInteger(resultSet, "review_period_days_override"),
                resultSet.getBoolean("rules_eligible_flg"),
                resultSet.getBoolean("qs_eligible_flg"),
                resultSet.getBoolean("ai_eligible_flg"),
                resultSet.getBoolean("replicate_to_ch_flg"),
                resultSet.getString("description_txt"),
                resultSet.getString("client_id"),
                resultSet.getString("governance_status_cd"),
                resultSet.getString("health_status_cd"),
                getOffsetDateTime(resultSet, "last_certified_ts"),
                resultSet.getString("last_certified_by"),
                getLocalDate(resultSet, "next_review_due_dt"),
                resultSet.getString("lifecycle_status_cd"),
                getOffsetDateTime(resultSet, "created_ts"),
                resultSet.getString("created_by"),
                getOffsetDateTime(resultSet, "updated_ts"),
                resultSet.getString("updated_by")
        );
    }

    private static Long getLong(ResultSet resultSet, String columnLabel) throws SQLException {
        return resultSet.getObject(columnLabel, Long.class);
    }

    private static Integer getInteger(ResultSet resultSet, String columnLabel) throws SQLException {
        Object value = resultSet.getObject(columnLabel);
        return value == null ? null : ((Number) value).intValue();
    }

    private static LocalDate getLocalDate(ResultSet resultSet, String columnLabel) throws SQLException {
        return resultSet.getObject(columnLabel, LocalDate.class);
    }

    private static OffsetDateTime getOffsetDateTime(ResultSet resultSet, String columnLabel) throws SQLException {
        return resultSet.getObject(columnLabel, OffsetDateTime.class);
    }
}
