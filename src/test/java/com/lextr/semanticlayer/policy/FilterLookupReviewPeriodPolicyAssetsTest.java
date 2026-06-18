package com.lextr.semanticlayer.policy;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

class FilterLookupReviewPeriodPolicyAssetsTest {

    @Test
    void policyAssetProvidesSingleEvaluateEntryPointAndReasonCode() throws IOException {
        String policy = read("opa/filter_lookup/review_period_floor.rego");

        assertTrue(policy.contains("package lextr.semantic.filter_lookup"));
        assertTrue(policy.contains("default evaluate :="));
        assertTrue(policy.contains("\"code\": \"GOV-FL-001\""));
        assertTrue(policy.contains("\"GOV-FL-001: unknown or invalid input\""));
    }

    @Test
    void regoTestAssetCoversAllowAndDenyBranches() throws IOException {
        String tests = read("opa/filter_lookup/review_period_floor_test.rego");

        assertTrue(tests.contains("package lextr.semantic.filter_lookup_test"));
        assertTrue(tests.contains("import data.lextr.semantic.filter_lookup"));
        assertTrue(tests.contains("test_evaluate_allows_when_override_absent"));
        assertTrue(tests.contains("test_evaluate_allows_when_override_is_stricter_than_floor"));
        assertTrue(tests.contains("test_evaluate_denies_when_override_is_looser_than_floor"));
        assertTrue(tests.contains("test_evaluate_defaults_deny_on_unknown_input"));
        assertTrue(tests.contains("decision.code == \"GOV-FL-001\""));
    }

    private static String read(String path) throws IOException {
        return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
    }
}
