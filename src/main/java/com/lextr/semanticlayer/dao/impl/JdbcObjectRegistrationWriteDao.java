package com.lextr.semanticlayer.dao.impl;

import com.lextr.semanticlayer.dao.ObjectRegistrationWriteDao;
import com.lextr.semanticlayer.exception.SemanticLayerException;
import com.lextr.semanticlayer.model.AttributeAccessGrantRecord;
import com.lextr.semanticlayer.model.AttributeAccessGrantStatusUpdateRequest;
import com.lextr.semanticlayer.model.AttributeAccessGrantWriteRequest;
import com.lextr.semanticlayer.model.AttributeCatalogRecord;
import com.lextr.semanticlayer.model.AttributeCatalogWriteRequest;
import com.lextr.semanticlayer.model.AttributeClassificationRecord;
import com.lextr.semanticlayer.model.AttributeClassificationWriteRequest;
import com.lextr.semanticlayer.model.MetadataChangeHistoryRecord;
import com.lextr.semanticlayer.model.MetadataChangeHistoryWriteRequest;
import com.lextr.semanticlayer.model.ObjectCatalogRecord;
import com.lextr.semanticlayer.model.ObjectClassificationRecord;
import com.lextr.semanticlayer.model.ObjectClassificationWriteRequest;
import com.lextr.semanticlayer.model.ObjectCatalogWriteRequest;
import com.lextr.semanticlayer.model.WorkflowTaskRecord;
import com.lextr.semanticlayer.model.WorkflowTaskWriteRequest;
import com.lextr.semanticlayer.util.SQLQueryLoaderUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger logger = LoggerFactory.getLogger(JdbcObjectRegistrationWriteDao.class);

    static final String INSERT_DRAFT_OBJECT = "object_registration.insert_draft_object";
    static final String INSERT_ATTRIBUTE = "object_registration.insert_attribute";
    static final String UPDATE_OBJECT_CLASSIFICATION = "object_registration.update_object_classification";
    static final String UPDATE_ATTRIBUTE_CLASSIFICATION = "object_registration.update_attribute_classification";
    static final String INSERT_ATTRIBUTE_ACCESS_GRANT = "object_registration.insert_attribute_access_grant";
    static final String UPDATE_ATTRIBUTE_ACCESS_GRANT_STATUS = "object_registration.update_attribute_access_grant_status";
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
        logger.debug("Executing object draft insert. clientId={}, objectCode={}, schemaCode={}",
                request.client_id(), request.object_cd(), request.schema_cd());
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
        ObjectCatalogRecord record = jdbcTemplate().query(
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
        logger.debug("Object draft insert completed. clientId={}, objectId={}", request.client_id(), record.object_id());
        return record;
    }

    @Override
    public AttributeCatalogRecord insertAttribute(AttributeCatalogWriteRequest request) {
        logger.debug("Executing object attribute insert. clientId={}, objectId={}, attributeCode={}",
                request.client_id(), request.object_id(), request.attribute_cd());
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
        AttributeCatalogRecord record = jdbcTemplate().query(
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
        logger.debug("Object attribute insert completed. clientId={}, attributeId={}", request.client_id(), record.attribute_id());
        return record;
    }

    @Override
    public ObjectClassificationRecord updateObjectClassification(ObjectClassificationWriteRequest request) {
        logger.debug("Executing object classification update. clientId={}, objectId={}, dataClassificationCode={}",
                request.client_id(), request.object_id(), request.data_classification_cd());
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("object_id", request.object_id())
                .addValue("client_id", request.client_id())
                .addValue("data_classification_cd", request.data_classification_cd())
                .addValue("pii_flg", request.pii_flg())
                .addValue("confidential_flg", request.confidential_flg())
                .addValue("updated_ts", request.updated_ts())
                .addValue("updated_by", request.updated_by());
        ObjectClassificationRecord record = jdbcTemplate().query(
                sqlQueryLoaderUtil.getQuery(UPDATE_OBJECT_CLASSIFICATION),
                parameters,
                (resultSet, rowNum) -> new ObjectClassificationRecord(
                        getUuid(resultSet, "object_id"),
                        resultSet.getString("client_id"),
                        resultSet.getString("object_cd"),
                        resultSet.getString("object_nm"),
                        resultSet.getString("object_type_cd"),
                        resultSet.getString("schema_cd"),
                        getUuid(resultSet, "connection_id"),
                        resultSet.getString("data_classification_cd"),
                        resultSet.getBoolean("pii_flg"),
                        resultSet.getBoolean("confidential_flg"),
                        resultSet.getString("lifecycle_status_cd"),
                        getOffsetDateTime(resultSet, "created_ts"),
                        resultSet.getString("created_by"),
                        getOffsetDateTime(resultSet, "updated_ts"),
                        resultSet.getString("updated_by")
                )
        ).stream().findFirst().orElseThrow(() -> new SemanticLayerException("Update object classification returned no rows"));
        logger.debug("Object classification update completed. clientId={}, objectId={}", request.client_id(), request.object_id());
        return record;
    }

    @Override
    public AttributeClassificationRecord updateAttributeClassification(AttributeClassificationWriteRequest request) {
        logger.debug("Executing attribute classification update. clientId={}, objectId={}, attributeCode={}, dataClassificationCode={}",
                request.client_id(), request.object_id(), request.attribute_cd(), request.data_classification_cd());
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("object_id", request.object_id())
                .addValue("client_id", request.client_id())
                .addValue("attribute_cd", request.attribute_cd())
                .addValue("data_classification_cd", request.data_classification_cd())
                .addValue("pii_flg", request.pii_flg())
                .addValue("confidential_flg", request.confidential_flg())
                .addValue("masking_policy_cd", request.masking_policy_cd())
                .addValue("mnpi_flg", request.mnpi_flg())
                .addValue("csi_flg", request.csi_flg())
                .addValue("ai_exposure_cd", request.ai_exposure_cd())
                .addValue("updated_ts", request.updated_ts())
                .addValue("updated_by", request.updated_by());
        AttributeClassificationRecord record = jdbcTemplate().query(
                sqlQueryLoaderUtil.getQuery(UPDATE_ATTRIBUTE_CLASSIFICATION),
                parameters,
                (resultSet, rowNum) -> new AttributeClassificationRecord(
                        getUuid(resultSet, "attribute_id"),
                        getUuid(resultSet, "object_id"),
                        resultSet.getString("client_id"),
                        resultSet.getString("attribute_cd"),
                        resultSet.getString("attribute_nm"),
                        resultSet.getString("data_type_cd"),
                        resultSet.getString("taxonomy_cd"),
                        resultSet.getString("taxonomy_source_cd"),
                        resultSet.getString("taxonomy_jurisdiction_cd"),
                        resultSet.getString("data_classification_cd"),
                        resultSet.getBoolean("pii_flg"),
                        resultSet.getBoolean("confidential_flg"),
                        resultSet.getString("masking_policy_cd"),
                        resultSet.getBoolean("mnpi_flg"),
                        resultSet.getBoolean("csi_flg"),
                        resultSet.getString("ai_exposure_cd"),
                        resultSet.getBoolean("pk_flg"),
                        resultSet.getBoolean("fk_flg"),
                        resultSet.getBoolean("nullable_flg"),
                        getOffsetDateTime(resultSet, "created_ts"),
                        resultSet.getString("created_by"),
                        getOffsetDateTime(resultSet, "updated_ts"),
                        resultSet.getString("updated_by")
                )
        ).stream().findFirst().orElseThrow(() -> new SemanticLayerException("Update attribute classification returned no rows"));
        logger.debug("Attribute classification update completed. clientId={}, objectId={}, attributeCode={}",
                request.client_id(), request.object_id(), request.attribute_cd());
        return record;
    }

    @Override
    public AttributeAccessGrantRecord insertAttributeAccessGrant(AttributeAccessGrantWriteRequest request) {
        logger.debug("Executing attribute access grant insert. clientId={}, objectCode={}, attributeCode={}, roleCode={}, purposeCode={}",
                request.client_id(), request.object_cd(), request.attribute_cd(), request.role_cd(), request.purpose_cd());
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("client_id", request.client_id())
                .addValue("schema_cd", request.schema_cd())
                .addValue("object_cd", request.object_cd())
                .addValue("attribute_cd", request.attribute_cd())
                .addValue("role_cd", request.role_cd())
                .addValue("purpose_cd", request.purpose_cd())
                .addValue("grant_scope_cd", request.grant_scope_cd())
                .addValue("grant_status_cd", request.grant_status_cd())
                .addValue("approved_by", request.approved_by())
                .addValue("approved_ts", request.approved_ts())
                .addValue("created_ts", request.created_ts())
                .addValue("created_by", request.created_by())
                .addValue("updated_ts", request.updated_ts())
                .addValue("updated_by", request.updated_by());
        AttributeAccessGrantRecord record = jdbcTemplate().query(
                sqlQueryLoaderUtil.getQuery(INSERT_ATTRIBUTE_ACCESS_GRANT),
                parameters,
                JdbcObjectRegistrationWriteDao::mapAttributeAccessGrantRow
        ).stream().findFirst().orElseThrow(() -> new SemanticLayerException("Insert attribute access grant returned no rows"));
        logger.debug("Attribute access grant insert completed. clientId={}, grantId={}", request.client_id(), record.id());
        return record;
    }

    @Override
    public AttributeAccessGrantRecord updateAttributeAccessGrantStatus(AttributeAccessGrantStatusUpdateRequest request) {
        logger.debug("Executing attribute access grant status update. clientId={}, grantId={}, grantStatusCode={}",
                request.client_id(), request.id(), request.grant_status_cd());
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("id", request.id())
                .addValue("client_id", request.client_id())
                .addValue("grant_status_cd", request.grant_status_cd())
                .addValue("approved_by", request.approved_by())
                .addValue("approved_ts", request.approved_ts())
                .addValue("updated_ts", request.updated_ts())
                .addValue("updated_by", request.updated_by());
        AttributeAccessGrantRecord record = jdbcTemplate().query(
                sqlQueryLoaderUtil.getQuery(UPDATE_ATTRIBUTE_ACCESS_GRANT_STATUS),
                parameters,
                JdbcObjectRegistrationWriteDao::mapAttributeAccessGrantRow
        ).stream().findFirst().orElseThrow(() -> new SemanticLayerException("Update attribute access grant status returned no rows"));
        logger.debug("Attribute access grant status update completed. clientId={}, grantId={}", request.client_id(), request.id());
        return record;
    }

    @Override
    public WorkflowTaskRecord insertWorkflowTask(WorkflowTaskWriteRequest request) {
        logger.debug("Executing object workflow task insert. clientId={}, workflowTaskId={}, entityTypeCode={}",
                request.client_id(), request.workflow_task_id(), request.entity_type_cd());
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
        WorkflowTaskRecord record = jdbcTemplate().query(
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
        logger.debug("Object workflow task insert completed. clientId={}, workflowTaskId={}", request.client_id(), record.workflow_task_id());
        return record;
    }

    @Override
    public MetadataChangeHistoryRecord insertMetadataChangeHistory(MetadataChangeHistoryWriteRequest request) {
        logger.debug("Executing object metadata change insert. clientId={}, entityTypeCode={}, changeTypeCode={}",
                request.client_id(), request.entity_type_cd(), request.change_type_cd());
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("change_history_id", request.change_history_id())
                .addValue("client_id", request.client_id())
                .addValue("entity_type_cd", request.entity_type_cd())
                .addValue("entity_id", request.entity_id())
                .addValue("change_type_cd", request.change_type_cd())
                .addValue("change_summary_txt", request.change_summary_txt())
                .addValue("created_ts", request.created_ts())
                .addValue("created_by", request.created_by());
        MetadataChangeHistoryRecord record = jdbcTemplate().query(
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
        logger.debug("Object metadata change insert completed. clientId={}, changeHistoryId={}",
                request.client_id(), record.change_history_id());
        return record;
    }

    private NamedParameterJdbcTemplate jdbcTemplate() {
        NamedParameterJdbcTemplate jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
        if (jdbcTemplate == null) {
            logger.error("NamedParameterJdbcTemplate is not configured for object registration DAO.");
            throw new SemanticLayerException("NamedParameterJdbcTemplate is not configured");
        }
        return jdbcTemplate;
    }

    private static AttributeAccessGrantRecord mapAttributeAccessGrantRow(ResultSet resultSet, int rowNum) throws SQLException {
        return new AttributeAccessGrantRecord(
                resultSet.getObject("id", Long.class),
                resultSet.getString("client_id"),
                resultSet.getString("schema_cd"),
                resultSet.getString("object_cd"),
                resultSet.getString("attribute_cd"),
                resultSet.getString("role_cd"),
                resultSet.getString("purpose_cd"),
                resultSet.getString("grant_scope_cd"),
                resultSet.getString("grant_status_cd"),
                resultSet.getString("approved_by"),
                getOffsetDateTime(resultSet, "approved_ts"),
                getOffsetDateTime(resultSet, "created_ts"),
                resultSet.getString("created_by"),
                getOffsetDateTime(resultSet, "updated_ts"),
                resultSet.getString("updated_by")
        );
    }

    private static OffsetDateTime getOffsetDateTime(ResultSet resultSet, String columnLabel) throws SQLException {
        return resultSet.getObject(columnLabel, OffsetDateTime.class);
    }

    private static UUID getUuid(ResultSet resultSet, String columnLabel) throws SQLException {
        return resultSet.getObject(columnLabel, UUID.class);
    }
}
