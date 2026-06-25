package com.lextr.semanticlayer.policy;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

class FilterLookupBindingPolicyAssetsTest {

    @Test
    void policyAssetProvidesSingleEvaluateEntryPointAndReasonCode() throws IOException {
        String policy = read("opa/filter_lookup/overdue_binding.rego");

        assertTrue(policy.contains("package lextr.semantic.filter_lookup_binding"));
        assertTrue(policy.contains("default evaluate :="));
        assertTrue(policy.contains("\"code\": \"POL-SV-002\""));
        assertTrue(policy.contains("\"POL-SV-002: unknown or invalid input\""));
        assertTrue(policy.contains("input.binding_context_cd == \"PIPELINE\""));
    }

    @Test
    void regoTestAssetCoversAllowAndDenyBranches() throws IOException {
        String tests = read("opa/filter_lookup/overdue_binding_test.rego");

        assertTrue(tests.contains("package lextr.semantic.filter_lookup_binding_test"));
        assertTrue(tests.contains("import data.lextr.semantic.filter_lookup_binding"));
        assertTrue(tests.contains("test_evaluate_allows_binding_when_not_overdue"));
        assertTrue(tests.contains("test_evaluate_allows_binding_when_overdue_but_query_studio"));
        assertTrue(tests.contains("test_evaluate_denies_binding_when_overdue_and_pipeline"));
        assertTrue(tests.contains("test_evaluate_defaults_deny_on_invalid_input"));
        assertTrue(tests.contains("decision.code == \"POL-SV-002\""));
        assertTrue(tests.contains("contains(decision.message, \"POL-SV-002\")"));
        assertTrue(tests.contains("decision.message == \"POL-SV-002: unknown or invalid input\""));
    }

    private static String read(String path) throws IOException {
        return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
    }
}
