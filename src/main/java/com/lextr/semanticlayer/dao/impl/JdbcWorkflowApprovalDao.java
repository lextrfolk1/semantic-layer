package com.lextr.semanticlayer.dao.impl;

import com.lextr.semanticlayer.dao.WorkflowApprovalDao;
import com.lextr.semanticlayer.exception.SemanticLayerException;
import com.lextr.semanticlayer.exception.RegistryResourceNotFoundException;
import com.lextr.semanticlayer.model.FilterLookupWorkflowTaskRecord;
import com.lextr.semanticlayer.util.SQLQueryLoaderUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import java.util.List;

@Repository
public class JdbcWorkflowApprovalDao implements WorkflowApprovalDao {

    private static final Logger logger = LoggerFactory.getLogger(JdbcWorkflowApprovalDao.class);

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
            logger.error("NamedParameterJdbcTemplate is not configured for workflow approval DAO.");
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
        logger.debug("Executing workflow task query by client and id. clientId={}, taskId={}", clientId, id);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("client_id", clientId)
                .addValue("id", id);
        FilterLookupWorkflowTaskRecord record = jdbcTemplate.query(
                sqlQueryLoaderUtil.getQuery(FIND_TASK_BY_ID),
                params,
                taskRowMapper
        ).stream().findFirst().orElse(null);
        logger.debug("Workflow task query by client and id completed. clientId={}, taskId={}, found={}",
                clientId, id, record != null);
        return record;
    }

    @Override
    public FilterLookupWorkflowTaskRecord approveTask(String clientId, Long id, String approvedBy, OffsetDateTime approvedTs, String approvalNote) {
        checkJdbcTemplate();
        logger.debug("Executing workflow task approval. clientId={}, taskId={}, approvedBy={}", clientId, id, approvedBy);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("client_id", clientId)
                .addValue("id", id)
                .addValue("task_status_cd", "APPROVED")
                .addValue("approved_by", approvedBy)
                .addValue("approved_ts", approvedTs)
                .addValue("approval_note_txt", approvalNote);
        FilterLookupWorkflowTaskRecord record = jdbcTemplate.query(
                sqlQueryLoaderUtil.getQuery(APPROVE_TASK),
                params,
                taskRowMapper
        ).stream().findFirst().orElseThrow(() -> new SemanticLayerException("Failed to approve task or task not pending: " + id));
        logger.debug("Workflow task approval completed. clientId={}, taskId={}", clientId, id);
        return record;
    }

    @Override
    public void approveLookup(String clientId, String lookupCd, String governanceStatus, OffsetDateTime updatedTs, String updatedBy) {
        checkJdbcTemplate();
        logger.debug("Executing workflow lookup approval. clientId={}, lookupCode={}, governanceStatusCode={}",
                clientId, lookupCd, governanceStatus);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("client_id", clientId)
                .addValue("lookup_cd", lookupCd)
                .addValue("governance_status_cd", governanceStatus)
                .addValue("updated_ts", updatedTs)
                .addValue("updated_by", updatedBy);
        jdbcTemplate.query(
                sqlQueryLoaderUtil.getQuery(APPROVE_LOOKUP),
                params,
                (resultSet, rowNum) -> {
                    return rowNum;
                }
        ).stream().findFirst().orElseThrow(() -> new RegistryResourceNotFoundException("filter lookup", lookupCd));
        logger.debug("Workflow lookup approval completed. clientId={}, lookupCode={}", clientId, lookupCd);
    }

    @Override
    public void approveAttributeOverride(String clientId, Long id, String lifecycleStatus, OffsetDateTime updatedTs, String updatedBy) {
        checkJdbcTemplate();
        logger.debug("Executing attribute override approval. clientId={}, overrideId={}, lifecycleStatusCode={}",
                clientId, id, lifecycleStatus);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("client_id", clientId)
                .addValue("id", id)
                .addValue("lifecycle_status_cd", lifecycleStatus)
                .addValue("updated_ts", updatedTs)
                .addValue("updated_by", updatedBy);
        int affectedRows = jdbcTemplate.update(sqlQueryLoaderUtil.getQuery(APPROVE_ATTRIBUTE_OVERRIDE), params);
        logger.debug("Attribute override approval completed. clientId={}, overrideId={}, affectedRows={}", clientId, id, affectedRows);
    }

    @Override
    public void approveFilterLookupValue(String lookupCd, String valueCd, String lifecycleStatus, boolean validated, OffsetDateTime updatedTs) {
        checkJdbcTemplate();
        logger.debug("Executing filter lookup value approval. lookupCode={}, valueCode={}, lifecycleStatusCode={}, validated={}",
                lookupCd, valueCd, lifecycleStatus, validated);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("lookup_cd", lookupCd)
                .addValue("value_cd", valueCd)
                .addValue("lifecycle_status_cd", lifecycleStatus)
                .addValue("validated_flg", validated)
                .addValue("updated_ts", updatedTs);
        int affectedRows = jdbcTemplate.update(sqlQueryLoaderUtil.getQuery(APPROVE_FILTER_LOOKUP_VALUE), params);
        logger.debug("Filter lookup value approval completed. lookupCode={}, valueCode={}, affectedRows={}",
                lookupCd, valueCd, affectedRows);
    }

    @Override
    public FilterLookupWorkflowTaskRecord findTaskByIdOnly(Long id) {
        checkJdbcTemplate();
        logger.debug("Executing workflow task query by id only. taskId={}", id);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", id);
        FilterLookupWorkflowTaskRecord record = jdbcTemplate.query(
                sqlQueryLoaderUtil.getQuery("workflow_approval.find_task_by_id_only"),
                params,
                taskRowMapper
        ).stream().findFirst().orElse(null);
        logger.debug("Workflow task query by id only completed. taskId={}, found={}", id, record != null);
        return record;
    }

    @Override
    public FilterLookupWorkflowTaskRecord rejectTask(String clientId, Long id, String rejectedBy, OffsetDateTime rejectedTs, String rejectionNote) {
        checkJdbcTemplate();
        logger.debug("Executing workflow task rejection. clientId={}, taskId={}, rejectedBy={}", clientId, id, rejectedBy);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("client_id", clientId)
                .addValue("id", id)
                .addValue("rejected_by", rejectedBy)
                .addValue("rejected_ts", rejectedTs)
                .addValue("rejection_note_txt", rejectionNote);
        FilterLookupWorkflowTaskRecord record = jdbcTemplate.query(
                sqlQueryLoaderUtil.getQuery("workflow_approval.reject_task"),
                params,
                taskRowMapper
        ).stream().findFirst().orElseThrow(() -> new SemanticLayerException("Failed to reject task or task not pending: " + id));
        logger.debug("Workflow task rejection completed. clientId={}, taskId={}", clientId, id);
        return record;
    }

    @Override
    public void approveObject(String clientId, String objectId, String lifecycleStatus, OffsetDateTime updatedTs, String updatedBy) {
        checkJdbcTemplate();
        logger.debug("Executing object approval. clientId={}, objectId={}, lifecycleStatusCode={}",
                clientId, objectId, lifecycleStatus);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("client_id", clientId)
                .addValue("object_id", objectId)
                .addValue("lifecycle_status_cd", lifecycleStatus)
                .addValue("updated_ts", updatedTs)
                .addValue("updated_by", updatedBy);
        int affectedRows = jdbcTemplate.update(sqlQueryLoaderUtil.getQuery("workflow_approval.approve_object"), params);
        logger.debug("Object approval completed. clientId={}, objectId={}, affectedRows={}", clientId, objectId, affectedRows);
    }

    @Override
    public void grantDefaultAttributeAccess(String clientId, String objectId, String approvedBy, OffsetDateTime approvedTs) {
        checkJdbcTemplate();
        logger.debug("Executing default attribute access grant. clientId={}, objectId={}, approvedBy={}",
                clientId, objectId, approvedBy);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("client_id", clientId)
                .addValue("object_id", objectId)
                .addValue("approved_by", approvedBy)
                .addValue("approved_ts", approvedTs);
        int affectedRows = jdbcTemplate.update(sqlQueryLoaderUtil.getQuery("workflow_approval.grant_default_attribute_access"), params);
        logger.debug("Default attribute access grant completed. clientId={}, objectId={}, affectedRows={}",
                clientId, objectId, affectedRows);
    }

    @Override
    public void approvePairing(String clientId, String pairingCd, String lifecycleStatus, OffsetDateTime updatedTs, String updatedBy) {
        checkJdbcTemplate();
        logger.debug("Executing pairing approval. clientId={}, pairingCode={}, lifecycleStatusCode={}",
                clientId, pairingCd, lifecycleStatus);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("client_id", clientId)
                .addValue("pairing_cd", pairingCd)
                .addValue("lifecycle_status_cd", lifecycleStatus)
                .addValue("updated_ts", updatedTs)
                .addValue("updated_by", updatedBy);
        int affectedRows = jdbcTemplate.update(sqlQueryLoaderUtil.getQuery("workflow_approval.approve_pairing"), params);
        logger.debug("Pairing approval completed. clientId={}, pairingCode={}, affectedRows={}", clientId, pairingCd, affectedRows);
    }

    @Override
    public void approveRelationship(String relationshipIdStr, String lifecycleStatus, OffsetDateTime updatedTs, String updatedBy) {
        checkJdbcTemplate();
        logger.debug("Executing relationship approval. relationshipIdentifier={}, lifecycleStatusCode={}",
                relationshipIdStr, lifecycleStatus);
        String relationshipCd = resolveRelationshipCd(relationshipIdStr);
        if (relationshipCd == null) {
            logger.warn("Relationship approval failed because relationship code could not be resolved. relationshipIdentifier={}",
                    relationshipIdStr);
            throw new SemanticLayerException("Could not resolve relationship_cd for relationship UUID: " + relationshipIdStr);
        }
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("relationship_cd", relationshipCd)
                .addValue("lifecycle_status_cd", lifecycleStatus)
                .addValue("updated_ts", updatedTs)
                .addValue("updated_by", updatedBy);
        int affectedRows = jdbcTemplate.update(sqlQueryLoaderUtil.getQuery("workflow_approval.approve_relationship"), params);
        logger.debug("Relationship approval completed. relationshipCode={}, affectedRows={}", relationshipCd, affectedRows);
    }

    @Override
    public void rejectLookup(String clientId, String lookupCd, String governanceStatus, String lifecycleStatus, OffsetDateTime updatedTs, String updatedBy) {
        checkJdbcTemplate();
        logger.debug("Executing lookup rejection. clientId={}, lookupCode={}, governanceStatusCode={}, lifecycleStatusCode={}",
                clientId, lookupCd, governanceStatus, lifecycleStatus);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("client_id", clientId)
                .addValue("lookup_cd", lookupCd)
                .addValue("governance_status_cd", governanceStatus)
                .addValue("lifecycle_status_cd", lifecycleStatus)
                .addValue("updated_ts", updatedTs)
                .addValue("updated_by", updatedBy);
        int affectedRows = jdbcTemplate.update(sqlQueryLoaderUtil.getQuery("workflow_approval.reject_lookup"), params);
        logger.debug("Lookup rejection completed. clientId={}, lookupCode={}, affectedRows={}", clientId, lookupCd, affectedRows);
    }

    @Override
    public void rejectAttributeOverride(String clientId, Long id, String overrideStatus, OffsetDateTime updatedTs, String updatedBy) {
        checkJdbcTemplate();
        logger.debug("Executing attribute override rejection. clientId={}, overrideId={}, overrideStatusCode={}",
                clientId, id, overrideStatus);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("client_id", clientId)
                .addValue("id", id)
                .addValue("override_status_cd", overrideStatus)
                .addValue("updated_ts", updatedTs)
                .addValue("updated_by", updatedBy);
        int affectedRows = jdbcTemplate.update(sqlQueryLoaderUtil.getQuery("workflow_approval.reject_attribute_override"), params);
        logger.debug("Attribute override rejection completed. clientId={}, overrideId={}, affectedRows={}", clientId, id, affectedRows);
    }

    @Override
    public void rejectObject(String clientId, String objectId, String lifecycleStatus, String governanceReviewStatus, OffsetDateTime updatedTs, String updatedBy) {
        checkJdbcTemplate();
        logger.debug("Executing object rejection. clientId={}, objectId={}, lifecycleStatusCode={}, governanceReviewStatusCode={}",
                clientId, objectId, lifecycleStatus, governanceReviewStatus);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("client_id", clientId)
                .addValue("object_id", objectId)
                .addValue("lifecycle_status_cd", lifecycleStatus)
                .addValue("governance_review_status_cd", governanceReviewStatus)
                .addValue("updated_ts", updatedTs)
                .addValue("updated_by", updatedBy);
        int affectedRows = jdbcTemplate.update(sqlQueryLoaderUtil.getQuery("workflow_approval.reject_object"), params);
        logger.debug("Object rejection completed. clientId={}, objectId={}, affectedRows={}", clientId, objectId, affectedRows);
    }

    @Override
    public void rejectPairing(String clientId, String pairingCd, String lifecycleStatus, String governanceReviewStatus, OffsetDateTime updatedTs, String updatedBy) {
        checkJdbcTemplate();
        logger.debug("Executing pairing rejection. clientId={}, pairingCode={}, lifecycleStatusCode={}, governanceReviewStatusCode={}",
                clientId, pairingCd, lifecycleStatus, governanceReviewStatus);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("client_id", clientId)
                .addValue("pairing_cd", pairingCd)
                .addValue("lifecycle_status_cd", lifecycleStatus)
                .addValue("governance_review_status_cd", governanceReviewStatus)
                .addValue("updated_ts", updatedTs)
                .addValue("updated_by", updatedBy);
        int affectedRows = jdbcTemplate.update(sqlQueryLoaderUtil.getQuery("workflow_approval.reject_pairing"), params);
        logger.debug("Pairing rejection completed. clientId={}, pairingCode={}, affectedRows={}", clientId, pairingCd, affectedRows);
    }

    @Override
    public void rejectRelationship(String relationshipIdStr, String lifecycleStatus, OffsetDateTime updatedTs, String updatedBy) {
        checkJdbcTemplate();
        logger.debug("Executing relationship rejection. relationshipIdentifier={}, lifecycleStatusCode={}",
                relationshipIdStr, lifecycleStatus);
        String relationshipCd = resolveRelationshipCd(relationshipIdStr);
        if (relationshipCd == null) {
            logger.warn("Relationship rejection failed because relationship code could not be resolved. relationshipIdentifier={}",
                    relationshipIdStr);
            throw new SemanticLayerException("Could not resolve relationship_cd for relationship UUID: " + relationshipIdStr);
        }
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("relationship_cd", relationshipCd)
                .addValue("lifecycle_status_cd", lifecycleStatus)
                .addValue("updated_ts", updatedTs)
                .addValue("updated_by", updatedBy);
        int affectedRows = jdbcTemplate.update(sqlQueryLoaderUtil.getQuery("workflow_approval.reject_relationship"), params);
        logger.debug("Relationship rejection completed. relationshipCode={}, affectedRows={}", relationshipCd, affectedRows);
    }

    private String resolveRelationshipCd(String relationshipIdStr) {
        java.util.UUID targetId;
        try {
            targetId = java.util.UUID.fromString(relationshipIdStr);
        } catch (IllegalArgumentException e) {
            return relationshipIdStr;
        }

        List<String> codes = jdbcTemplate.queryForList(
                "SELECT relationship_cd FROM meta.semantic_relationship_catalog",
                new MapSqlParameterSource(),
                String.class
        );
        logger.debug("Resolving relationship code from UUID. relationshipIdentifier={}, candidateCount={}",
                relationshipIdStr, codes.size());
        for (String code : codes) {
            java.util.UUID id = java.util.UUID.nameUUIDFromBytes(code.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            if (id.equals(targetId)) {
                logger.debug("Resolved relationship code from UUID. relationshipIdentifier={}, relationshipCode={}",
                        relationshipIdStr, code);
                return code;
            }
        }
        logger.warn("Relationship code could not be resolved from UUID. relationshipIdentifier={}", relationshipIdStr);
        return null;
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
