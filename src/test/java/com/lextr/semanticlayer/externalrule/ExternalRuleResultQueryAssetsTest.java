package com.lextr.semanticlayer.externalrule;

import com.lextr.semanticlayer.util.SQLQueryLoaderUtil;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ExternalRuleResultQueryAssetsTest {

    @Test
    void loadsExternalRuleResultIngestQueryFromProperties() {
        SQLQueryLoaderUtil loader = new SQLQueryLoaderUtil(new DefaultResourceLoader());

        String insertQuery = loader.getQuery("external_rule_result.insert_result");

        assertTrue(insertQuery.contains("INSERT INTO meta.external_rule_result"));
        assertTrue(insertQuery.contains(":client_id"));
        assertTrue(insertQuery.contains(":outbound_id"));
        assertTrue(insertQuery.contains(":rule_ref_cd"));
        assertTrue(insertQuery.contains(":output_kind_cd"));
        assertTrue(insertQuery.contains("CAST(:output_payload_jsonb AS jsonb)"));
        assertTrue(insertQuery.contains(":observed_ts"));
        assertTrue(insertQuery.contains(":created_ts"));
        assertTrue(insertQuery.contains(":created_by"));
        assertTrue(insertQuery.contains(":updated_ts"));
        assertTrue(insertQuery.contains(":updated_by"));
        assertTrue(insertQuery.contains("RETURNING id"));
        assertTrue(insertQuery.contains("output_payload_jsonb"));
    }
}
