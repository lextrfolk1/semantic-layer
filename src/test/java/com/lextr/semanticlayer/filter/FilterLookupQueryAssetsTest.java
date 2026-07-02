package com.lextr.semanticlayer.filter;

import com.lextr.semanticlayer.util.SQLQueryLoaderUtil;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import static org.junit.jupiter.api.Assertions.assertTrue;

class FilterLookupQueryAssetsTest {

    @Test
    void loadsFilterLookupRegistrationQueriesFromProperties() {
        SQLQueryLoaderUtil loader = new SQLQueryLoaderUtil(new DefaultResourceLoader());

        String insertLookupQuery = loader.getQuery("filter_lookup_registration.insert_lookup");
        String certifyLookupQuery = loader.getQuery("filter_lookup_registration.certify_lookup");
        String insertBindingQuery = loader.getQuery("filter_lookup_registration.insert_binding");
        String insertWorkflowTaskQuery = loader.getQuery("filter_lookup_registration.insert_workflow_task");
        String insertMetadataChangeHistoryQuery = loader.getQuery("filter_lookup_registration.insert_metadata_change_history");
        String governancePresetQuery = loader.getQuery("governance_policy_preset.find_by_code");
        String effectiveLookupListQuery = loader.getQuery("filter_lookup_effective_review.find_all");
        String effectiveLookupQuery = loader.getQuery("filter_lookup_effective_review.find_lookup_by_code");
        String manualPreviewQuery = loader.getQuery("filter_lookup_effective_review.find_manual_values_by_lookup");
        String sqlPreviewQuery = loader.getQuery("filter_lookup_effective_review.find_sql_values_template");
        String valueCountQuery = loader.getQuery("filter_lookup_effective_review.count_values_by_lookup");
        String staleValueCountQuery = loader.getQuery("filter_lookup_effective_review.count_stale_values_by_lookup");
        String executionLogInsertQuery = loader.getQuery("filter_lookup_exec_log.insert_execution");

        assertTrue(insertLookupQuery.contains("INSERT INTO meta.semantic_filter_lookup"));
        assertTrue(insertLookupQuery.contains(":lookup_cd"));
        assertTrue(insertLookupQuery.contains(":review_period_days_override"));
        assertTrue(insertLookupQuery.contains(":next_review_due_dt"));
        assertTrue(insertLookupQuery.contains(":created_ts"));
        assertTrue(insertLookupQuery.contains(":updated_by"));
        assertTrue(insertLookupQuery.contains("construction_type_cd"));
        assertTrue(insertLookupQuery.contains("execution_strategy_cd"));
        assertTrue(insertLookupQuery.contains("governance_status_cd"));
        assertTrue(insertLookupQuery.contains("health_status_cd"));
        assertTrue(insertLookupQuery.contains("next_review_due_dt"));
        assertTrue(insertLookupQuery.contains("lifecycle_status_cd"));
        assertTrue(insertLookupQuery.contains("created_ts"));
        assertTrue(insertLookupQuery.contains("updated_ts"));

        assertTrue(certifyLookupQuery.contains("UPDATE meta.semantic_filter_lookup"));
        assertTrue(certifyLookupQuery.contains(":health_status_cd"));
        assertTrue(certifyLookupQuery.contains(":last_certified_ts"));
        assertTrue(certifyLookupQuery.contains(":last_certified_by"));
        assertTrue(certifyLookupQuery.contains(":next_review_due_dt"));
        assertTrue(certifyLookupQuery.contains("WHERE client_id = :client_id AND lookup_cd = :lookup_cd"));
        assertTrue(certifyLookupQuery.contains("RETURNING id, lookup_cd"));

        assertTrue(insertBindingQuery.contains("INSERT INTO meta.filter_lookup_binding"));
        assertTrue(insertBindingQuery.contains(":lookup_cd"));
        assertTrue(insertBindingQuery.contains(":bound_obj"));
        assertTrue(insertBindingQuery.contains(":bound_attr_cd"));
        assertTrue(insertBindingQuery.contains(":binding_context_cd"));
        assertTrue(insertBindingQuery.contains(":binding_ref"));
        assertTrue(insertBindingQuery.contains(":bound_by"));
        assertTrue(insertBindingQuery.contains(":bound_ts"));
        assertTrue(insertBindingQuery.contains(":is_active_flg"));
        assertTrue(insertBindingQuery.contains("RETURNING id, lookup_cd, bound_obj"));

        assertTrue(insertWorkflowTaskQuery.contains("INSERT INTO wkfl.workflow_task"));
        assertTrue(insertWorkflowTaskQuery.contains(":task_type_cd"));
        assertTrue(insertWorkflowTaskQuery.contains(":entity_ref"));
        assertTrue(insertWorkflowTaskQuery.contains(":submitted_ts"));
        assertTrue(insertWorkflowTaskQuery.contains(":client_id"));
        assertTrue(insertWorkflowTaskQuery.contains("task_type_cd"));
        assertTrue(insertWorkflowTaskQuery.contains("entity_type_cd"));
        assertTrue(insertWorkflowTaskQuery.contains("entity_ref"));
        assertTrue(insertWorkflowTaskQuery.contains("task_status_cd"));
        assertTrue(insertWorkflowTaskQuery.contains("submitted_by"));
        assertTrue(insertWorkflowTaskQuery.contains("submitted_ts"));

        assertTrue(insertMetadataChangeHistoryQuery.contains("INSERT INTO meta.metadata_change_history"));
        assertTrue(insertMetadataChangeHistoryQuery.contains(":entity_ref"));
        assertTrue(insertMetadataChangeHistoryQuery.contains(":changed_ts"));
        assertTrue(insertMetadataChangeHistoryQuery.contains("CAST(:old_value_json AS jsonb)"));
        assertTrue(insertMetadataChangeHistoryQuery.contains("CAST(:new_value_json AS jsonb)"));
        assertTrue(insertMetadataChangeHistoryQuery.contains("change_reason_txt"));

        assertTrue(governancePresetQuery.contains("FROM governance.policy_preset"));
        assertTrue(governancePresetQuery.contains(":policy_cd"));
        assertTrue(governancePresetQuery.contains(":policy_scope_cd"));
        assertTrue(governancePresetQuery.contains(":as_of_dt"));
        assertTrue(governancePresetQuery.contains("policy_cd"));
        assertTrue(governancePresetQuery.contains("policy_scope_cd"));
        assertTrue(governancePresetQuery.contains("default_value_txt"));
        assertTrue(governancePresetQuery.contains("data_type_cd"));
        assertTrue(governancePresetQuery.contains("is_overrideable_flg"));
        assertTrue(governancePresetQuery.contains("override_requires_approval_flg"));
        assertTrue(governancePresetQuery.contains("effective_from_dt"));
        assertTrue(governancePresetQuery.contains("effective_to_dt"));
        assertTrue(governancePresetQuery.contains("created_ts"));
        assertTrue(governancePresetQuery.contains("created_by"));

        assertTrue(effectiveLookupListQuery.contains("FROM meta.semantic_filter_lookup"));
        assertTrue(effectiveLookupListQuery.contains(":client_id"));
        assertTrue(effectiveLookupListQuery.contains(":governance_status_cd"));
        assertTrue(effectiveLookupListQuery.contains(":health_status_cd"));
        assertTrue(effectiveLookupListQuery.contains(":lifecycle_status_cd"));
        assertTrue(effectiveLookupListQuery.contains("ORDER BY lookup_cd"));

        assertTrue(effectiveLookupQuery.contains("FROM meta.semantic_filter_lookup"));
        assertTrue(effectiveLookupQuery.contains(":client_id"));
        assertTrue(effectiveLookupQuery.contains(":lookup_cd"));
        assertTrue(effectiveLookupQuery.contains("review_period_days_override"));
        assertTrue(effectiveLookupQuery.contains("health_status_cd"));
        assertTrue(effectiveLookupQuery.contains("last_certified_ts"));
        assertTrue(effectiveLookupQuery.contains("next_review_due_dt"));
        assertTrue(effectiveLookupQuery.contains("lifecycle_status_cd"));
        assertTrue(effectiveLookupQuery.contains("created_ts"));
        assertTrue(effectiveLookupQuery.contains("updated_by"));

        assertTrue(manualPreviewQuery.contains("FROM meta.filter_lookup_value flv"));
        assertTrue(manualPreviewQuery.contains("JOIN meta.semantic_filter_lookup sfl ON sfl.lookup_cd = flv.lookup_cd"));
        assertTrue(manualPreviewQuery.contains(":client_id"));
        assertTrue(manualPreviewQuery.contains(":lookup_cd"));
        assertTrue(manualPreviewQuery.contains("flv.lifecycle_status_cd IN ('ACTIVE','ANTICIPATED')"));
        assertTrue(manualPreviewQuery.contains("anticipated_dt"));
        assertTrue(manualPreviewQuery.contains("ORDER BY CASE flv.lifecycle_status_cd"));
        assertTrue(sqlPreviewQuery.contains("SELECT DISTINCT :lookup_cd AS lookup_cd"));
        assertTrue(sqlPreviewQuery.contains("CAST(%1$s AS varchar(100)) AS value_cd"));
        assertTrue(sqlPreviewQuery.contains("FROM %2$s"));
        assertTrue(sqlPreviewQuery.contains("WHERE %3$s"));
        assertTrue(sqlPreviewQuery.contains("LIMIT :max_output_rows"));

        assertTrue(valueCountQuery.contains("FROM meta.filter_lookup_value"));
        assertTrue(valueCountQuery.contains(":client_id"));
        assertTrue(valueCountQuery.contains(":lookup_cd"));
        assertTrue(valueCountQuery.contains("JOIN meta.semantic_filter_lookup sfl ON sfl.lookup_cd = flv.lookup_cd"));
        assertTrue(valueCountQuery.contains("COUNT(*) AS value_count"));
        assertTrue(valueCountQuery.contains("flv.lifecycle_status_cd IN ('ACTIVE','ANTICIPATED')"));
        assertTrue(valueCountQuery.contains("GROUP BY flv.lookup_cd, sfl.client_id"));

        assertTrue(staleValueCountQuery.contains("FROM meta.filter_lookup_value"));
        assertTrue(staleValueCountQuery.contains(":client_id"));
        assertTrue(staleValueCountQuery.contains(":lookup_cd"));
        assertTrue(staleValueCountQuery.contains("COUNT(*) AS stale_value_count"));
        assertTrue(staleValueCountQuery.contains("flv.lifecycle_status_cd = 'INACTIVE_IN_SOURCE'"));
        assertTrue(staleValueCountQuery.contains("GROUP BY flv.lookup_cd, sfl.client_id"));

        assertTrue(executionLogInsertQuery.contains("INSERT INTO meta.filter_lookup_exec_log"));
        assertTrue(executionLogInsertQuery.contains(":lookup_cd"));
        assertTrue(executionLogInsertQuery.contains(":executed_by"));
        assertTrue(executionLogInsertQuery.contains(":phase1_duration_ms"));
        assertTrue(executionLogInsertQuery.contains(":phase1_row_count"));
        assertTrue(executionLogInsertQuery.contains(":phase1_cache_hit_flg"));
        assertTrue(executionLogInsertQuery.contains(":execution_strategy_used_cd"));
        assertTrue(executionLogInsertQuery.contains(":result_status_cd"));
        assertTrue(executionLogInsertQuery.contains("RETURNING id, lookup_cd, executed_by, executed_ts"));
    }
}
