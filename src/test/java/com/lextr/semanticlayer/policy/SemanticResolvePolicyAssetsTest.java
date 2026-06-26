package com.lextr.semanticlayer.policy;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SemanticResolvePolicyAssetsTest {

    @Test
    void policyAssetProvidesSingleEvaluateEntryPointAndReasonCode() throws IOException {
        String policy = read("opa/semantic_resolve/resolve_scope.rego");

        assertTrue(policy.contains("package lextr.semantic.semantic_resolve"));
        assertTrue(policy.contains("default evaluate :="));
        assertTrue(policy.contains("\"code\": \"POL-RS-001\""));
        assertTrue(policy.contains("\"reason\": \"POL-RS-001: unknown or invalid input\""));
        assertTrue(policy.contains("input.resource_client_id"));
        assertTrue(policy.contains("input.request_type_cd == \"SEMANTIC\""));
        assertTrue(policy.contains("input.request_type_cd == \"CONSUMPTION\""));
    }

    @Test
    void regoTestAssetCoversAllowAndDenyBranches() throws IOException {
        String tests = read("opa/semantic_resolve/resolve_scope_test.rego");

        assertTrue(tests.contains("package lextr.semantic.semantic_resolve_test"));
        assertTrue(tests.contains("import data.lextr.semantic.semantic_resolve"));
        assertTrue(tests.contains("test_evaluate_allows_same_tenant_semantic_resolve"));
        assertTrue(tests.contains("test_evaluate_allows_same_tenant_consumption_resolve"));
        assertTrue(tests.contains("test_evaluate_denies_cross_tenant_resolve"));
        assertTrue(tests.contains("test_evaluate_denies_missing_role_or_purpose"));
        assertTrue(tests.contains("test_evaluate_defaults_deny_on_unknown_input"));
        assertTrue(tests.contains("decision.code == \"POL-RS-001\""));
        assertTrue(tests.contains("decision.reason == \"POL-RS-001: unknown or invalid input\""));
    }

    private static String read(String path) throws IOException {
        return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
    }
}
