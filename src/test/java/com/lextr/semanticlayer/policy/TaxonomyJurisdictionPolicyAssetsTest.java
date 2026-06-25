package com.lextr.semanticlayer.policy;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TaxonomyJurisdictionPolicyAssetsTest {

    @Test
    void policyAssetProvidesSingleEvaluateEntryPointAndReasonCode() throws IOException {
        String policy = read("opa/taxonomy/jurisdiction_valid.rego");

        assertTrue(policy.contains("package lextr.semantic.taxonomy"));
        assertTrue(policy.contains("default evaluate :="));
        assertTrue(policy.contains("\"code\": \"taxonomy.jurisdiction_valid\""));
        assertTrue(policy.contains("expected_jurisdiction_length[\"MDRM\"] := 2"));
    }

    @Test
    void regoTestAssetCoversAllowAndDenyBranches() throws IOException {
        String tests = read("opa/taxonomy/jurisdiction_valid_test.rego");

        assertTrue(tests.contains("package lextr.semantic.taxonomy_test"));
        assertTrue(tests.contains("import data.lextr.semantic.taxonomy"));
        assertTrue(tests.contains("test_evaluate_allows_valid_mdrm_input"));
        assertTrue(tests.contains("test_evaluate_denies_invalid_taxonomy_cd_length"));
        assertTrue(tests.contains("test_evaluate_denies_invalid_jurisdiction_length"));
        assertTrue(tests.contains("test_evaluate_defaults_deny_on_unknown_input"));
        assertTrue(tests.contains("test_evaluate_defaults_deny_on_missing_client_id"));
        assertTrue(tests.contains("decision.code == \"taxonomy.jurisdiction_valid\""));
    }

    private static String read(String path) throws IOException {
        return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
    }
}
