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
        String insertWorkflowTaskQuery = loader.getQuery("filter_lookup_registration.insert_workflow_task");
        String governancePresetQuery = loader.getQuery("governance_policy_preset.find_by_code");

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
    }
}
