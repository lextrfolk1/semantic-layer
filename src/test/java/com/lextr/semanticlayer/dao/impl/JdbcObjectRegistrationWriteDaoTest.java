package com.lextr.semanticlayer.dao.impl;

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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.jdbc.datasource.AbstractDataSource;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JdbcObjectRegistrationWriteDaoTest {

    @Test
    void bindsObjectInsertParametersAndMapsReturnedColumns() {
        UUID objectId = UUID.fromString("00000000-0000-0000-0000-000000000101");
        UUID connectionId = UUID.fromString("00000000-0000-0000-0000-000000000201");
        RecordingNamedParameterJdbcTemplate jdbcTemplate = new RecordingNamedParameterJdbcTemplate(List.of(objectRow(objectId, connectionId)));
        JdbcObjectRegistrationWriteDao dao = new JdbcObjectRegistrationWriteDao(providerOf(jdbcTemplate), new SQLQueryLoaderUtil(new DefaultResourceLoader()));
        ObjectCatalogWriteRequest request = new ObjectCatalogWriteRequest(
                objectId,
                "client-a",
                "GL_BALANCE",
                "GL Balance",
                "TABLE",
                "meta",
                connectionId,
                OffsetDateTime.parse("2026-06-17T10:15:30+05:30"),
                "producer",
                OffsetDateTime.parse("2026-06-17T10:15:30+05:30"),
                "producer"
        );

        ObjectCatalogRecord result = dao.insertDraftObject(request);

        assertTrue(jdbcTemplate.recordedSql.contains("INSERT INTO meta.object_catalog"));
        assertTrue(jdbcTemplate.recordedSql.contains("RETURNING object_id, client_id, object_cd"));
        assertTrue(jdbcTemplate.recordedSql.contains("'DRAFT'"));
        assertEquals("client-a", jdbcTemplate.recordedParameters.get("client_id"));
        assertEquals("GL_BALANCE", jdbcTemplate.recordedParameters.get("object_cd"));
        assertEquals(objectId, result.object_id());
        assertEquals("DRAFT", result.lifecycle_status_cd());
        assertEquals("GL Balance", result.object_nm());
    }

    @Test
    void bindsAttributeInsertParametersAndMapsTaxonomyColumns() {
        UUID attributeId = UUID.fromString("00000000-0000-0000-0000-000000000102");
        UUID objectId = UUID.fromString("00000000-0000-0000-0000-000000000101");
        RecordingNamedParameterJdbcTemplate jdbcTemplate = new RecordingNamedParameterJdbcTemplate(List.of(attributeRow(attributeId, objectId)));
        JdbcObjectRegistrationWriteDao dao = new JdbcObjectRegistrationWriteDao(providerOf(jdbcTemplate), new SQLQueryLoaderUtil(new DefaultResourceLoader()));
        AttributeCatalogWriteRequest request = new AttributeCatalogWriteRequest(
                attributeId,
                objectId,
                "client-a",
                "AMOUNT",
                "Amount",
                "DECIMAL",
                "MDRM12345678",
                "MDRM",
                "US",
                true,
                false,
                false,
                OffsetDateTime.parse("2026-06-17T10:15:30+05:30"),
                "producer",
                OffsetDateTime.parse("2026-06-17T10:15:30+05:30"),
                "producer"
        );

        AttributeCatalogRecord result = dao.insertAttribute(request);

        assertTrue(jdbcTemplate.recordedSql.contains("INSERT INTO meta.attribute_catalog"));
        assertTrue(jdbcTemplate.recordedSql.contains("taxonomy_jurisdiction_cd"));
        assertTrue(jdbcTemplate.recordedSql.contains("pk_flg"));
        assertTrue(jdbcTemplate.recordedSql.contains("fk_flg"));
        assertTrue(jdbcTemplate.recordedSql.contains("nullable_flg"));
        assertEquals("MDRM12345678", jdbcTemplate.recordedParameters.get("taxonomy_cd"));
        assertEquals(true, jdbcTemplate.recordedParameters.get("pk_flg"));
        assertEquals(false, jdbcTemplate.recordedParameters.get("fk_flg"));
        assertEquals(false, jdbcTemplate.recordedParameters.get("nullable_flg"));
        assertEquals("MDRM", jdbcTemplate.recordedParameters.get("taxonomy_source_cd"));
        assertEquals("US", result.taxonomy_jurisdiction_cd());
        assertEquals("AMOUNT", result.attribute_cd());
        assertTrue(result.pk_flg());
        assertEquals(false, result.fk_flg());
        assertEquals(false, result.nullable_flg());
    }

    @Test
    void bindsWorkflowTaskParametersAndMapsReturnedColumns() {
        UUID workflowTaskId = UUID.fromString("00000000-0000-0000-0000-000000000301");
        UUID entityId = UUID.fromString("00000000-0000-0000-0000-000000000101");
        RecordingNamedParameterJdbcTemplate jdbcTemplate = new RecordingNamedParameterJdbcTemplate(List.of(workflowTaskRow(workflowTaskId, entityId)));
        JdbcObjectRegistrationWriteDao dao = new JdbcObjectRegistrationWriteDao(providerOf(jdbcTemplate), new SQLQueryLoaderUtil(new DefaultResourceLoader()));
        WorkflowTaskWriteRequest request = new WorkflowTaskWriteRequest(
                workflowTaskId,
                "client-a",
                "OBJECT_REGISTRATION",
                "OBJECT",
                entityId,
                "PENDING_APPROVAL",
                OffsetDateTime.parse("2026-06-17T10:15:30+05:30"),
                "producer",
                OffsetDateTime.parse("2026-06-17T10:15:30+05:30"),
                "producer"
        );

        WorkflowTaskRecord result = dao.insertWorkflowTask(request);

        assertTrue(jdbcTemplate.recordedSql.contains("INSERT INTO wkfl.workflow_task"));
        assertEquals("OBJECT_REGISTRATION", jdbcTemplate.recordedParameters.get("workflow_type_cd"));
        assertEquals("PENDING_APPROVAL", result.task_status_cd());
        assertEquals(entityId, result.entity_id());
    }

    @Test
    void bindsMetadataChangeHistoryParametersAndMapsReturnedColumns() {
        UUID changeHistoryId = UUID.fromString("00000000-0000-0000-0000-000000000401");
        UUID entityId = UUID.fromString("00000000-0000-0000-0000-000000000101");
        RecordingNamedParameterJdbcTemplate jdbcTemplate = new RecordingNamedParameterJdbcTemplate(List.of(metadataChangeRow(changeHistoryId, entityId)));
        JdbcObjectRegistrationWriteDao dao = new JdbcObjectRegistrationWriteDao(providerOf(jdbcTemplate), new SQLQueryLoaderUtil(new DefaultResourceLoader()));
        MetadataChangeHistoryWriteRequest request = new MetadataChangeHistoryWriteRequest(
                changeHistoryId,
                "client-a",
                "OBJECT",
                entityId,
                "REGISTERED",
                "Registered draft object",
                OffsetDateTime.parse("2026-06-17T10:15:30+05:30"),
                "producer"
        );

        MetadataChangeHistoryRecord result = dao.insertMetadataChangeHistory(request);

        assertTrue(jdbcTemplate.recordedSql.contains("INSERT INTO meta.metadata_change_history"));
        assertEquals("REGISTERED", jdbcTemplate.recordedParameters.get("change_type_cd"));
        assertEquals("Registered draft object", result.change_summary_txt());
        assertEquals(changeHistoryId, result.change_history_id());
    }

    @Test
    void updatesObjectClassificationAndMapsReturnedColumns() {
        UUID objectId = UUID.fromString("00000000-0000-0000-0000-000000000101");
        UUID connectionId = UUID.fromString("00000000-0000-0000-0000-000000000201");
        RecordingNamedParameterJdbcTemplate jdbcTemplate = new RecordingNamedParameterJdbcTemplate(List.of(objectClassificationRow(objectId, connectionId)));
        JdbcObjectRegistrationWriteDao dao = new JdbcObjectRegistrationWriteDao(providerOf(jdbcTemplate), new SQLQueryLoaderUtil(new DefaultResourceLoader()));
        ObjectClassificationWriteRequest request = new ObjectClassificationWriteRequest(
                objectId,
                "client-a",
                "CONFIDENTIAL",
                true,
                true,
                OffsetDateTime.parse("2026-06-18T10:15:30+05:30"),
                "governance-bot"
        );

        ObjectClassificationRecord result = dao.updateObjectClassification(request);

        assertTrue(jdbcTemplate.recordedSql.contains("UPDATE meta.object_catalog"));
        assertTrue(jdbcTemplate.recordedSql.contains("client_id = :client_id"));
        assertEquals("CONFIDENTIAL", jdbcTemplate.recordedParameters.get("data_classification_cd"));
        assertEquals(true, jdbcTemplate.recordedParameters.get("pii_flg"));
        assertEquals(objectId, jdbcTemplate.recordedParameters.get("object_id"));
        assertEquals("CONFIDENTIAL", result.data_classification_cd());
        assertTrue(result.pii_flg());
        assertTrue(result.confidential_flg());
    }

    @Test
    void updatesAttributeClassificationAndMapsReturnedColumns() {
        UUID attributeId = UUID.fromString("00000000-0000-0000-0000-000000000102");
        UUID objectId = UUID.fromString("00000000-0000-0000-0000-000000000101");
        RecordingNamedParameterJdbcTemplate jdbcTemplate = new RecordingNamedParameterJdbcTemplate(List.of(attributeClassificationRow(attributeId, objectId)));
        JdbcObjectRegistrationWriteDao dao = new JdbcObjectRegistrationWriteDao(providerOf(jdbcTemplate), new SQLQueryLoaderUtil(new DefaultResourceLoader()));
        AttributeClassificationWriteRequest request = new AttributeClassificationWriteRequest(
                objectId,
                "client-a",
                "AMOUNT",
                "RESTRICTED",
                true,
                true,
                "MASK_FULL",
                false,
                true,
                "RESTRICTED",
                OffsetDateTime.parse("2026-06-18T10:15:30+05:30"),
                "governance-bot"
        );

        AttributeClassificationRecord result = dao.updateAttributeClassification(request);

        assertTrue(jdbcTemplate.recordedSql.contains("UPDATE meta.attribute_catalog a"));
        assertTrue(jdbcTemplate.recordedSql.contains("masking_policy_cd = :masking_policy_cd"));
        assertEquals("AMOUNT", jdbcTemplate.recordedParameters.get("attribute_cd"));
        assertEquals("MASK_FULL", jdbcTemplate.recordedParameters.get("masking_policy_cd"));
        assertEquals("RESTRICTED", result.data_classification_cd());
        assertEquals("MASK_FULL", result.masking_policy_cd());
        assertTrue(result.csi_flg());
    }

    @Test
    void insertsAttributeAccessGrantAndMapsReturnedColumns() {
        RecordingNamedParameterJdbcTemplate jdbcTemplate = new RecordingNamedParameterJdbcTemplate(List.of(attributeAccessGrantRow(10L, "ACTIVE")));
        JdbcObjectRegistrationWriteDao dao = new JdbcObjectRegistrationWriteDao(providerOf(jdbcTemplate), new SQLQueryLoaderUtil(new DefaultResourceLoader()));
        AttributeAccessGrantWriteRequest request = new AttributeAccessGrantWriteRequest(
                "client-a",
                "meta",
                "GL_BALANCE",
                "AMOUNT",
                "FINANCE",
                "REPORTING",
                "READ",
                "ACTIVE",
                "approver",
                OffsetDateTime.parse("2026-06-18T12:00:00Z"),
                OffsetDateTime.parse("2026-06-18T11:00:00Z"),
                "producer",
                OffsetDateTime.parse("2026-06-18T11:30:00Z"),
                "producer"
        );

        AttributeAccessGrantRecord result = dao.insertAttributeAccessGrant(request);

        assertTrue(jdbcTemplate.recordedSql.contains("INSERT INTO meta.attribute_access_grant"));
        assertEquals("FINANCE", jdbcTemplate.recordedParameters.get("role_cd"));
        assertEquals("REPORTING", jdbcTemplate.recordedParameters.get("purpose_cd"));
        assertEquals("READ", result.grant_scope_cd());
        assertEquals("ACTIVE", result.grant_status_cd());
    }

    @Test
    void updatesAttributeAccessGrantStatusAndMapsReturnedColumns() {
        RecordingNamedParameterJdbcTemplate jdbcTemplate = new RecordingNamedParameterJdbcTemplate(List.of(attributeAccessGrantRow(10L, "REVOKED")));
        JdbcObjectRegistrationWriteDao dao = new JdbcObjectRegistrationWriteDao(providerOf(jdbcTemplate), new SQLQueryLoaderUtil(new DefaultResourceLoader()));
        AttributeAccessGrantStatusUpdateRequest request = new AttributeAccessGrantStatusUpdateRequest(
                10L,
                "client-a",
                "REVOKED",
                "approver",
                OffsetDateTime.parse("2026-06-18T12:00:00Z"),
                OffsetDateTime.parse("2026-06-18T12:05:00Z"),
                "approver"
        );

        AttributeAccessGrantRecord result = dao.updateAttributeAccessGrantStatus(request);

        assertTrue(jdbcTemplate.recordedSql.contains("UPDATE meta.attribute_access_grant"));
        assertTrue(jdbcTemplate.recordedSql.contains("WHERE client_id = :client_id AND id = :id"));
        assertEquals(10L, jdbcTemplate.recordedParameters.get("id"));
        assertEquals("REVOKED", jdbcTemplate.recordedParameters.get("grant_status_cd"));
        assertEquals("REVOKED", result.grant_status_cd());
    }

    @Test
    void transactionRollsBackObjectRegistrationWritesOnFailure() {
        TransactionHarness harness = new TransactionHarness();
        TransactionalNamedParameterJdbcTemplate jdbcTemplate = new TransactionalNamedParameterJdbcTemplate(harness);
        JdbcObjectRegistrationWriteDao dao = new JdbcObjectRegistrationWriteDao(providerOf(jdbcTemplate), new SQLQueryLoaderUtil(new DefaultResourceLoader()));
        RecordingTransactionOperations transactionOperations = new RecordingTransactionOperations(harness);
        UUID objectId = UUID.fromString("00000000-0000-0000-0000-000000000101");
        UUID attributeId = UUID.fromString("00000000-0000-0000-0000-000000000102");
        UUID workflowTaskId = UUID.fromString("00000000-0000-0000-0000-000000000301");
        UUID changeHistoryId = UUID.fromString("00000000-0000-0000-0000-000000000401");

        assertThrows(IllegalStateException.class, () -> transactionOperations.execute(status -> {
            dao.insertDraftObject(objectRequest(objectId));
            dao.insertAttribute(attributeRequest(attributeId, objectId));
            dao.insertWorkflowTask(workflowTaskRequest(workflowTaskId, objectId));
            dao.insertMetadataChangeHistory(metadataChangeRequest(changeHistoryId, objectId));
            throw new IllegalStateException("object registration transaction failed");
        }));

        assertTrue(harness.rolledBack);
        assertFalse(harness.committed);
        assertTrue(jdbcTemplate.committedObjects.isEmpty());
        assertTrue(jdbcTemplate.committedAttributes.isEmpty());
        assertTrue(jdbcTemplate.committedWorkflowTasks.isEmpty());
        assertTrue(jdbcTemplate.committedMetadataChanges.isEmpty());
        assertEquals(4, jdbcTemplate.recordedSqls.size());
        assertTrue(jdbcTemplate.recordedSqls.get(0).contains("INSERT INTO meta.object_catalog"));
        assertTrue(jdbcTemplate.recordedSqls.get(1).contains("INSERT INTO meta.attribute_catalog"));
        assertTrue(jdbcTemplate.recordedSqls.get(2).contains("INSERT INTO wkfl.workflow_task"));
        assertTrue(jdbcTemplate.recordedSqls.get(3).contains("INSERT INTO meta.metadata_change_history"));
    }

    @Test
    void transactionRollsBackClassificationAndGrantWritesOnFailure() {
        TransactionHarness harness = new TransactionHarness();
        TransactionalNamedParameterJdbcTemplate jdbcTemplate = new TransactionalNamedParameterJdbcTemplate(harness);
        JdbcObjectRegistrationWriteDao dao = new JdbcObjectRegistrationWriteDao(providerOf(jdbcTemplate), new SQLQueryLoaderUtil(new DefaultResourceLoader()));
        RecordingTransactionOperations transactionOperations = new RecordingTransactionOperations(harness);
        UUID objectId = UUID.fromString("00000000-0000-0000-0000-000000000101");
        UUID attributeId = UUID.fromString("00000000-0000-0000-0000-000000000102");

        assertThrows(IllegalStateException.class, () -> transactionOperations.execute(status -> {
            dao.updateObjectClassification(objectClassificationRequest(objectId));
            dao.updateAttributeClassification(attributeClassificationRequest(objectId));
            dao.insertAttributeAccessGrant(attributeAccessGrantRequest("ACTIVE"));
            dao.updateAttributeAccessGrantStatus(attributeAccessGrantStatusUpdateRequest(10L, "REVOKED"));
            throw new IllegalStateException("classification and grant transaction failed");
        }));

        assertTrue(harness.rolledBack);
        assertFalse(harness.committed);
        assertTrue(jdbcTemplate.committedObjectClassifications.isEmpty());
        assertTrue(jdbcTemplate.committedAttributeClassifications.isEmpty());
        assertTrue(jdbcTemplate.committedAttributeAccessGrants.isEmpty());
        assertEquals(4, jdbcTemplate.recordedSqls.size());
        assertTrue(jdbcTemplate.recordedSqls.get(0).contains("UPDATE meta.object_catalog"));
        assertTrue(jdbcTemplate.recordedSqls.get(1).contains("UPDATE meta.attribute_catalog a"));
        assertTrue(jdbcTemplate.recordedSqls.get(2).contains("INSERT INTO meta.attribute_access_grant"));
        assertTrue(jdbcTemplate.recordedSqls.get(3).contains("UPDATE meta.attribute_access_grant"));
    }

    @Test
    void failsWhenNamedParameterJdbcTemplateMissing() {
        JdbcObjectRegistrationWriteDao dao = new JdbcObjectRegistrationWriteDao(providerOf(null), new SQLQueryLoaderUtil(new DefaultResourceLoader()));
        ObjectCatalogWriteRequest request = objectRequest(UUID.fromString("00000000-0000-0000-0000-000000000101"));

        assertThrows(SemanticLayerException.class, () -> dao.insertDraftObject(request));
    }

    @Test
    void usesNamedParameterJdbcTemplateAndDoesNotUseJpa() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/lextr/semanticlayer/dao/impl/JdbcObjectRegistrationWriteDao.java"
        ));

        assertTrue(source.contains("NamedParameterJdbcTemplate"));
        assertTrue(!source.contains("EntityManager"));
        assertTrue(!source.contains("JpaRepository"));
        assertTrue(!source.contains("jakarta.persistence"));
        assertTrue(!source.contains("javax.persistence"));
    }

    private static ObjectProvider<NamedParameterJdbcTemplate> providerOf(NamedParameterJdbcTemplate jdbcTemplate) {
        return new ObjectProvider<>() {
            @Override
            public NamedParameterJdbcTemplate getObject(Object... args) {
                return jdbcTemplate;
            }

            @Override
            public NamedParameterJdbcTemplate getIfAvailable() {
                return jdbcTemplate;
            }

            @Override
            public NamedParameterJdbcTemplate getIfUnique() {
                return jdbcTemplate;
            }

            @Override
            public NamedParameterJdbcTemplate getObject() {
                return jdbcTemplate;
            }

            @Override
            public Iterator<NamedParameterJdbcTemplate> iterator() {
                return jdbcTemplate == null ? Collections.emptyIterator() : List.of(jdbcTemplate).iterator();
            }
        };
    }

    private static ObjectCatalogWriteRequest objectRequest(UUID objectId) {
        return new ObjectCatalogWriteRequest(
                objectId,
                "client-a",
                "GL_BALANCE",
                "GL Balance",
                "TABLE",
                "meta",
                UUID.fromString("00000000-0000-0000-0000-000000000201"),
                OffsetDateTime.parse("2026-06-17T10:15:30+05:30"),
                "producer",
                OffsetDateTime.parse("2026-06-17T10:15:30+05:30"),
                "producer"
        );
    }

    private static AttributeCatalogWriteRequest attributeRequest(UUID attributeId, UUID objectId) {
        return new AttributeCatalogWriteRequest(
                attributeId,
                objectId,
                "client-a",
                "AMOUNT",
                "Amount",
                "DECIMAL",
                "MDRM12345678",
                "MDRM",
                "US",
                true,
                false,
                false,
                OffsetDateTime.parse("2026-06-17T10:15:30+05:30"),
                "producer",
                OffsetDateTime.parse("2026-06-17T10:15:30+05:30"),
                "producer"
        );
    }

    private static WorkflowTaskWriteRequest workflowTaskRequest(UUID workflowTaskId, UUID entityId) {
        return new WorkflowTaskWriteRequest(
                workflowTaskId,
                "client-a",
                "OBJECT_REGISTRATION",
                "OBJECT",
                entityId,
                "PENDING_APPROVAL",
                OffsetDateTime.parse("2026-06-17T10:15:30+05:30"),
                "producer",
                OffsetDateTime.parse("2026-06-17T10:15:30+05:30"),
                "producer"
        );
    }

    private static MetadataChangeHistoryWriteRequest metadataChangeRequest(UUID changeHistoryId, UUID entityId) {
        return new MetadataChangeHistoryWriteRequest(
                changeHistoryId,
                "client-a",
                "OBJECT",
                entityId,
                "REGISTERED",
                "Registered draft object",
                OffsetDateTime.parse("2026-06-17T10:15:30+05:30"),
                "producer"
        );
    }

    private static ObjectClassificationWriteRequest objectClassificationRequest(UUID objectId) {
        return new ObjectClassificationWriteRequest(
                objectId,
                "client-a",
                "CONFIDENTIAL",
                true,
                true,
                OffsetDateTime.parse("2026-06-18T10:15:30+05:30"),
                "governance-bot"
        );
    }

    private static AttributeClassificationWriteRequest attributeClassificationRequest(UUID objectId) {
        return new AttributeClassificationWriteRequest(
                objectId,
                "client-a",
                "AMOUNT",
                "RESTRICTED",
                true,
                true,
                "MASK_FULL",
                false,
                true,
                "RESTRICTED",
                OffsetDateTime.parse("2026-06-18T10:15:30+05:30"),
                "governance-bot"
        );
    }

    private static AttributeAccessGrantWriteRequest attributeAccessGrantRequest(String grantStatus) {
        return new AttributeAccessGrantWriteRequest(
                "client-a",
                "meta",
                "GL_BALANCE",
                "AMOUNT",
                "FINANCE",
                "REPORTING",
                "READ",
                grantStatus,
                "approver",
                OffsetDateTime.parse("2026-06-18T12:00:00Z"),
                OffsetDateTime.parse("2026-06-18T11:00:00Z"),
                "producer",
                OffsetDateTime.parse("2026-06-18T11:30:00Z"),
                "producer"
        );
    }

    private static AttributeAccessGrantStatusUpdateRequest attributeAccessGrantStatusUpdateRequest(Long id, String grantStatus) {
        return new AttributeAccessGrantStatusUpdateRequest(
                id,
                "client-a",
                grantStatus,
                "approver",
                OffsetDateTime.parse("2026-06-18T12:00:00Z"),
                OffsetDateTime.parse("2026-06-18T12:05:00Z"),
                "approver"
        );
    }

    private static Map<String, Object> objectRow(UUID objectId, UUID connectionId) {
        Map<String, Object> row = new HashMap<>();
        row.put("object_id", objectId);
        row.put("client_id", "client-a");
        row.put("object_cd", "GL_BALANCE");
        row.put("object_nm", "GL Balance");
        row.put("object_type_cd", "TABLE");
        row.put("schema_cd", "meta");
        row.put("connection_id", connectionId);
        row.put("lifecycle_status_cd", "DRAFT");
        row.put("created_ts", OffsetDateTime.parse("2026-06-17T10:15:30+05:30"));
        row.put("created_by", "producer");
        row.put("updated_ts", OffsetDateTime.parse("2026-06-17T10:15:30+05:30"));
        row.put("updated_by", "producer");
        return row;
    }

    private static Map<String, Object> attributeRow(UUID attributeId, UUID objectId) {
        Map<String, Object> row = new HashMap<>();
        row.put("attribute_id", attributeId);
        row.put("object_id", objectId);
        row.put("client_id", "client-a");
        row.put("attribute_cd", "AMOUNT");
        row.put("attribute_nm", "Amount");
        row.put("data_type_cd", "DECIMAL");
        row.put("taxonomy_cd", "MDRM12345678");
        row.put("taxonomy_source_cd", "MDRM");
        row.put("taxonomy_jurisdiction_cd", "US");
        row.put("pk_flg", true);
        row.put("fk_flg", false);
        row.put("nullable_flg", false);
        row.put("created_ts", OffsetDateTime.parse("2026-06-17T10:15:30+05:30"));
        row.put("created_by", "producer");
        row.put("updated_ts", OffsetDateTime.parse("2026-06-17T10:15:30+05:30"));
        row.put("updated_by", "producer");
        return row;
    }

    private static Map<String, Object> workflowTaskRow(UUID workflowTaskId, UUID entityId) {
        Map<String, Object> row = new HashMap<>();
        row.put("workflow_task_id", workflowTaskId);
        row.put("client_id", "client-a");
        row.put("workflow_type_cd", "OBJECT_REGISTRATION");
        row.put("entity_type_cd", "OBJECT");
        row.put("entity_id", entityId);
        row.put("task_status_cd", "PENDING_APPROVAL");
        row.put("created_ts", OffsetDateTime.parse("2026-06-17T10:15:30+05:30"));
        row.put("created_by", "producer");
        row.put("updated_ts", OffsetDateTime.parse("2026-06-17T10:15:30+05:30"));
        row.put("updated_by", "producer");
        return row;
    }

    private static Map<String, Object> metadataChangeRow(UUID changeHistoryId, UUID entityId) {
        Map<String, Object> row = new HashMap<>();
        row.put("change_history_id", changeHistoryId);
        row.put("client_id", "client-a");
        row.put("entity_type_cd", "OBJECT");
        row.put("entity_id", entityId);
        row.put("change_type_cd", "REGISTERED");
        row.put("change_summary_txt", "Registered draft object");
        row.put("created_ts", OffsetDateTime.parse("2026-06-17T10:15:30+05:30"));
        row.put("created_by", "producer");
        return row;
    }

    private static Map<String, Object> objectClassificationRow(UUID objectId, UUID connectionId) {
        Map<String, Object> row = objectRow(objectId, connectionId);
        row.put("data_classification_cd", "CONFIDENTIAL");
        row.put("pii_flg", true);
        row.put("confidential_flg", true);
        row.put("updated_by", "governance-bot");
        return row;
    }

    private static Map<String, Object> attributeClassificationRow(UUID attributeId, UUID objectId) {
        Map<String, Object> row = attributeRow(attributeId, objectId);
        row.put("data_classification_cd", "RESTRICTED");
        row.put("pii_flg", true);
        row.put("confidential_flg", true);
        row.put("masking_policy_cd", "MASK_FULL");
        row.put("mnpi_flg", false);
        row.put("csi_flg", true);
        row.put("ai_exposure_cd", "RESTRICTED");
        row.put("updated_by", "governance-bot");
        return row;
    }

    private static Map<String, Object> attributeAccessGrantRow(Long id, String grantStatus) {
        Map<String, Object> row = new HashMap<>();
        row.put("id", id);
        row.put("client_id", "client-a");
        row.put("schema_cd", "meta");
        row.put("object_cd", "GL_BALANCE");
        row.put("attribute_cd", "AMOUNT");
        row.put("role_cd", "FINANCE");
        row.put("purpose_cd", "REPORTING");
        row.put("grant_scope_cd", "READ");
        row.put("grant_status_cd", grantStatus);
        row.put("approved_by", "approver");
        row.put("approved_ts", OffsetDateTime.parse("2026-06-18T12:00:00Z"));
        row.put("created_ts", OffsetDateTime.parse("2026-06-18T11:00:00Z"));
        row.put("created_by", "producer");
        row.put("updated_ts", OffsetDateTime.parse("2026-06-18T12:05:00Z"));
        row.put("updated_by", "approver");
        return row;
    }

    private static final class RecordingNamedParameterJdbcTemplate extends NamedParameterJdbcTemplate {

        private final List<Map<String, Object>> rows;
        private String recordedSql;
        private Map<String, Object> recordedParameters = Map.of();

        private RecordingNamedParameterJdbcTemplate(List<Map<String, Object>> rows) {
            super(noOpDataSource());
            this.rows = rows;
        }

        @Override
        public <T> List<T> query(String sql, SqlParameterSource paramSource, RowMapper<T> rowMapper) {
            this.recordedSql = sql;
            if (paramSource instanceof MapSqlParameterSource source) {
                this.recordedParameters = source.getValues();
            }
            return rows.stream().map(row -> mapRow(rowMapper, row)).toList();
        }

        private <T> T mapRow(RowMapper<T> rowMapper, Map<String, Object> row) {
            try {
                return rowMapper.mapRow(resultSet(row), 0);
            } catch (SQLException exception) {
                throw new RuntimeException(exception);
            }
        }

        private ResultSet resultSet(Map<String, Object> row) {
            return (ResultSet) Proxy.newProxyInstance(
                    ResultSet.class.getClassLoader(),
                    new Class[]{ResultSet.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "getString" -> (String) row.get(args[0]);
                        case "getBoolean" -> {
                            Object value = row.get(args[0]);
                            yield value != null && (Boolean) value;
                        }
                        case "getObject" -> {
                            if (args.length == 1) {
                                yield row.get(args[0]);
                            }
                            Object value = row.get(args[0]);
                            yield value == null ? null : ((Class<?>) args[1]).cast(value);
                        }
                        case "close" -> null;
                        case "wasNull" -> false;
                        default -> defaultValue(method.getReturnType());
                    }
            );
        }

        private Object defaultValue(Class<?> returnType) {
            if (!returnType.isPrimitive()) {
                return null;
            }
            if (returnType == boolean.class) {
                return false;
            }
            if (returnType == byte.class) {
                return (byte) 0;
            }
            if (returnType == short.class) {
                return (short) 0;
            }
            if (returnType == int.class) {
                return 0;
            }
            if (returnType == long.class) {
                return 0L;
            }
            if (returnType == float.class) {
                return 0F;
            }
            if (returnType == double.class) {
                return 0D;
            }
            if (returnType == char.class) {
                return '\0';
            }
            return null;
        }

        private static DataSource noOpDataSource() {
            return new AbstractDataSource() {
                @Override
                public Connection getConnection() {
                    throw new UnsupportedOperationException("Not used in tests");
                }

                @Override
                public Connection getConnection(String username, String password) {
                    throw new UnsupportedOperationException("Not used in tests");
                }
            };
        }
    }

    private static final class TransactionalNamedParameterJdbcTemplate extends NamedParameterJdbcTemplate {

        private final TransactionHarness harness;
        private final Map<UUID, Map<String, Object>> committedObjects = new LinkedHashMap<>();
        private final Map<UUID, Map<String, Object>> committedAttributes = new LinkedHashMap<>();
        private final Map<UUID, Map<String, Object>> committedWorkflowTasks = new LinkedHashMap<>();
        private final Map<UUID, Map<String, Object>> committedMetadataChanges = new LinkedHashMap<>();
        private final Map<UUID, Map<String, Object>> committedObjectClassifications = new LinkedHashMap<>();
        private final Map<UUID, Map<String, Object>> committedAttributeClassifications = new LinkedHashMap<>();
        private final Map<Long, Map<String, Object>> committedAttributeAccessGrants = new LinkedHashMap<>();
        private final List<String> recordedSqls = new ArrayList<>();

        private TransactionalNamedParameterJdbcTemplate(TransactionHarness harness) {
            super(RecordingNamedParameterJdbcTemplate.noOpDataSource());
            this.harness = harness;
        }

        @Override
        public <T> List<T> query(String sql, SqlParameterSource paramSource, RowMapper<T> rowMapper) {
            recordedSqls.add(sql);
            Map<String, Object> parameters = paramSource instanceof MapSqlParameterSource source ? source.getValues() : Map.of();
            TransactionState state = harness.transactionState(
                    committedObjects,
                    committedAttributes,
                    committedWorkflowTasks,
                    committedMetadataChanges,
                    committedObjectClassifications,
                    committedAttributeClassifications,
                    committedAttributeAccessGrants
            );
            Map<String, Object> row;
            if (sql.startsWith("INSERT INTO meta.object_catalog")) {
                row = objectRow((UUID) parameters.get("object_id"), (UUID) parameters.get("connection_id"));
                state.objectRows.put((UUID) parameters.get("object_id"), row);
            } else if (sql.startsWith("INSERT INTO meta.attribute_catalog")) {
                row = attributeRow((UUID) parameters.get("attribute_id"), (UUID) parameters.get("object_id"));
                state.attributeRows.put((UUID) parameters.get("attribute_id"), row);
            } else if (sql.startsWith("INSERT INTO wkfl.workflow_task")) {
                row = workflowTaskRow((UUID) parameters.get("workflow_task_id"), (UUID) parameters.get("entity_id"));
                state.workflowRows.put((UUID) parameters.get("workflow_task_id"), row);
            } else if (sql.startsWith("INSERT INTO meta.metadata_change_history")) {
                row = metadataChangeRow((UUID) parameters.get("change_history_id"), (UUID) parameters.get("entity_id"));
                state.metadataRows.put((UUID) parameters.get("change_history_id"), row);
            } else if (sql.startsWith("UPDATE meta.object_catalog")) {
                row = objectClassificationRow((UUID) parameters.get("object_id"), UUID.fromString("00000000-0000-0000-0000-000000000201"));
                state.objectClassificationRows.put((UUID) parameters.get("object_id"), row);
            } else if (sql.startsWith("UPDATE meta.attribute_catalog")) {
                row = attributeClassificationRow(UUID.fromString("00000000-0000-0000-0000-000000000102"), (UUID) parameters.get("object_id"));
                state.attributeClassificationRows.put(UUID.fromString("00000000-0000-0000-0000-000000000102"), row);
            } else if (sql.startsWith("INSERT INTO meta.attribute_access_grant")) {
                row = attributeAccessGrantRow(10L, (String) parameters.get("grant_status_cd"));
                state.attributeAccessGrantRows.put(10L, row);
            } else if (sql.startsWith("UPDATE meta.attribute_access_grant")) {
                row = attributeAccessGrantRow((Long) parameters.get("id"), (String) parameters.get("grant_status_cd"));
                state.attributeAccessGrantRows.put((Long) parameters.get("id"), row);
            } else {
                throw new IllegalArgumentException("Unexpected SQL: " + sql);
            }
            return List.of(mapRow(rowMapper, row));
        }

        private <T> T mapRow(RowMapper<T> rowMapper, Map<String, Object> row) {
            try {
                return rowMapper.mapRow(recordingResultSet(row), 0);
            } catch (SQLException exception) {
                throw new RuntimeException(exception);
            }
        }

        private ResultSet recordingResultSet(Map<String, Object> row) {
            return (ResultSet) Proxy.newProxyInstance(
                    ResultSet.class.getClassLoader(),
                    new Class[]{ResultSet.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "getString" -> (String) row.get(args[0]);
                        case "getBoolean" -> {
                            Object value = row.get(args[0]);
                            yield value != null && (Boolean) value;
                        }
                        case "getObject" -> {
                            if (args.length == 1) {
                                yield row.get(args[0]);
                            }
                            Object value = row.get(args[0]);
                            yield value == null ? null : ((Class<?>) args[1]).cast(value);
                        }
                        case "close" -> null;
                        case "wasNull" -> false;
                        default -> defaultValue(method.getReturnType());
                    }
            );
        }

        private Object defaultValue(Class<?> returnType) {
            if (!returnType.isPrimitive()) {
                return null;
            }
            if (returnType == boolean.class) {
                return false;
            }
            if (returnType == byte.class) {
                return (byte) 0;
            }
            if (returnType == short.class) {
                return (short) 0;
            }
            if (returnType == int.class) {
                return 0;
            }
            if (returnType == long.class) {
                return 0L;
            }
            if (returnType == float.class) {
                return 0F;
            }
            if (returnType == double.class) {
                return 0D;
            }
            if (returnType == char.class) {
                return '\0';
            }
            return null;
        }
    }

    private static final class TransactionHarness {

        private TransactionState state;
        private boolean committed;
        private boolean rolledBack;

        private TransactionState transactionState(Map<UUID, Map<String, Object>> committedObjects,
                                                  Map<UUID, Map<String, Object>> committedAttributes,
                                                  Map<UUID, Map<String, Object>> committedWorkflowTasks,
                                                  Map<UUID, Map<String, Object>> committedMetadataChanges,
                                                  Map<UUID, Map<String, Object>> committedObjectClassifications,
                                                  Map<UUID, Map<String, Object>> committedAttributeClassifications,
                                                  Map<Long, Map<String, Object>> committedAttributeAccessGrants) {
            if (state == null) {
                state = new TransactionState(
                        new LinkedHashMap<>(committedObjects),
                        new LinkedHashMap<>(committedAttributes),
                        new LinkedHashMap<>(committedWorkflowTasks),
                        new LinkedHashMap<>(committedMetadataChanges),
                        new LinkedHashMap<>(committedObjectClassifications),
                        new LinkedHashMap<>(committedAttributeClassifications),
                        new LinkedHashMap<>(committedAttributeAccessGrants)
                );
            }
            return state;
        }
    }

    private record TransactionState(Map<UUID, Map<String, Object>> objectRows,
                                    Map<UUID, Map<String, Object>> attributeRows,
                                    Map<UUID, Map<String, Object>> workflowRows,
                                    Map<UUID, Map<String, Object>> metadataRows,
                                    Map<UUID, Map<String, Object>> objectClassificationRows,
                                    Map<UUID, Map<String, Object>> attributeClassificationRows,
                                    Map<Long, Map<String, Object>> attributeAccessGrantRows) {
    }

    private static final class RecordingTransactionOperations implements TransactionOperations {

        private final TransactionHarness harness;

        private RecordingTransactionOperations(TransactionHarness harness) {
            this.harness = harness;
        }

        @Override
        public <T> T execute(TransactionCallback<T> action) {
            try {
                T result = action.doInTransaction(new NoOpTransactionStatus());
                harness.state = null;
                harness.committed = true;
                return result;
            } catch (RuntimeException exception) {
                harness.state = null;
                harness.rolledBack = true;
                throw exception;
            }
        }
    }

    private static final class NoOpTransactionStatus implements TransactionStatus {

        @Override
        public boolean isNewTransaction() {
            return false;
        }

        @Override
        public boolean hasSavepoint() {
            return false;
        }

        @Override
        public void setRollbackOnly() {
        }

        @Override
        public boolean isRollbackOnly() {
            return false;
        }

        @Override
        public void flush() {
        }

        @Override
        public boolean isCompleted() {
            return false;
        }

        @Override
        public Object createSavepoint() {
            return null;
        }

        @Override
        public void rollbackToSavepoint(Object savepoint) {
        }

        @Override
        public void releaseSavepoint(Object savepoint) {
        }
    }
}
