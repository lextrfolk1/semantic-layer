package com.lextr.semanticlayer.pairing;

import com.lextr.semanticlayer.util.SQLQueryLoaderUtil;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AttributePairingQueryAssetsTest {

    @Test
    void loadsAttributePairingRegistrationQueriesFromProperties() {
        SQLQueryLoaderUtil loader = new SQLQueryLoaderUtil(new DefaultResourceLoader());

        String insertPairingQuery = loader.getQuery("attribute_pairing_registration.insert_pairing");
        String insertWorkflowTaskQuery = loader.getQuery("attribute_pairing_registration.insert_workflow_task");
        String insertMetadataChangeHistoryQuery = loader.getQuery("attribute_pairing_registration.insert_metadata_change_history");

        assertTrue(insertPairingQuery.contains("INSERT INTO meta.attribute_pairing_catalog"));
        assertTrue(insertPairingQuery.contains(":pairing_cd"));
        assertTrue(insertPairingQuery.contains(":display_attribute_cd"));
        assertTrue(insertPairingQuery.contains(":filter_attribute_cd"));
        assertTrue(insertPairingQuery.contains(":lookup_strategy_cd"));
        assertTrue(insertPairingQuery.contains("CAST(:lookup_inline_map_jsonb AS jsonb)"));
        assertTrue(insertPairingQuery.contains(":lookup_cache_ttl_seconds_nbr"));
        assertTrue(insertPairingQuery.contains(":is_cross_engine_flg"));
        assertTrue(insertPairingQuery.contains(":filter_attribute_indexed_flg"));
        assertTrue(insertPairingQuery.contains(":governance_review_status_cd"));
        assertTrue(insertPairingQuery.contains(":version_nbr"));
        assertTrue(insertPairingQuery.contains(":created_ts"));
        assertTrue(insertPairingQuery.contains(":updated_by"));
        assertTrue(insertPairingQuery.contains("RETURNING id, pairing_cd, pairing_nm"));
        assertTrue(insertPairingQuery.contains("display_attribute_cd"));
        assertTrue(insertPairingQuery.contains("filter_attribute_cd"));
        assertTrue(insertPairingQuery.contains("lookup_inline_map_jsonb"));
        assertTrue(insertPairingQuery.contains("lookup_cache_ttl_seconds_nbr"));
        assertTrue(insertPairingQuery.contains("governance_review_status_cd"));

        assertTrue(insertWorkflowTaskQuery.contains("INSERT INTO wkfl.workflow_task"));
        assertTrue(insertWorkflowTaskQuery.contains(":task_type_cd"));
        assertTrue(insertWorkflowTaskQuery.contains(":entity_type_cd"));
        assertTrue(insertWorkflowTaskQuery.contains(":entity_ref"));
        assertTrue(insertWorkflowTaskQuery.contains(":task_status_cd"));
        assertTrue(insertWorkflowTaskQuery.contains(":submitted_by"));
        assertTrue(insertWorkflowTaskQuery.contains(":submitted_ts"));
        assertTrue(insertWorkflowTaskQuery.contains(":client_id"));
        assertTrue(insertWorkflowTaskQuery.contains("RETURNING id, task_type_cd, entity_type_cd"));

        assertTrue(insertMetadataChangeHistoryQuery.contains("INSERT INTO meta.metadata_change_history"));
        assertTrue(insertMetadataChangeHistoryQuery.contains(":entity_type_cd"));
        assertTrue(insertMetadataChangeHistoryQuery.contains(":entity_ref"));
        assertTrue(insertMetadataChangeHistoryQuery.contains(":change_type_cd"));
        assertTrue(insertMetadataChangeHistoryQuery.contains(":changed_by"));
        assertTrue(insertMetadataChangeHistoryQuery.contains(":changed_ts"));
        assertTrue(insertMetadataChangeHistoryQuery.contains("CAST(:old_value_json AS jsonb)"));
        assertTrue(insertMetadataChangeHistoryQuery.contains("CAST(:new_value_json AS jsonb)"));
        assertTrue(insertMetadataChangeHistoryQuery.contains("RETURNING id, entity_type_cd, entity_ref"));
    }

    @Test
    void loadsAttributePairingResolutionQueriesFromProperties() {
        SQLQueryLoaderUtil loader = new SQLQueryLoaderUtil(new DefaultResourceLoader());

        String findByCodeQuery = loader.getQuery("attribute_pairing_resolution.find_pairing_by_code");
        String findActiveByDisplayAttributeQuery = loader.getQuery("attribute_pairing_resolution.find_active_pairing_by_display_attribute");
        String checkIndexQuery = loader.getQuery("attribute_pairing_resolution.check_filter_attribute_index");

        assertTrue(findByCodeQuery.contains("FROM meta.attribute_pairing_catalog"));
        assertTrue(findByCodeQuery.contains("pairing_cd = :pairing_cd"));
        assertTrue(findByCodeQuery.contains("(client_id = :client_id OR client_id IS NULL)"));
        assertTrue(findByCodeQuery.contains("display_attribute_cd"));
        assertTrue(findByCodeQuery.contains("filter_attribute_cd"));
        assertTrue(findByCodeQuery.contains("lookup_strategy_cd"));
        assertTrue(findByCodeQuery.contains("lookup_inline_map_jsonb"));
        assertTrue(findByCodeQuery.contains("lifecycle_status_cd"));
        assertTrue(findByCodeQuery.contains("governance_review_status_cd"));
        assertTrue(findByCodeQuery.contains("created_ts"));
        assertTrue(findByCodeQuery.contains("updated_by"));

        assertTrue(findActiveByDisplayAttributeQuery.contains("FROM meta.attribute_pairing_catalog"));
        assertTrue(findActiveByDisplayAttributeQuery.contains("schema_cd = :schema_cd"));
        assertTrue(findActiveByDisplayAttributeQuery.contains("object_cd = :object_cd"));
        assertTrue(findActiveByDisplayAttributeQuery.contains("display_attribute_cd = :display_attribute_cd"));
        assertTrue(findActiveByDisplayAttributeQuery.contains("lifecycle_status_cd = 'ACTIVE'"));
        assertTrue(findActiveByDisplayAttributeQuery.contains("(client_id = :client_id OR client_id IS NULL)"));
        assertTrue(findActiveByDisplayAttributeQuery.contains("ORDER BY CASE WHEN client_id = :client_id THEN 0 ELSE 1 END"));
        assertTrue(findActiveByDisplayAttributeQuery.contains("version_nbr DESC"));
        assertTrue(findActiveByDisplayAttributeQuery.contains("LIMIT 1"));

        assertTrue(checkIndexQuery.contains("FROM pg_indexes"));
        assertTrue(checkIndexQuery.contains("schemaname = :schema_cd"));
        assertTrue(checkIndexQuery.contains("tablename = :object_cd"));
        assertTrue(checkIndexQuery.contains(":attribute_cd"));
        assertTrue(checkIndexQuery.contains("AS indexed_flg"));
    }

    @Test
    void loadsAttributePairingCacheQueriesFromProperties() {
        SQLQueryLoaderUtil loader = new SQLQueryLoaderUtil(new DefaultResourceLoader());

        String findCachedValueQuery = loader.getQuery("attribute_pairing_cache.find_value");
        String upsertCachedValueQuery = loader.getQuery("attribute_pairing_cache.upsert_value");
        String recordCacheHitQuery = loader.getQuery("attribute_pairing_cache.record_hit");

        assertTrue(findCachedValueQuery.contains("FROM meta.attribute_pairing_value_cache"));
        assertTrue(findCachedValueQuery.contains("pairing_cd = :pairing_cd"));
        assertTrue(findCachedValueQuery.contains("display_value_txt = :display_value_txt"));
        assertTrue(findCachedValueQuery.contains("(client_id = :client_id OR client_id = 'GLOBAL')"));
        assertTrue(findCachedValueQuery.contains("(expires_ts IS NULL OR expires_ts > :as_of_ts)"));
        assertTrue(findCachedValueQuery.contains("ORDER BY CASE WHEN client_id = :client_id THEN 0 ELSE 1 END"));
        assertTrue(findCachedValueQuery.contains("filter_value_txt"));
        assertTrue(findCachedValueQuery.contains("is_one_to_many_flg"));
        assertTrue(findCachedValueQuery.contains("hit_count_nbr"));
        assertTrue(findCachedValueQuery.contains("last_hit_ts"));
        assertTrue(findCachedValueQuery.contains("LIMIT 1"));

        assertTrue(upsertCachedValueQuery.contains("INSERT INTO meta.attribute_pairing_value_cache"));
        assertTrue(upsertCachedValueQuery.contains(":pairing_cd"));
        assertTrue(upsertCachedValueQuery.contains(":client_id"));
        assertTrue(upsertCachedValueQuery.contains(":display_value_txt"));
        assertTrue(upsertCachedValueQuery.contains(":filter_value_txt"));
        assertTrue(upsertCachedValueQuery.contains(":is_one_to_many_flg"));
        assertTrue(upsertCachedValueQuery.contains(":hit_count_nbr"));
        assertTrue(upsertCachedValueQuery.contains(":last_hit_ts"));
        assertTrue(upsertCachedValueQuery.contains(":cached_ts"));
        assertTrue(upsertCachedValueQuery.contains(":expires_ts"));
        assertTrue(upsertCachedValueQuery.contains("ON CONFLICT (pairing_cd, display_value_txt, client_id) DO UPDATE"));
        assertTrue(upsertCachedValueQuery.contains("RETURNING id, pairing_cd, client_id"));
        assertTrue(upsertCachedValueQuery.contains("filter_value_txt"));
        assertTrue(upsertCachedValueQuery.contains("is_one_to_many_flg"));
        assertTrue(upsertCachedValueQuery.contains("hit_count_nbr"));
        assertTrue(upsertCachedValueQuery.contains("expires_ts"));

        assertTrue(recordCacheHitQuery.contains("UPDATE meta.attribute_pairing_value_cache"));
        assertTrue(recordCacheHitQuery.contains("hit_count_nbr = hit_count_nbr + 1"));
        assertTrue(recordCacheHitQuery.contains("last_hit_ts = :last_hit_ts"));
        assertTrue(recordCacheHitQuery.contains("WHERE pairing_cd = :pairing_cd"));
        assertTrue(recordCacheHitQuery.contains("display_value_txt = :display_value_txt"));
        assertTrue(recordCacheHitQuery.contains("client_id = :client_id"));
        assertTrue(recordCacheHitQuery.contains("RETURNING id, pairing_cd, client_id"));
    }
}
