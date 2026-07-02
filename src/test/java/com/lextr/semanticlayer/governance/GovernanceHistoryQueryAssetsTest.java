package com.lextr.semanticlayer.governance;

import com.lextr.semanticlayer.util.SQLQueryLoaderUtil;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import static org.junit.jupiter.api.Assertions.assertTrue;

class GovernanceHistoryQueryAssetsTest {

    @Test
    void loadsGovernanceHistoryQueryFromProperties() {
        SQLQueryLoaderUtil loader = new SQLQueryLoaderUtil(new DefaultResourceLoader());

        String query = loader.getQuery("governance_history.find_by_entity");

        assertTrue(query.contains("FROM meta.metadata_change_history mch"));
        assertTrue(query.contains(":client_id"));
        assertTrue(query.contains(":entity_type_cd"));
        assertTrue(query.contains(":entity_ref"));
        assertTrue(query.contains("CAST(:change_type_cd AS varchar) IS NULL"));
        assertTrue(query.contains("mch.change_type_cd = :change_type_cd"));
        assertTrue(query.contains("EXISTS (SELECT 1 FROM wkfl.workflow_task wt"));
        assertTrue(query.contains("wt.client_id = :client_id"));
        assertTrue(query.contains("wt.entity_type_cd = mch.entity_type_cd"));
        assertTrue(query.contains("wt.entity_ref = mch.entity_ref"));
        assertTrue(query.contains("ORDER BY mch.changed_ts DESC, mch.id DESC"));

        assertHistoryColumns(query);
    }

    private static void assertHistoryColumns(String query) {
        assertTrue(query.contains("mch.id AS event_id"));
        assertTrue(query.contains(":client_id AS client_id"));
        assertTrue(query.contains("mch.entity_type_cd"));
        assertTrue(query.contains("mch.entity_ref"));
        assertTrue(query.contains("mch.change_type_cd"));
        assertTrue(query.contains("COALESCE(mch.change_reason_txt, mch.change_type_cd) AS change_summary_txt"));
        assertTrue(query.contains("mch.changed_by AS actor_id"));
        assertTrue(query.contains("mch.changed_ts AS event_ts"));
        assertTrue(query.contains("CAST(mch.old_value_json AS text) AS old_value_json"));
        assertTrue(query.contains("CAST(mch.new_value_json AS text) AS new_value_json"));
    }
}
