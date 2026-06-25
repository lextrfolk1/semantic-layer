package com.lextr.semanticlayer.dao.impl;

import com.lextr.semanticlayer.dao.ObjectRegistrationWriteDao;
import com.lextr.semanticlayer.exception.SemanticLayerException;
import com.lextr.semanticlayer.model.AttributeCatalogRecord;
import com.lextr.semanticlayer.model.AttributeCatalogWriteRequest;
import com.lextr.semanticlayer.model.MetadataChangeHistoryRecord;
import com.lextr.semanticlayer.model.MetadataChangeHistoryWriteRequest;
import com.lextr.semanticlayer.model.ObjectCatalogRecord;
import com.lextr.semanticlayer.model.ObjectCatalogWriteRequest;
import com.lextr.semanticlayer.model.WorkflowTaskRecord;
import com.lextr.semanticlayer.model.WorkflowTaskWriteRequest;
import com.lextr.semanticlayer.util.SQLQueryLoaderUtil;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.UUID;

@Repository
public class JdbcObjectRegistrationWriteDao implements ObjectRegistrationWriteDao {

    static final String INSERT_DRAFT_OBJECT = "object_registration.insert_draft_object";
    static final String INSERT_ATTRIBUTE = "object_registration.insert_attribute";
    static final String INSERT_WORKFLOW_TASK = "object_registration.insert_workflow_task";
    static final String INSERT_METADATA_CHANGE_HISTORY = "object_registration.insert_metadata_change_history";

    private final ObjectProvider<NamedParameterJdbcTemplate> jdbcTemplateProvider;
    private final SQLQueryLoaderUtil sqlQueryLoaderUtil;

    public JdbcObjectRegistrationWriteDao(ObjectProvider<NamedParameterJdbcTemplate> jdbcTemplateProvider,
                                          SQLQueryLoaderUtil sqlQueryLoaderUtil) {
        this.jdbcTemplateProvider = jdbcTemplateProvider;
        this.sqlQueryLoaderUtil = sqlQueryLoaderUtil;
    }

    @Override
    public ObjectCatalogRecord insertDraftObject(ObjectCatalogWriteRequest request) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("object_id", request.object_id())
                .addValue("client_id", request.client_id())
                .addValue("object_cd", request.object_cd())
                .addValue("object_nm", request.object_nm())
                .addValue("object_type_cd", request.object_type_cd())
                .addValue("schema_cd", request.schema_cd())
                .addValue("connection_id", request.connection_id())
                .addValue("created_ts", request.created_ts())
                .addValue("created_by", request.created_by())
                .addValue("updated_ts", request.updated_ts())
                .addValue("updated_by", request.updated_by());
        return jdbcTemplate().query(
                sqlQueryLoaderUtil.getQuery(INSERT_DRAFT_OBJECT),
                parameters,
                (resultSet, rowNum) -> new ObjectCatalogRecord(
                        getUuid(resultSet, "object_id"),
                        resultSet.getString("client_id"),
                        resultSet.getString("object_cd"),
                        resultSet.getString("object_nm"),
                        resultSet.getString("object_type_cd"),
                        resultSet.getString("schema_cd"),
                        getUuid(resultSet, "connection_id"),
                        resultSet.getString("lifecycle_status_cd"),
                        getOffsetDateTime(resultSet, "created_ts"),
                        resultSet.getString("created_by"),
                        getOffsetDateTime(resultSet, "updated_ts"),
                        resultSet.getString("updated_by")
                )
        ).stream().findFirst().orElseThrow(() -> new SemanticLayerException("Insert object returned no rows"));
    }

    @Override
    public AttributeCatalogRecord insertAttribute(AttributeCatalogWriteRequest request) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("attribute_id", request.attribute_id())
                .addValue("object_id", request.object_id())
                .addValue("client_id", request.client_id())
                .addValue("attribute_cd", request.attribute_cd())
                .addValue("attribute_nm", request.attribute_nm())
                .addValue("data_type_cd", request.data_type_cd())
                .addValue("taxonomy_cd", request.taxonomy_cd())
                .addValue("taxonomy_source_cd", request.taxonomy_source_cd())
                .addValue("taxonomy_jurisdiction_cd", request.taxonomy_jurisdiction_cd())
                .addValue("pk_flg", request.pk_flg())
                .addValue("fk_flg", request.fk_flg())
                .addValue("nullable_flg", request.nullable_flg())
                .addValue("created_ts", request.created_ts())
                .addValue("created_by", request.created_by())
                .addValue("updated_ts", request.updated_ts())
                .addValue("updated_by", request.updated_by());
        return jdbcTemplate().query(
                sqlQueryLoaderUtil.getQuery(INSERT_ATTRIBUTE),
                parameters,
                (resultSet, rowNum) -> new AttributeCatalogRecord(
                        getUuid(resultSet, "attribute_id"),
                        getUuid(resultSet, "object_id"),
                        resultSet.getString("client_id"),
                        resultSet.getString("attribute_cd"),
                        resultSet.getString("attribute_nm"),
                        resultSet.getString("data_type_cd"),
                        resultSet.getString("taxonomy_cd"),
                        resultSet.getString("taxonomy_source_cd"),
                        resultSet.getString("taxonomy_jurisdiction_cd"),
                        resultSet.getBoolean("pk_flg"),
                        resultSet.getBoolean("fk_flg"),
                        resultSet.getBoolean("nullable_flg"),
                        getOffsetDateTime(resultSet, "created_ts"),
                        resultSet.getString("created_by"),
                        getOffsetDateTime(resultSet, "updated_ts"),
                        resultSet.getString("updated_by")
                )
        ).stream().findFirst().orElseThrow(() -> new SemanticLayerException("Insert attribute returned no rows"));
    }

    @Override
    public WorkflowTaskRecord insertWorkflowTask(WorkflowTaskWriteRequest request) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("workflow_task_id", request.workflow_task_id())
                .addValue("client_id", request.client_id())
                .addValue("workflow_type_cd", request.workflow_type_cd())
                .addValue("entity_type_cd", request.entity_type_cd())
                .addValue("entity_id", request.entity_id())
                .addValue("task_status_cd", request.task_status_cd())
                .addValue("created_ts", request.created_ts())
                .addValue("created_by", request.created_by())
                .addValue("updated_ts", request.updated_ts())
                .addValue("updated_by", request.updated_by());
        return jdbcTemplate().query(
                sqlQueryLoaderUtil.getQuery(INSERT_WORKFLOW_TASK),
                parameters,
                (resultSet, rowNum) -> new WorkflowTaskRecord(
                        getUuid(resultSet, "workflow_task_id"),
                        resultSet.getString("client_id"),
                        resultSet.getString("workflow_type_cd"),
                        resultSet.getString("entity_type_cd"),
                        getUuid(resultSet, "entity_id"),
                        resultSet.getString("task_status_cd"),
                        getOffsetDateTime(resultSet, "created_ts"),
                        resultSet.getString("created_by"),
                        getOffsetDateTime(resultSet, "updated_ts"),
                        resultSet.getString("updated_by")
                )
        ).stream().findFirst().orElseThrow(() -> new SemanticLayerException("Insert workflow task returned no rows"));
    }

    @Override
    public MetadataChangeHistoryRecord insertMetadataChangeHistory(MetadataChangeHistoryWriteRequest request) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("change_history_id", request.change_history_id())
                .addValue("client_id", request.client_id())
                .addValue("entity_type_cd", request.entity_type_cd())
                .addValue("entity_id", request.entity_id())
                .addValue("change_type_cd", request.change_type_cd())
                .addValue("change_summary_txt", request.change_summary_txt())
                .addValue("created_ts", request.created_ts())
                .addValue("created_by", request.created_by());
        return jdbcTemplate().query(
                sqlQueryLoaderUtil.getQuery(INSERT_METADATA_CHANGE_HISTORY),
                parameters,
                (resultSet, rowNum) -> new MetadataChangeHistoryRecord(
                        getUuid(resultSet, "change_history_id"),
                        resultSet.getString("client_id"),
                        resultSet.getString("entity_type_cd"),
                        getUuid(resultSet, "entity_id"),
                        resultSet.getString("change_type_cd"),
                        resultSet.getString("change_summary_txt"),
                        getOffsetDateTime(resultSet, "created_ts"),
                        resultSet.getString("created_by")
                )
        ).stream().findFirst().orElseThrow(() -> new SemanticLayerException("Insert metadata change history returned no rows"));
    }

    private NamedParameterJdbcTemplate jdbcTemplate() {
        NamedParameterJdbcTemplate jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
        if (jdbcTemplate == null) {
            throw new SemanticLayerException("NamedParameterJdbcTemplate is not configured");
        }
        return jdbcTemplate;
    }

    private static OffsetDateTime getOffsetDateTime(ResultSet resultSet, String columnLabel) throws SQLException {
        return resultSet.getObject(columnLabel, OffsetDateTime.class);
    }

    private static UUID getUuid(ResultSet resultSet, String columnLabel) throws SQLException {
        return resultSet.getObject(columnLabel, UUID.class);
    }
}
