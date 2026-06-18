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
        String insertRelationshipQuery = loader.getQuery("relationship_registration.insert_relationship");
        String updateRelationshipProjectionSyncQuery = loader.getQuery("relationship_registration.update_neo4j_projection_sync");
        String insertFilterLookupQuery = loader.getQuery("filter_lookup_registration.insert_lookup");
        String insertFilterLookupWorkflowTaskQuery = loader.getQuery("filter_lookup_registration.insert_workflow_task");
        String governancePolicyPresetByCodeQuery = loader.getQuery("governance_policy_preset.find_by_code");
        String objectListQuery = loader.getQuery("object_exposure.find_all");
        String objectByIdQuery = loader.getQuery("object_exposure.find_by_id");
        String objectBySchemaAndCodeQuery = loader.getQuery("object_exposure.find_by_schema_and_code");
        String attributeByObjectIdQuery = loader.getQuery("object_exposure.find_attributes_by_object_id");

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
        assertTrue(insertRelationshipQuery.contains("INSERT INTO meta.semantic_relationship_catalog"));
        assertTrue(insertRelationshipQuery.contains(":relationship_cd"));
        assertTrue(insertRelationshipQuery.contains(":parent_object_cd"));
        assertTrue(insertRelationshipQuery.contains(":child_attribute_cd"));
        assertTrue(insertRelationshipQuery.contains("is_cross_engine_flg"));
        assertTrue(updateRelationshipProjectionSyncQuery.contains("UPDATE meta.semantic_relationship_catalog"));
        assertTrue(updateRelationshipProjectionSyncQuery.contains(":neo4j_synced_ts"));
        assertTrue(updateRelationshipProjectionSyncQuery.contains("WHERE relationship_cd = :relationship_cd"));
        assertTrue(insertFilterLookupQuery.contains("INSERT INTO meta.semantic_filter_lookup"));
        assertTrue(insertFilterLookupQuery.contains(":lookup_cd"));
        assertTrue(insertFilterLookupQuery.contains(":review_period_days_override"));
        assertTrue(insertFilterLookupQuery.contains("governance_status_cd"));
        assertTrue(insertFilterLookupQuery.contains("next_review_due_dt"));
        assertTrue(insertFilterLookupWorkflowTaskQuery.contains("INSERT INTO wkfl.workflow_task"));
        assertTrue(insertFilterLookupWorkflowTaskQuery.contains(":entity_ref"));
        assertTrue(insertFilterLookupWorkflowTaskQuery.contains(":submitted_ts"));
        assertTrue(governancePolicyPresetByCodeQuery.contains("FROM governance.policy_preset"));
        assertTrue(governancePolicyPresetByCodeQuery.contains(":policy_cd"));
        assertTrue(governancePolicyPresetByCodeQuery.contains(":policy_scope_cd"));
        assertTrue(governancePolicyPresetByCodeQuery.contains(":as_of_dt"));
        assertTrue(objectListQuery.contains("FROM meta.object_catalog"));
        assertTrue(objectListQuery.contains("effective_object_nm"));
        assertTrue(objectListQuery.contains(":schema_cd"));
        assertTrue(objectListQuery.contains(":lifecycle_status_cd"));
        assertTrue(objectByIdQuery.contains(":object_id"));
        assertTrue(objectBySchemaAndCodeQuery.contains(":schema_cd"));
        assertTrue(objectBySchemaAndCodeQuery.contains(":object_cd"));
        assertTrue(objectBySchemaAndCodeQuery.contains("effective_object_nm"));
        assertTrue(attributeByObjectIdQuery.contains("FROM meta.attribute_catalog"));
        assertTrue(attributeByObjectIdQuery.contains("effective_attribute_nm"));
        assertTrue(attributeByObjectIdQuery.contains(":object_id"));
    }

    @Test
    void failsWhenQueryKeyMissing() {
        SQLQueryLoaderUtil loader = new SQLQueryLoaderUtil(new DefaultResourceLoader());

        assertThrows(SemanticLayerException.class, () -> loader.getQuery("missing.query"));
    }
}
