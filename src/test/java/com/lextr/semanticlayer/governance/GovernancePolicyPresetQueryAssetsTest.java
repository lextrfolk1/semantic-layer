package com.lextr.semanticlayer.governance;

import com.lextr.semanticlayer.util.SQLQueryLoaderUtil;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import static org.junit.jupiter.api.Assertions.assertTrue;

class GovernancePolicyPresetQueryAssetsTest {

    @Test
    void loadsGovernancePolicyPresetQueriesFromProperties() {
        SQLQueryLoaderUtil loader = new SQLQueryLoaderUtil(new DefaultResourceLoader());

        String findByCodeQuery = loader.getQuery("governance_policy_preset.find_by_code");
        String findAllQuery = loader.getQuery("governance_policy_preset.find_all");

        assertPolicyPresetColumns(findByCodeQuery);
        assertTrue(findByCodeQuery.contains("FROM governance.policy_preset"));
        assertTrue(findByCodeQuery.contains("policy_cd = :policy_cd"));
        assertTrue(findByCodeQuery.contains("policy_scope_cd = :policy_scope_cd"));
        assertTrue(findByCodeQuery.contains("effective_from_dt <= :as_of_dt"));
        assertTrue(findByCodeQuery.contains("effective_to_dt IS NULL OR effective_to_dt >= :as_of_dt"));

        assertPolicyPresetColumns(findAllQuery);
        assertTrue(findAllQuery.contains("FROM governance.policy_preset"));
        assertTrue(findAllQuery.contains("CAST(:policy_scope_cd AS varchar) IS NULL"));
        assertTrue(findAllQuery.contains(":as_of_dt"));
        assertTrue(findAllQuery.contains("ORDER BY policy_cd"));
    }

    private static void assertPolicyPresetColumns(String query) {
        assertTrue(query.contains("policy_cd"));
        assertTrue(query.contains("policy_nm"));
        assertTrue(query.contains("policy_scope_cd"));
        assertTrue(query.contains("default_value_txt"));
        assertTrue(query.contains("data_type_cd"));
        assertTrue(query.contains("is_overrideable_flg"));
        assertTrue(query.contains("override_requires_approval_flg"));
        assertTrue(query.contains("effective_from_dt"));
        assertTrue(query.contains("effective_to_dt"));
        assertTrue(query.contains("approved_by"));
        assertTrue(query.contains("approved_ts"));
        assertTrue(query.contains("created_ts"));
        assertTrue(query.contains("created_by"));
    }
}
