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
        String rejectTaskQuery = loader.getQuery("workflow_approval.reject_task");
        String approveLookupQuery = loader.getQuery("workflow_approval.approve_lookup");
        String rejectLookupQuery = loader.getQuery("workflow_approval.reject_lookup");
        String approveOverrideQuery = loader.getQuery("workflow_approval.approve_attribute_override");
        String rejectOverrideQuery = loader.getQuery("workflow_approval.reject_attribute_override");
        String approveValueQuery = loader.getQuery("workflow_approval.approve_filter_lookup_value");
        String approveObjectQuery = loader.getQuery("workflow_approval.approve_object");
        String rejectObjectQuery = loader.getQuery("workflow_approval.reject_object");
        String approvePairingQuery = loader.getQuery("workflow_approval.approve_pairing");
        String rejectPairingQuery = loader.getQuery("workflow_approval.reject_pairing");
        String approveRelationshipQuery = loader.getQuery("workflow_approval.approve_relationship");
        String rejectRelationshipQuery = loader.getQuery("workflow_approval.reject_relationship");

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

        assertTrue(rejectTaskQuery.contains("UPDATE wkfl.workflow_task"));
        assertTrue(rejectTaskQuery.contains("task_status_cd = 'REJECTED'"));
        assertTrue(rejectTaskQuery.contains("approved_by = :rejected_by"));
        assertTrue(rejectTaskQuery.contains("approved_ts = :rejected_ts"));
        assertTrue(rejectTaskQuery.contains("approval_note_txt = :rejection_note_txt"));
        assertTrue(rejectTaskQuery.contains("WHERE client_id = :client_id AND id = :id"));
        assertTrue(rejectTaskQuery.contains("RETURNING id, task_type_cd"));

        assertTrue(approveLookupQuery.contains("UPDATE meta.semantic_filter_lookup"));
        assertTrue(approveLookupQuery.contains("governance_status_cd = :governance_status_cd"));
        assertTrue(approveLookupQuery.contains("WHERE client_id = :client_id AND lookup_cd = :lookup_cd"));
        assertTrue(approveLookupQuery.contains("RETURNING id, lookup_cd, governance_status_cd"));

        assertTrue(rejectLookupQuery.contains("UPDATE meta.semantic_filter_lookup"));
        assertTrue(rejectLookupQuery.contains("governance_status_cd = 'SUSPENDED'"));
        assertTrue(rejectLookupQuery.contains("lifecycle_status_cd = 'REJECTED'"));
        assertTrue(rejectLookupQuery.contains("WHERE client_id = :client_id AND lookup_cd = :lookup_cd"));

        assertTrue(approveOverrideQuery.contains("UPDATE meta.attribute_logical_name_override"));
        assertTrue(approveOverrideQuery.contains("override_status_cd = :lifecycle_status_cd"));
        assertTrue(approveOverrideQuery.contains("WHERE client_id = :client_id AND id = :id"));
        assertTrue(approveOverrideQuery.contains("RETURNING id, client_id, attribute_cd, override_nm"));

        assertTrue(rejectOverrideQuery.contains("UPDATE meta.attribute_logical_name_override"));
        assertTrue(rejectOverrideQuery.contains("override_status_cd = 'REJECTED'"));
        assertTrue(rejectOverrideQuery.contains("WHERE client_id = :client_id AND id = :id"));

        assertTrue(approveValueQuery.contains("UPDATE meta.filter_lookup_value"));
        assertTrue(approveValueQuery.contains("lifecycle_status_cd = :lifecycle_status_cd"));
        assertTrue(approveValueQuery.contains("validated_flg = :validated_flg"));
        assertTrue(approveValueQuery.contains("WHERE lookup_cd = :lookup_cd AND value_cd = :value_cd"));
        assertTrue(approveValueQuery.contains("RETURNING lookup_cd, value_cd, lifecycle_status_cd, validated_flg"));

        assertTrue(approveObjectQuery.contains("UPDATE meta.object_catalog"));
        assertTrue(approveObjectQuery.contains("lifecycle_status_cd = 'APPROVED'"));
        assertTrue(approveObjectQuery.contains("governance_review_status_cd = 'APPROVED'"));
        assertTrue(approveObjectQuery.contains("WHERE client_id = :client_id"));

        assertTrue(rejectObjectQuery.contains("UPDATE meta.object_catalog"));
        assertTrue(rejectObjectQuery.contains("lifecycle_status_cd = 'DRAFT'"));
        assertTrue(rejectObjectQuery.contains("governance_review_status_cd = 'REJECTED'"));
        assertTrue(rejectObjectQuery.contains("WHERE client_id = :client_id"));

        assertTrue(approvePairingQuery.contains("UPDATE meta.attribute_pairing_catalog"));
        assertTrue(approvePairingQuery.contains("lifecycle_status_cd = 'APPROVED'"));
        assertTrue(approvePairingQuery.contains("governance_review_status_cd = 'APPROVED'"));

        assertTrue(rejectPairingQuery.contains("UPDATE meta.attribute_pairing_catalog"));
        assertTrue(rejectPairingQuery.contains("lifecycle_status_cd = 'DRAFT'"));
        assertTrue(rejectPairingQuery.contains("governance_review_status_cd = 'REJECTED'"));

        assertTrue(approveRelationshipQuery.contains("UPDATE meta.semantic_relationship_catalog"));
        assertTrue(approveRelationshipQuery.contains("lifecycle_status_cd = 'APPROVED'"));

        assertTrue(rejectRelationshipQuery.contains("UPDATE meta.semantic_relationship_catalog"));
        assertTrue(rejectRelationshipQuery.contains("lifecycle_status_cd = 'REJECTED'"));
    }
}
