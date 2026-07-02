package com.lextr.semanticlayer.policy;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RuleResultPolicyAssetsTest {

    @Test
    void policyAssetProvidesSingleEvaluateEntryPointAndReasonCode() throws IOException {
        String policy = read("opa/rule_results/ingest_result.rego");

        assertTrue(policy.contains("package lextr.semantic.rule_results"));
        assertTrue(policy.contains("default evaluate :="));
        assertTrue(policy.contains("\"code\": \"POL-RR-001\""));
        assertTrue(policy.contains("\"reason\": \"POL-RR-001: unknown or invalid input\""));
        assertTrue(policy.contains("POL-RR-001: tenant scope is required for rule result ingest"));
        assertTrue(policy.contains("input.request_type_cd == \"RULE_RESULT\""));
        assertTrue(policy.contains("input.output_kind_cd"));
        assertTrue(policy.contains("input.principal_cd"));
        assertTrue(policy.contains("upper(input.principal_cd) == \"ENGINE\""));
        assertTrue(policy.contains("tenant_scope_present"));
    }

    @Test
    void regoTestAssetCoversAllowAndDenyBranches() throws IOException {
        String tests = read("opa/rule_results/ingest_result_test.rego");

        assertTrue(tests.contains("package lextr.semantic.rule_results_test"));
        assertTrue(tests.contains("import data.lextr.semantic.rule_results"));
        assertTrue(tests.contains("test_evaluate_allows_engine_principal_rule_result_ingest"));
        assertTrue(tests.contains("test_evaluate_denies_rule_result_ingest_without_tenant_scope"));
        assertTrue(tests.contains("test_evaluate_denies_non_engine_principal_rule_result_ingest"));
        assertTrue(tests.contains("test_evaluate_defaults_deny_on_unknown_input"));
        assertTrue(tests.contains("decision.code == \"POL-RR-001\""));
        assertTrue(tests.contains("decision.reason == \"POL-RR-001: unknown or invalid input\""));
        assertTrue(tests.contains("decision.reason == \"POL-RR-001: tenant scope is required for rule result ingest\""));
    }

    private static String read(String path) throws IOException {
        return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
    }
}
