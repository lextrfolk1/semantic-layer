package com.lextr.semanticlayer.dao.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lextr.semanticlayer.dao.ConsumptionDao;
import com.lextr.semanticlayer.exception.SemanticLayerException;
import com.lextr.semanticlayer.model.ConsumptionLayerRecord;
import com.lextr.semanticlayer.model.ConsumptionLayerWriteRequest;
import com.lextr.semanticlayer.model.ConsumptionOutboundRecord;
import com.lextr.semanticlayer.model.ConsumptionOutboundGrainWriteRequest;
import com.lextr.semanticlayer.model.ConsumptionOutboundWriteRequest;
import com.lextr.semanticlayer.model.ConsumptionPromotionRecord;
import com.lextr.semanticlayer.model.FilterLookupWorkflowTaskRecord;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcConsumptionDao implements ConsumptionDao {

    private static final Logger logger = LoggerFactory.getLogger(JdbcConsumptionDao.class);

    static final String INSERT_LAYER = "consumption_layer.insert_request";
    static final String INSERT_OUTBOUND = "consumption_outbound.insert_request";
    static final String INSERT_OUTBOUND_GRAIN = "consumption_outbound_grain.insert_request";
    static final String FIND_LAYERS = "consumption_layer.find_all";
    static final String FIND_LAYER = "consumption_layer.find_by_code";
    static final String FIND_EXPOSURES = "consumption_outbound.find_all_by_object";
    static final String FIND_EXPOSURE = "consumption_outbound.find_by_id";
    static final String FIND_EXPOSURE_UNSCOPED = "consumption_outbound.find_by_id_unscoped";
    static final String FIND_LATEST_PROMOTION = "consumption_promotion.find_by_outbound";
    static final String INSERT_PROMOTION = "consumption_promotion.insert_request";
    static final String APPLY_PROMOTION = "consumption_promotion.apply_promotion";
    static final String INSERT_WORKFLOW_TASK = "consumption_promotion.insert_workflow_task";
    static final String INSERT_METADATA_CHANGE_HISTORY = "consumption_promotion.insert_metadata_change_history";

    private final ObjectProvider<NamedParameterJdbcTemplate> jdbcTemplateProvider;
    private final SQLQueryLoaderUtil sqlQueryLoaderUtil;
    private final ObjectMapper objectMapper;

    public JdbcConsumptionDao(ObjectProvider<NamedParameterJdbcTemplate> jdbcTemplateProvider,
                              SQLQueryLoaderUtil sqlQueryLoaderUtil,
                              ObjectMapper objectMapper) {
        this.jdbcTemplateProvider = jdbcTemplateProvider;
        this.sqlQueryLoaderUtil = sqlQueryLoaderUtil;
        this.objectMapper = objectMapper;
    }

    @Override
    public ConsumptionLayerRecord insertLayer(ConsumptionLayerWriteRequest request) {
        logger.debug("Executing consumption layer insert. clientId={}, layerCode={}, queryKey={}", request.client_id(), request.layer_cd(), INSERT_LAYER);
        ConsumptionLayerRecord record = jdbcTemplate().query(
                sqlQueryLoaderUtil.getQuery(INSERT_LAYER),
                new MapSqlParameterSource()
                        .addValue("client_id", request.client_id())
                        .addValue("layer_cd", request.layer_cd())
                        .addValue("layer_nm", request.layer_nm())
                        .addValue("layer_desc_txt", request.layer_desc_txt())
                        .addValue("layer_type_cd", request.layer_type_cd())
                        .addValue("lifecycle_status_cd", request.lifecycle_status_cd())
                        .addValue("created_ts", request.created_ts())
                        .addValue("created_by", request.created_by())
                        .addValue("updated_ts", request.updated_ts())
                        .addValue("updated_by", request.updated_by()),
                (rs, rowNum) -> toLayerRecord(rs)
        ).stream().findFirst().orElseThrow(() -> new SemanticLayerException("Insert consumption layer returned no rows"));
        logger.debug("Consumption layer insert completed. clientId={}, layerCode={}", request.client_id(), request.layer_cd());
        return record;
    }

    @Override
    public ConsumptionOutboundRecord insertOutbound(ConsumptionOutboundWriteRequest request) {
        return jdbcTemplate().query(
                sqlQueryLoaderUtil.getQuery(INSERT_OUTBOUND),
                new MapSqlParameterSource()
                        .addValue("client_id", request.client_id())
                        .addValue("layer_cd", request.layer_cd())
                        .addValue("object_id", request.object_id())
                        .addValue("outbound_cd", request.outbound_cd())
                        .addValue("outbound_nm", request.outbound_nm())
                        .addValue("structure_type_cd", request.structure_type_cd())
                        .addValue("description_txt", request.description_txt())
                        .addValue("sdlc_status_cd", request.sdlc_status_cd())
                        .addValue("version_nbr", request.version_nbr())
                        .addValue("created_ts", request.created_ts())
                        .addValue("created_by", request.created_by())
                        .addValue("updated_ts", request.updated_ts())
                        .addValue("updated_by", request.updated_by()),
                (rs, rowNum) -> toExposureRecord(rs)
        ).stream().findFirst().orElseThrow(() -> new SemanticLayerException("Insert consumption outbound returned no rows"));
    }

    @Override
    public void insertOutboundGrain(ConsumptionOutboundGrainWriteRequest request) {
        jdbcTemplate().update(
                sqlQueryLoaderUtil.getQuery(INSERT_OUTBOUND_GRAIN),
                new MapSqlParameterSource()
                        .addValue("client_id", request.client_id())
                        .addValue("outbound_id", request.outbound_id())
                        .addValue("grain_level_nbr", request.grain_level_nbr())
                        .addValue("logical_attribute_cd", request.logical_attribute_cd())
                        .addValue("attribute_role_cd", request.attribute_role_cd())
                        .addValue("created_ts", request.created_ts())
                        .addValue("created_by", request.created_by())
                        .addValue("updated_ts", request.updated_ts())
                        .addValue("updated_by", request.updated_by())
        );
    }

    @Override
    public List<ConsumptionLayerRecord> findLayers(String clientId, String lifecycleStatusCode) {
        logger.debug("Executing consumption layer lookup. clientId={}, lifecycleStatusCode={}, queryKey={}", clientId, lifecycleStatusCode, FIND_LAYERS);
        List<ConsumptionLayerRecord> layers = jdbcTemplate().query(
                sqlQueryLoaderUtil.getQuery(FIND_LAYERS),
                new MapSqlParameterSource()
                        .addValue("client_id", clientId)
                        .addValue("lifecycle_status_cd", lifecycleStatusCode),
                (rs, rowNum) -> toLayerRecord(rs)
        );
        logger.debug("Consumption layer lookup completed. clientId={}, resultCount={}", clientId, layers.size());
        return layers;
    }

    @Override
    public Optional<ConsumptionLayerRecord> findLayer(String clientId, String layerCode) {
        logger.debug("Executing consumption layer lookup by code. clientId={}, layerCode={}, queryKey={}", clientId, layerCode, FIND_LAYER);
        List<ConsumptionLayerRecord> layers = jdbcTemplate().query(
                sqlQueryLoaderUtil.getQuery(FIND_LAYER),
                new MapSqlParameterSource()
                        .addValue("client_id", clientId)
                        .addValue("layer_cd", layerCode),
                (rs, rowNum) -> toLayerRecord(rs)
        );
        logger.debug("Consumption layer lookup by code completed. clientId={}, layerCode={}, resultCount={}", clientId, layerCode, layers.size());
        return layers.stream().findFirst();
    }

    @Override
    public List<ConsumptionOutboundRecord> findExposures(String clientId, UUID objectId, String structureTypeCode) {
        logger.debug("Executing consumption exposure lookup. clientId={}, objectId={}, structureTypeCode={}, queryKey={}", clientId, objectId, structureTypeCode, FIND_EXPOSURES);
        List<ConsumptionOutboundRecord> exposures = jdbcTemplate().query(
                sqlQueryLoaderUtil.getQuery(FIND_EXPOSURES),
                new MapSqlParameterSource()
                        .addValue("client_id", clientId)
                        .addValue("object_id", objectId)
                        .addValue("structure_type_cd", structureTypeCode),
                (rs, rowNum) -> toExposureRecord(rs)
        );
        logger.debug("Consumption exposure lookup completed. clientId={}, objectId={}, resultCount={}", clientId, objectId, exposures.size());
        return exposures;
    }

    @Override
    public Optional<ConsumptionOutboundRecord> findExposure(String clientId, Long exposureId) {
        logger.debug("Executing scoped consumption exposure lookup. clientId={}, exposureId={}, queryKey={}", clientId, exposureId, FIND_EXPOSURE);
        List<ConsumptionOutboundRecord> exposures = jdbcTemplate().query(
                sqlQueryLoaderUtil.getQuery(FIND_EXPOSURE),
                new MapSqlParameterSource()
                        .addValue("client_id", clientId)
                        .addValue("outbound_id", exposureId),
                (rs, rowNum) -> toExposureRecord(rs)
        );
        logger.debug("Scoped consumption exposure lookup completed. clientId={}, exposureId={}, resultCount={}", clientId, exposureId, exposures.size());
        return exposures.stream().findFirst();
    }

    @Override
    public Optional<ConsumptionOutboundRecord> findExposure(Long exposureId) {
        return jdbcTemplate().query(
                sqlQueryLoaderUtil.getQuery(FIND_EXPOSURE_UNSCOPED),
                new MapSqlParameterSource()
                        .addValue("outbound_id", exposureId),
                (rs, rowNum) -> toExposureRecord(rs)
        ).stream().findFirst();
    }

    @Override
    public Optional<ConsumptionPromotionRecord> findLatestPromotion(String clientId, Long exposureId) {
        logger.debug("Executing latest promotion lookup. clientId={}, exposureId={}, queryKey={}", clientId, exposureId, FIND_LATEST_PROMOTION);
        List<ConsumptionPromotionRecord> promotions = jdbcTemplate().query(
                sqlQueryLoaderUtil.getQuery(FIND_LATEST_PROMOTION),
                new MapSqlParameterSource()
                        .addValue("client_id", clientId)
                        .addValue("outbound_id", exposureId),
                (rs, rowNum) -> toPromotionRecord(rs)
        );
        logger.debug("Latest promotion lookup completed. clientId={}, exposureId={}, resultCount={}", clientId, exposureId, promotions.size());
        return promotions.stream().findFirst();
    }

    @Override
    public ConsumptionPromotionRecord insertPromotionRequest(String clientId,
                                                              Long outboundId,
                                                              String sourceSdlcStatusCode,
                                                              String targetSdlcStatusCode,
                                                              String validationStatusCode,
                                                              String opaDecisionCode,
                                                              Long workflowTaskId,
                                                              String promotionStatusCode,
                                                              Integer versionNumber,
                                                              OffsetDateTime createdTs,
                                                              String createdBy,
                                                              OffsetDateTime updatedTs,
                                                              String updatedBy) {
        return queryForPromotion(
                sqlQueryLoaderUtil.getQuery(INSERT_PROMOTION),
                new MapSqlParameterSource()
                        .addValue("client_id", clientId)
                        .addValue("outbound_id", outboundId)
                        .addValue("source_sdlc_status_cd", sourceSdlcStatusCode)
                        .addValue("target_sdlc_status_cd", targetSdlcStatusCode)
                        .addValue("validation_status_cd", validationStatusCode)
                        .addValue("opa_decision_cd", opaDecisionCode)
                        .addValue("workflow_task_id", workflowTaskId)
                        .addValue("promotion_status_cd", promotionStatusCode)
                        .addValue("version_nbr", versionNumber)
                        .addValue("created_ts", createdTs)
                        .addValue("created_by", createdBy)
                        .addValue("updated_ts", updatedTs)
                        .addValue("updated_by", updatedBy)
        );
    }

    @Override
    public ConsumptionPromotionRecord applyPromotion(String clientId,
                                                     Long id,
                                                     String targetSdlcStatusCode,
                                                     String validationStatusCode,
                                                     String opaDecisionCode,
                                                     String promotionStatusCode,
                                                     OffsetDateTime appliedTs,
                                                     String appliedBy,
                                                     OffsetDateTime updatedTs,
                                                     String updatedBy) {
        return queryForPromotion(
                sqlQueryLoaderUtil.getQuery(APPLY_PROMOTION),
                new MapSqlParameterSource()
                        .addValue("client_id", clientId)
                        .addValue("id", id)
                        .addValue("target_sdlc_status_cd", targetSdlcStatusCode)
                        .addValue("validation_status_cd", validationStatusCode)
                        .addValue("opa_decision_cd", opaDecisionCode)
                        .addValue("promotion_status_cd", promotionStatusCode)
                        .addValue("applied_ts", appliedTs)
                        .addValue("applied_by", appliedBy)
                        .addValue("updated_ts", updatedTs)
                        .addValue("updated_by", updatedBy)
        );
    }

    @Override
    public FilterLookupWorkflowTaskRecord insertWorkflowTask(String clientId,
                                                            String entityRef,
                                                            String taskStatusCode,
                                                            String submittedBy,
                                                            OffsetDateTime submittedTs,
                                                            String descriptionTxt,
                                                            String approvedBy,
                                                            OffsetDateTime approvedTs,
                                                            String approvalNoteTxt) {
        return jdbcTemplate().query(
                sqlQueryLoaderUtil.getQuery(INSERT_WORKFLOW_TASK),
                new MapSqlParameterSource()
                        .addValue("entity_type_cd", "CONSUMPTION_EXPOSURE")
                        .addValue("entity_ref", entityRef)
                        .addValue("task_status_cd", taskStatusCode)
                        .addValue("submitted_by", submittedBy)
                        .addValue("submitted_ts", submittedTs)
                        .addValue("assigned_to", null)
                        .addValue("due_dt", null)
                        .addValue("description_txt", descriptionTxt)
                        .addValue("client_id", clientId)
                        .addValue("approved_by", approvedBy)
                        .addValue("approved_ts", approvedTs)
                        .addValue("approval_note_txt", approvalNoteTxt)
                ,
                (rs, rowNum) -> new FilterLookupWorkflowTaskRecord(
                        resultSetLong(rs, "id"),
                        rs.getString("task_type_cd"),
                        rs.getString("entity_type_cd"),
                        rs.getString("entity_ref"),
                        rs.getString("task_status_cd"),
                        rs.getString("submitted_by"),
                        rs.getObject("submitted_ts", OffsetDateTime.class),
                        rs.getString("assigned_to"),
                        rs.getObject("due_dt", java.time.LocalDate.class),
                        rs.getString("description_txt"),
                        rs.getString("client_id"),
                        rs.getString("approved_by"),
                        rs.getObject("approved_ts", OffsetDateTime.class),
                        rs.getString("approval_note_txt")
                )
        ).stream().findFirst().orElseThrow(() -> new SemanticLayerException("Insert workflow task returned no rows"));
    }

    @Override
    public void insertMetadataChangeHistory(String clientId,
                                            String entityTypeCode,
                                            String entityRef,
                                            String changeTypeCode,
                                            String changeReasonTxt,
                                            String changedBy,
                                            OffsetDateTime changedTs) {
        logger.debug("Executing consumption metadata history insert. clientId={}, entityTypeCode={}, entityRef={}, changeTypeCode={}, queryKey={}", clientId, entityTypeCode, entityRef, changeTypeCode, INSERT_METADATA_CHANGE_HISTORY);
        int affectedRows = jdbcTemplate().update(
                sqlQueryLoaderUtil.getQuery(INSERT_METADATA_CHANGE_HISTORY),
                new MapSqlParameterSource()
                        .addValue("client_id", clientId)
                        .addValue("entity_type_cd", entityTypeCode)
                        .addValue("entity_ref", entityRef)
                        .addValue("change_type_cd", changeTypeCode)
                        .addValue("change_reason_txt", changeReasonTxt)
                        .addValue("changed_by", changedBy)
                        .addValue("changed_ts", changedTs)
        );
        logger.debug("Consumption metadata history insert completed. clientId={}, entityTypeCode={}, entityRef={}, affectedRows={}", clientId, entityTypeCode, entityRef, affectedRows);
    }

    private NamedParameterJdbcTemplate jdbcTemplate() {
        NamedParameterJdbcTemplate jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
        if (jdbcTemplate == null) {
            throw new SemanticLayerException("NamedParameterJdbcTemplate is not configured");
        }
        return jdbcTemplate;
    }

    private ConsumptionLayerRecord toLayerRecord(ResultSet resultSet) throws SQLException {
        return new ConsumptionLayerRecord(
                resultSet.getObject("id", Long.class),
                resultSet.getString("client_id"),
                resultSet.getString("layer_cd"),
                resultSet.getString("layer_nm"),
                resultSet.getString("layer_desc_txt"),
                resultSet.getString("layer_type_cd"),
                resultSet.getString("lifecycle_status_cd"),
                resultSet.getObject("created_ts", OffsetDateTime.class),
                resultSet.getString("created_by"),
                resultSet.getObject("updated_ts", OffsetDateTime.class),
                resultSet.getString("updated_by")
        );
    }

    private ConsumptionOutboundRecord toExposureRecord(ResultSet resultSet) throws SQLException {
        return new ConsumptionOutboundRecord(
                resultSet.getObject("id", Long.class),
                resultSet.getString("client_id"),
                resultSet.getString("layer_cd"),
                resultSet.getObject("object_id", Long.class),
                resultSet.getString("outbound_cd"),
                resultSet.getString("outbound_nm"),
                resultSet.getString("structure_type_cd"),
                resultSet.getString("description_txt"),
                parseAttributes(resultSet.getObject("attributes_jsonb")),
                resultSet.getString("sdlc_status_cd"),
                getInteger(resultSet, "version_nbr"),
                resultSet.getObject("created_ts", OffsetDateTime.class),
                resultSet.getString("created_by"),
                resultSet.getObject("updated_ts", OffsetDateTime.class),
                resultSet.getString("updated_by")
        );
    }

    private ConsumptionPromotionRecord toPromotionRecord(ResultSet resultSet) throws SQLException {
        return new ConsumptionPromotionRecord(
                resultSet.getObject("id", Long.class),
                resultSet.getString("client_id"),
                resultSet.getObject("outbound_id", Long.class),
                resultSet.getString("source_sdlc_status_cd"),
                resultSet.getString("target_sdlc_status_cd"),
                resultSet.getString("validation_status_cd"),
                resultSet.getString("opa_decision_cd"),
                resultSet.getObject("workflow_task_id", Long.class),
                resultSet.getString("promotion_status_cd"),
                getInteger(resultSet, "version_nbr"),
                resultSet.getObject("applied_ts", OffsetDateTime.class),
                resultSet.getString("applied_by"),
                resultSet.getObject("created_ts", OffsetDateTime.class),
                resultSet.getString("created_by"),
                resultSet.getObject("updated_ts", OffsetDateTime.class),
                resultSet.getString("updated_by")
        );
    }

    private ConsumptionPromotionRecord queryForPromotion(String sql, MapSqlParameterSource parameters) {
        return jdbcTemplate().query(sql, parameters, (rs, rowNum) -> toPromotionRecord(rs)).stream()
                .findFirst()
                .orElseThrow(() -> new SemanticLayerException("Promotion query did not return a row"));
    }

    private List<String> parseAttributes(Object rawValue) {
        if (rawValue == null) {
            return List.of();
        }
        try {
            return objectMapper.readValue(rawValue.toString(), new TypeReference<List<String>>() { });
        } catch (Exception exception) {
            logger.error("Failed to parse consumption attributes JSON. errorMessage={}", exception.getMessage(), exception);
            throw new SemanticLayerException("Unable to parse consumption attributes JSON", exception);
        }
    }

    private static Integer getInteger(ResultSet resultSet, String columnLabel) throws SQLException {
        Object value = resultSet.getObject(columnLabel);
        return value == null ? null : ((Number) value).intValue();
    }

    private static Long resultSetLong(ResultSet resultSet, String columnLabel) throws SQLException {
        Object value = resultSet.getObject(columnLabel);
        return value == null ? null : ((Number) value).longValue();
    }
}
