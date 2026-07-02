package com.lextr.semanticlayer.dq;

import com.lextr.semanticlayer.util.SQLQueryLoaderUtil;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DqRuleQueryAssetsTest {

    @Test
    void loadsDqRuleCatalogRequestMatrixAndResultQueriesFromProperties() {
        SQLQueryLoaderUtil loader = new SQLQueryLoaderUtil(new DefaultResourceLoader());

        String catalogListQuery = loader.getQuery("dq_rule_catalog.find_all");
        String catalogByCodeQuery = loader.getQuery("dq_rule_catalog.find_by_code");
        String requestInsertQuery = loader.getQuery("dq_rule_request.insert_workflow_task");
        String requestByIdQuery = loader.getQuery("dq_rule_request.find_by_id");
        String requestAuditQuery = loader.getQuery("dq_rule_request.insert_metadata_change_history");
        String matrixByRuleQuery = loader.getQuery("dq_rule_attribute.find_by_rule_code");
        String resultByAttributeQuery = loader.getQuery("dq_result.find_by_attribute");
        String resultInsertQuery = loader.getQuery("dq_result.insert_result");

        assertTrue(catalogListQuery.contains("FROM meta.dq_rule_catalog"));
        assertTrue(catalogListQuery.contains("rule_cd"));
        assertTrue(catalogListQuery.contains("rule_nm"));
        assertTrue(catalogListQuery.contains("rule_dimension_cd"));
        assertTrue(catalogListQuery.contains("logical_attribute_cd"));
        assertTrue(catalogListQuery.contains("rule_expression_txt"));
        assertTrue(catalogListQuery.contains("severity_cd"));
        assertTrue(catalogListQuery.contains("lifecycle_status_cd"));
        assertTrue(catalogListQuery.contains("client_id = :client_id"));
        assertTrue(catalogListQuery.contains("client_id = 'GLOBAL'"));
        assertTrue(catalogListQuery.contains("ORDER BY CASE WHEN client_id = :client_id THEN 0 ELSE 1 END, rule_cd"));

        assertTrue(catalogByCodeQuery.contains("FROM meta.dq_rule_catalog"));
        assertTrue(catalogByCodeQuery.contains("rule_cd = :rule_cd"));
        assertTrue(catalogByCodeQuery.contains("client_id = 'GLOBAL'"));
        assertTrue(catalogByCodeQuery.contains("LIMIT 1"));

        assertTrue(requestInsertQuery.contains("INSERT INTO wkfl.workflow_task"));
        assertTrue(requestInsertQuery.contains(":workflow_type_cd"));
        assertTrue(requestInsertQuery.contains(":entity_type_cd"));
        assertTrue(requestInsertQuery.contains(":rule_cd"));
        assertTrue(requestInsertQuery.contains(":task_status_cd"));
        assertTrue(requestInsertQuery.contains(":assigned_to"));
        assertTrue(requestInsertQuery.contains(":due_dt"));
        assertTrue(requestInsertQuery.contains(":description_txt"));
        assertTrue(requestInsertQuery.contains(":client_id"));
        assertTrue(requestInsertQuery.contains("RETURNING id"));
        assertTrue(requestInsertQuery.contains("entity_ref AS rule_cd"));

        assertTrue(requestByIdQuery.contains("FROM wkfl.workflow_task"));
        assertTrue(requestByIdQuery.contains("task_type_cd = 'DQ_RULE_REQUEST'"));
        assertTrue(requestByIdQuery.contains("WHERE client_id = :client_id AND CAST(md5(id::text) AS uuid) = :workflow_task_id"));
        assertTrue(requestByIdQuery.contains("entity_ref AS rule_cd"));
        assertTrue(requestByIdQuery.contains("approval_note_txt"));
        assertTrue(requestAuditQuery.contains("INSERT INTO meta.metadata_change_history"));
        assertTrue(requestAuditQuery.contains("CAST(:entity_ref AS varchar)"));

        assertTrue(matrixByRuleQuery.contains("FROM meta.dq_rule_attribute"));
        assertTrue(matrixByRuleQuery.contains("rule_cd = :rule_cd"));
        assertTrue(matrixByRuleQuery.contains("attribute_cd"));
        assertTrue(matrixByRuleQuery.contains("attribute_role_cd"));
        assertTrue(matrixByRuleQuery.contains("client_id = :client_id"));
        assertTrue(matrixByRuleQuery.contains("client_id = 'GLOBAL'"));
        assertTrue(matrixByRuleQuery.contains("ORDER BY CASE WHEN client_id = :client_id THEN 0 ELSE 1 END, attribute_cd"));

        assertTrue(resultByAttributeQuery.contains("FROM meta.dq_result"));
        assertTrue(resultByAttributeQuery.contains("logical_attribute_cd = :logical_attribute_cd"));
        assertTrue(resultByAttributeQuery.contains("observed_value_txt"));
        assertTrue(resultByAttributeQuery.contains("expected_value_txt"));
        assertTrue(resultByAttributeQuery.contains("result_status_cd"));
        assertTrue(resultByAttributeQuery.contains("result_reason_txt"));
        assertTrue(resultByAttributeQuery.contains("observed_ts"));
        assertTrue(resultByAttributeQuery.contains("created_ts"));
        assertTrue(resultByAttributeQuery.contains("updated_by"));
        assertTrue(resultInsertQuery.contains("INSERT INTO meta.dq_result"));
        assertTrue(resultInsertQuery.contains("result_status_cd"));
    }
}
