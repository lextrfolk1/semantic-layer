package com.lextr.semanticlayer.policy;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ObjectExposureAccessPolicyAssetsTest {

    @Test
    void policyAssetProvidesSingleEvaluateEntryPointAndReasonCode() throws IOException {
        String policy = read("opa/object_exposure/access_control.rego");

        assertTrue(policy.contains("package lextr.semantic.object_exposure"));
        assertTrue(policy.contains("default evaluate :="));
        assertTrue(policy.contains("\"code\": \"POL-AC-001\""));
        assertTrue(policy.contains("\"reason\": \"POL-AC-001: unknown or invalid input\""));
        assertTrue(policy.contains("input.client_id != input.object_client_id"));
        assertTrue(policy.contains("input.grant_scope_cds[scope_index] == \"READ\""));
        assertTrue(policy.contains("input.grant_status_cds[status_index] == \"ACTIVE\""));
    }

    @Test
    void regoTestAssetCoversAllowAndDenyBranches() throws IOException {
        String tests = read("opa/object_exposure/access_control_test.rego");

        assertTrue(tests.contains("package lextr.semantic.object_exposure_test"));
        assertTrue(tests.contains("import data.lextr.semantic.object_exposure"));
        assertTrue(tests.contains("test_evaluate_allows_same_tenant_object_request"));
        assertTrue(tests.contains("test_evaluate_allows_same_tenant_attribute_request_with_active_read_grant"));
        assertTrue(tests.contains("test_evaluate_denies_cross_tenant_access"));
        assertTrue(tests.contains("test_evaluate_denies_unauthorized_role_or_purpose"));
        assertTrue(tests.contains("test_evaluate_denies_attribute_request_without_active_read_grant"));
        assertTrue(tests.contains("test_evaluate_defaults_deny_on_unknown_input"));
        assertTrue(tests.contains("decision.code == \"POL-AC-001\""));
        assertTrue(tests.contains("decision.reason == \"POL-AC-001: unknown or invalid input\""));
    }

    private static String read(String path) throws IOException {
        return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
    }
}
