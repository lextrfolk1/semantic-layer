package com.lextr.semanticlayer.util;

import com.lextr.semanticlayer.exception.SemanticLayerException;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SQLQueryLoaderUtilTest {

    @Test
    void loadsRegistryQueriesFromProperties() {
        SQLQueryLoaderUtil loader = new SQLQueryLoaderUtil(new DefaultResourceLoader());

        String schemaQuery = loader.getQuery("schema_registry.find_all");
        String schemaByCodeQuery = loader.getQuery("schema_registry.find_by_code");
        String connectionQuery = loader.getQuery("connection_registry.find_all");
        String connectionByIdQuery = loader.getQuery("connection_registry.find_by_id");
        String insertDraftObjectQuery = loader.getQuery("object_registration.insert_draft_object");
        String insertAttributeQuery = loader.getQuery("object_registration.insert_attribute");
        String insertWorkflowTaskQuery = loader.getQuery("object_registration.insert_workflow_task");
        String insertMetadataChangeHistoryQuery = loader.getQuery("object_registration.insert_metadata_change_history");

        assertTrue(schemaQuery.contains("schema_cd"));
        assertTrue(schemaQuery.contains("client_id"));
        assertTrue(schemaQuery.contains("effective_schema_nm"));
        assertTrue(schemaQuery.contains(":lifecycle_status_cd"));
        assertTrue(schemaByCodeQuery.contains(":schema_cd"));
        assertTrue(connectionQuery.contains("connection_id"));
        assertTrue(connectionQuery.contains("client_id"));
        assertTrue(connectionQuery.contains("effective_connection_nm"));
        assertTrue(connectionQuery.contains(":engine_cd"));
        assertTrue(connectionQuery.contains(":is_active_flg"));
        assertTrue(connectionByIdQuery.contains(":connection_id"));
        assertFalse(connectionQuery.contains("secrets_ref"));
        assertTrue(insertDraftObjectQuery.contains("INSERT INTO meta.object_catalog"));
        assertTrue(insertDraftObjectQuery.contains(":object_cd"));
        assertTrue(insertDraftObjectQuery.contains("lifecycle_status_cd"));
        assertTrue(insertAttributeQuery.contains("INSERT INTO meta.attribute_catalog"));
        assertTrue(insertAttributeQuery.contains(":taxonomy_jurisdiction_cd"));
        assertTrue(insertWorkflowTaskQuery.contains("INSERT INTO wkfl.workflow_task"));
        assertTrue(insertWorkflowTaskQuery.contains(":task_status_cd"));
        assertTrue(insertMetadataChangeHistoryQuery.contains("INSERT INTO meta.metadata_change_history"));
        assertTrue(insertMetadataChangeHistoryQuery.contains(":change_summary_txt"));
    }

    @Test
    void failsWhenQueryKeyMissing() {
        SQLQueryLoaderUtil loader = new SQLQueryLoaderUtil(new DefaultResourceLoader());

        assertThrows(SemanticLayerException.class, () -> loader.getQuery("missing.query"));
    }
}
