package com.lextr.semanticlayer.dao.impl;

import com.lextr.semanticlayer.dao.AttributePairingRegistrationWriteDao;
import com.lextr.semanticlayer.exception.SemanticLayerException;
import com.lextr.semanticlayer.model.AttributePairingCatalogRecord;
import com.lextr.semanticlayer.model.AttributePairingCatalogWriteRequest;
import com.lextr.semanticlayer.model.FilterLookupMetadataChangeHistoryRecord;
import com.lextr.semanticlayer.model.FilterLookupMetadataChangeHistoryWriteRequest;
import com.lextr.semanticlayer.model.FilterLookupWorkflowTaskRecord;
import com.lextr.semanticlayer.model.FilterLookupWorkflowTaskWriteRequest;
import com.lextr.semanticlayer.util.SQLQueryLoaderUtil;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Repository
public class JdbcAttributePairingRegistrationWriteDao implements AttributePairingRegistrationWriteDao {

    static final String INSERT_PAIRING = "attribute_pairing_registration.insert_pairing";
    static final String INSERT_WORKFLOW_TASK = "attribute_pairing_registration.insert_workflow_task";
    static final String INSERT_METADATA_CHANGE_HISTORY = "attribute_pairing_registration.insert_metadata_change_history";

    private final ObjectProvider<NamedParameterJdbcTemplate> jdbcTemplateProvider;
    private final SQLQueryLoaderUtil sqlQueryLoaderUtil;

    public JdbcAttributePairingRegistrationWriteDao(ObjectProvider<NamedParameterJdbcTemplate> jdbcTemplateProvider,
                                                    SQLQueryLoaderUtil sqlQueryLoaderUtil) {
        this.jdbcTemplateProvider = jdbcTemplateProvider;
        this.sqlQueryLoaderUtil = sqlQueryLoaderUtil;
    }

    @Override
    public AttributePairingCatalogRecord insertPairing(AttributePairingCatalogWriteRequest request) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("pairing_cd", request.pairing_cd())
                .addValue("pairing_nm", request.pairing_nm())
                .addValue("schema_cd", request.schema_cd())
                .addValue("object_cd", request.object_cd())
                .addValue("display_attribute_cd", request.display_attribute_cd())
                .addValue("filter_attribute_cd", request.filter_attribute_cd())
                .addValue("pairing_type_cd", request.pairing_type_cd())
                .addValue("lookup_strategy_cd", request.lookup_strategy_cd())
                .addValue("lookup_inline_map_jsonb", request.lookup_inline_map_jsonb())
                .addValue("lookup_sql_template_txt", request.lookup_sql_template_txt())
                .addValue("lookup_cache_enabled_flg", request.lookup_cache_enabled_flg())
                .addValue("lookup_cache_ttl_seconds_nbr", request.lookup_cache_ttl_seconds_nbr())
                .addValue("cardinality_cd", request.cardinality_cd())
                .addValue("is_bidirectional_flg", request.is_bidirectional_flg())
                .addValue("is_cross_engine_flg", request.is_cross_engine_flg())
                .addValue("filter_attribute_indexed_flg", request.filter_attribute_indexed_flg())
                .addValue("filter_attribute_index_type_cd", request.filter_attribute_index_type_cd())
                .addValue("performance_gain_pct_est_nbr", request.performance_gain_pct_est_nbr())
                .addValue("ai_context_txt", request.ai_context_txt())
                .addValue("client_id", request.client_id())
                .addValue("lifecycle_status_cd", request.lifecycle_status_cd())
                .addValue("governance_review_status_cd", request.governance_review_status_cd())
                .addValue("version_nbr", request.version_nbr())
                .addValue("created_ts", request.created_ts())
                .addValue("created_by", request.created_by())
                .addValue("updated_ts", request.updated_ts())
                .addValue("updated_by", request.updated_by());
        return jdbcTemplate().query(
                sqlQueryLoaderUtil.getQuery(INSERT_PAIRING),
                parameters,
                JdbcAttributePairingRegistrationWriteDao::mapPairingRow
        ).stream().findFirst().orElseThrow(() -> new SemanticLayerException("Insert attribute pairing returned no rows"));
    }

    @Override
    public FilterLookupWorkflowTaskRecord insertWorkflowTask(FilterLookupWorkflowTaskWriteRequest request) {
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
        return jdbcTemplate().query(
                sqlQueryLoaderUtil.getQuery(INSERT_WORKFLOW_TASK),
                parameters,
                JdbcAttributePairingRegistrationWriteDao::mapWorkflowTaskRow
        ).stream().findFirst().orElseThrow(() -> new SemanticLayerException("Insert attribute pairing workflow task returned no rows"));
    }

    @Override
    public FilterLookupMetadataChangeHistoryRecord insertMetadataChangeHistory(
            FilterLookupMetadataChangeHistoryWriteRequest request
    ) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("entity_type_cd", request.entity_type_cd())
                .addValue("entity_ref", request.entity_ref())
                .addValue("change_type_cd", request.change_type_cd())
                .addValue("changed_by", request.changed_by())
                .addValue("changed_ts", request.changed_ts())
                .addValue("old_value_json", request.old_value_json())
                .addValue("new_value_json", request.new_value_json())
                .addValue("change_reason_txt", request.change_reason_txt());
        return jdbcTemplate().query(
                sqlQueryLoaderUtil.getQuery(INSERT_METADATA_CHANGE_HISTORY),
                parameters,
                JdbcAttributePairingRegistrationWriteDao::mapMetadataChangeRow
        ).stream().findFirst().orElseThrow(() -> new SemanticLayerException("Insert attribute pairing metadata change history returned no rows"));
    }

    private NamedParameterJdbcTemplate jdbcTemplate() {
        NamedParameterJdbcTemplate jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
        if (jdbcTemplate == null) {
            throw new SemanticLayerException("NamedParameterJdbcTemplate is not configured");
        }
        return jdbcTemplate;
    }

    private static AttributePairingCatalogRecord mapPairingRow(ResultSet resultSet, int rowNum) throws SQLException {
        return new AttributePairingCatalogRecord(
                getLong(resultSet, "id"),
                resultSet.getString("pairing_cd"),
                resultSet.getString("pairing_nm"),
                resultSet.getString("schema_cd"),
                resultSet.getString("object_cd"),
                resultSet.getString("display_attribute_cd"),
                resultSet.getString("filter_attribute_cd"),
                resultSet.getString("pairing_type_cd"),
                resultSet.getString("lookup_strategy_cd"),
                resultSet.getString("lookup_inline_map_jsonb"),
                resultSet.getString("lookup_sql_template_txt"),
                resultSet.getBoolean("lookup_cache_enabled_flg"),
                getInteger(resultSet, "lookup_cache_ttl_seconds_nbr"),
                resultSet.getString("cardinality_cd"),
                resultSet.getBoolean("is_bidirectional_flg"),
                resultSet.getBoolean("is_cross_engine_flg"),
                resultSet.getBoolean("filter_attribute_indexed_flg"),
                resultSet.getString("filter_attribute_index_type_cd"),
                getInteger(resultSet, "performance_gain_pct_est_nbr"),
                resultSet.getString("ai_context_txt"),
                resultSet.getString("client_id"),
                resultSet.getString("lifecycle_status_cd"),
                resultSet.getString("governance_review_status_cd"),
                getInteger(resultSet, "version_nbr"),
                getOffsetDateTime(resultSet, "created_ts"),
                resultSet.getString("created_by"),
                getOffsetDateTime(resultSet, "updated_ts"),
                resultSet.getString("updated_by")
        );
    }

    private static FilterLookupWorkflowTaskRecord mapWorkflowTaskRow(ResultSet resultSet, int rowNum) throws SQLException {
        return new FilterLookupWorkflowTaskRecord(
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
        );
    }

    private static FilterLookupMetadataChangeHistoryRecord mapMetadataChangeRow(ResultSet resultSet, int rowNum) throws SQLException {
        return new FilterLookupMetadataChangeHistoryRecord(
                getLong(resultSet, "id"),
                resultSet.getString("entity_type_cd"),
                resultSet.getString("entity_ref"),
                resultSet.getString("change_type_cd"),
                resultSet.getString("changed_by"),
                getOffsetDateTime(resultSet, "changed_ts"),
                resultSet.getString("old_value_json"),
                resultSet.getString("new_value_json"),
                resultSet.getString("change_reason_txt")
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
