package com.lextr.semanticlayer.policy;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DqRequestObservePolicyAssetsTest {

    @Test
    void policyAssetProvidesSingleEvaluateEntryPointAndReasonCode() throws IOException {
        String policy = read("opa/dq/request_observe.rego");

        assertTrue(policy.contains("package lextr.semantic.dq_request_observe"));
        assertTrue(policy.contains("default evaluate :="));
        assertTrue(policy.contains("\"code\": \"POL-DQ-001\""));
        assertTrue(policy.contains("\"reason\": \"POL-DQ-001: unknown or invalid input\""));
        assertTrue(policy.contains("input.request_type_cd == \"REQUEST\""));
        assertTrue(policy.contains("input.request_type_cd == \"RESULT\""));
        assertTrue(policy.contains("input.client_id != \"\""));
        assertTrue(policy.contains("input.principal_cd == \"ENGINE\""));
    }

    @Test
    void regoTestAssetCoversAllowAndDenyBranches() throws IOException {
        String tests = read("opa/dq/request_observe_test.rego");

        assertTrue(tests.contains("package lextr.semantic.dq_request_observe_test"));
        assertTrue(tests.contains("import data.lextr.semantic.dq_request_observe"));
        assertTrue(tests.contains("test_evaluate_allows_request_when_tenant_scope_present"));
        assertTrue(tests.contains("test_evaluate_denies_request_without_tenant_scope"));
        assertTrue(tests.contains("test_evaluate_allows_result_post_when_principal_is_engine"));
        assertTrue(tests.contains("test_evaluate_denies_result_post_when_principal_is_not_engine"));
        assertTrue(tests.contains("test_evaluate_defaults_deny_on_unknown_input"));
        assertTrue(tests.contains("decision.code == \"POL-DQ-001\""));
        assertTrue(tests.contains("decision.reason == \"POL-DQ-001: unknown or invalid input\""));
    }

    private static String read(String path) throws IOException {
        return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
    }
}
