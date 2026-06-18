package com.lextr.semanticlayer.policy;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RelationshipCrossEnginePolicyAssetsTest {

    @Test
    void policyAssetProvidesSingleEvaluateEntryPointAndReasonCode() throws IOException {
        String policy = read("opa/relationship/cross_engine_block.rego");

        assertTrue(policy.contains("package lextr.semantic.relationship"));
        assertTrue(policy.contains("default evaluate :="));
        assertTrue(policy.contains("\"code\": \"POL-CE-001\""));
        assertTrue(policy.contains("allowed_engine[\"POSTGRES\"]"));
        assertTrue(policy.contains("allowed_engine[\"CLICKHOUSE\"]"));
        assertTrue(policy.contains("allowed_engine[\"NEO4J\"]"));
    }

    @Test
    void regoTestAssetCoversAllowAndDenyBranches() throws IOException {
        String tests = read("opa/relationship/cross_engine_block_test.rego");

        assertTrue(tests.contains("package lextr.semantic.relationship_test"));
        assertTrue(tests.contains("import data.lextr.semantic.relationship"));
        assertTrue(tests.contains("test_evaluate_allows_same_engine_relationship"));
        assertTrue(tests.contains("test_evaluate_denies_flagged_cross_engine_relationship"));
        assertTrue(tests.contains("test_evaluate_denies_engine_mismatch_even_when_flag_false"));
        assertTrue(tests.contains("test_evaluate_defaults_deny_on_unknown_input"));
        assertTrue(tests.contains("decision.code == \"POL-CE-001\""));
    }

    private static String read(String path) throws IOException {
        return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
    }
}
