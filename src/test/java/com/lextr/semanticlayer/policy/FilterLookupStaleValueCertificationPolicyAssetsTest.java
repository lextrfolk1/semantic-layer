package com.lextr.semanticlayer.policy;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

class FilterLookupStaleValueCertificationPolicyAssetsTest {

    @Test
    void policyAssetProvidesSingleEvaluateEntryPointAndReasonCode() throws IOException {
        String policy = read("opa/filter_lookup/stale_value_certification.rego");

        assertTrue(policy.contains("package lextr.semantic.filter_lookup_certification"));
        assertTrue(policy.contains("default evaluate :="));
        assertTrue(policy.contains("\"code\": \"POL-SV-001\""));
        assertTrue(policy.contains("\"POL-SV-001: unknown or invalid input\""));
        assertTrue(policy.contains("input.stale_value_count >= 0"));
    }

    @Test
    void regoTestAssetCoversAllowAndDenyBranches() throws IOException {
        String tests = read("opa/filter_lookup/stale_value_certification_test.rego");

        assertTrue(tests.contains("package lextr.semantic.filter_lookup_certification_test"));
        assertTrue(tests.contains("import data.lextr.semantic.filter_lookup_certification"));
        assertTrue(tests.contains("test_evaluate_allows_certification_when_no_stale_values_exist"));
        assertTrue(tests.contains("test_evaluate_denies_certification_when_stale_values_exist"));
        assertTrue(tests.contains("test_evaluate_defaults_deny_on_unknown_input"));
        assertTrue(tests.contains("decision.code == \"POL-SV-001\""));
        assertTrue(tests.contains("contains(decision.message, \"inactive in source\")"));
    }

    private static String read(String path) throws IOException {
        return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
    }
}
