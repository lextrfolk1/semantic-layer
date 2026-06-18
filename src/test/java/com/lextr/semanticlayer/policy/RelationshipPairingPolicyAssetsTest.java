package com.lextr.semanticlayer.policy;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RelationshipPairingPolicyAssetsTest {

    @Test
    void pairingPolicyAssetProvidesSingleEvaluateEntryPointAndReasonCode() throws IOException {
        String policy = read("opa/relationship/cross_engine_pairing.rego");

        assertTrue(policy.contains("package lextr.semantic.attribute_pairing"));
        assertTrue(policy.contains("default evaluate :="));
        assertTrue(policy.contains("\"code\": \"POL-CE-002\""));
    }

    @Test
    void pairingRegoTestAssetCoversAllowAndDenyBranches() throws IOException {
        String tests = read("opa/relationship/cross_engine_pairing_test.rego");

        assertTrue(tests.contains("package lextr.semantic.attribute_pairing_test"));
        assertTrue(tests.contains("import data.lextr.semantic.attribute_pairing"));
        assertTrue(tests.contains("test_evaluate_allows_non_cross_engine_pairing"));
        assertTrue(tests.contains("test_evaluate_denies_cross_engine_pairing"));
        assertTrue(tests.contains("test_evaluate_defaults_deny_on_invalid_input"));
        assertTrue(tests.contains("decision.code == \"POL-CE-002\""));
    }

    @Test
    void queryPolicyAssetProvidesSingleEvaluateEntryPointAndReasonCode() throws IOException {
        String policy = read("opa/relationship/cross_engine_query.rego");

        assertTrue(policy.contains("package lextr.semantic.cross_engine_query"));
        assertTrue(policy.contains("default evaluate :="));
        assertTrue(policy.contains("\"code\": \"POL-CE-003\""));
    }

    @Test
    void queryRegoTestAssetCoversAllowAndDenyBranches() throws IOException {
        String tests = read("opa/relationship/cross_engine_query_test.rego");

        assertTrue(tests.contains("package lextr.semantic.cross_engine_query_test"));
        assertTrue(tests.contains("import data.lextr.semantic.cross_engine_query"));
        assertTrue(tests.contains("test_evaluate_allows_non_cross_engine_query"));
        assertTrue(tests.contains("test_evaluate_denies_cross_engine_query"));
        assertTrue(tests.contains("test_evaluate_defaults_deny_on_invalid_input"));
        assertTrue(tests.contains("decision.code == \"POL-CE-003\""));
    }

    private static String read(String path) throws IOException {
        return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
    }
}
