package com.lextr.semanticlayer.filter;

import com.lextr.semanticlayer.util.SQLQueryLoaderUtil;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkflowApprovalQueryTest {

    @Test
    void loadsWorkflowApprovalAndSideEffectQueriesFromProperties() {
        SQLQueryLoaderUtil loader = new SQLQueryLoaderUtil(new DefaultResourceLoader());

        String findTaskQuery = loader.getQuery("workflow_approval.find_task_by_id");
        String approveTaskQuery = loader.getQuery("workflow_approval.approve_task");
        String approveLookupQuery = loader.getQuery("workflow_approval.approve_lookup");
        String approveOverrideQuery = loader.getQuery("workflow_approval.approve_attribute_override");
        String approveValueQuery = loader.getQuery("workflow_approval.approve_filter_lookup_value");

        assertTrue(findTaskQuery.contains("FROM wkfl.workflow_task"));
        assertTrue(findTaskQuery.contains("client_id = :client_id"));
        assertTrue(findTaskQuery.contains("id = :id"));
        assertTrue(findTaskQuery.contains("task_type_cd"));
        assertTrue(findTaskQuery.contains("entity_type_cd"));
        assertTrue(findTaskQuery.contains("entity_ref"));
        assertTrue(findTaskQuery.contains("task_status_cd"));

        assertTrue(approveTaskQuery.contains("UPDATE wkfl.workflow_task"));
        assertTrue(approveTaskQuery.contains("SET task_status_cd ="));
        assertTrue(approveTaskQuery.contains("approved_by = :approved_by"));
        assertTrue(approveTaskQuery.contains("approved_ts = :approved_ts"));
        assertTrue(approveTaskQuery.contains("approval_note_txt = :approval_note_txt"));
        assertTrue(approveTaskQuery.contains("task_status_cd = 'PENDING'"));
        assertTrue(approveTaskQuery.contains("RETURNING id, task_type_cd"));

        assertTrue(approveLookupQuery.contains("UPDATE meta.semantic_filter_lookup"));
        assertTrue(approveLookupQuery.contains("governance_status_cd = :governance_status_cd"));
        assertTrue(approveLookupQuery.contains("WHERE client_id = :client_id AND lookup_cd = :lookup_cd"));
        assertTrue(approveLookupQuery.contains("RETURNING id, lookup_cd, governance_status_cd"));

        assertTrue(approveOverrideQuery.contains("UPDATE meta.attribute_logical_name_override"));
        assertTrue(approveOverrideQuery.contains("override_status_cd = :lifecycle_status_cd"));
        assertTrue(approveOverrideQuery.contains("WHERE client_id = :client_id AND id = :id"));
        assertTrue(approveOverrideQuery.contains("RETURNING id, client_id, attribute_cd, override_nm"));

        assertTrue(approveValueQuery.contains("UPDATE meta.filter_lookup_value"));
        assertTrue(approveValueQuery.contains("lifecycle_status_cd = :lifecycle_status_cd"));
        assertTrue(approveValueQuery.contains("validated_flg = :validated_flg"));
        assertTrue(approveValueQuery.contains("WHERE lookup_cd = :lookup_cd AND value_cd = :value_cd"));
        assertTrue(approveValueQuery.contains("RETURNING lookup_cd, value_cd, lifecycle_status_cd, validated_flg"));
    }
}
