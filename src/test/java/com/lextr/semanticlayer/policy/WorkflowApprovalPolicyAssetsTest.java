package com.lextr.semanticlayer.policy;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkflowApprovalPolicyAssetsTest {

    @Test
    void policyAssetProvidesSingleEvaluateEntryPointAndReasonCode() throws IOException {
        String policy = read("opa/workflow/approval_gate.rego");

        assertTrue(policy.contains("package lextr.semantic.workflow_approval"));
        assertTrue(policy.contains("default evaluate :="));
        assertTrue(policy.contains("\"code\": \"POL-SV-003\""));
        assertTrue(policy.contains("approver %s cannot be the same as submitter"));
        assertTrue(policy.contains("POL-SV-003: allow"));
    }

    @Test
    void regoTestAssetCoversAllowAndDenyBranches() throws IOException {
        String tests = read("opa/workflow/approval_gate_test.rego");

        assertTrue(tests.contains("package lextr.semantic.workflow_approval_test"));
        assertTrue(tests.contains("import data.lextr.semantic.workflow_approval"));
        assertTrue(tests.contains("test_evaluate_allows_when_approver_differs_from_submitter"));
        assertTrue(tests.contains("test_evaluate_denies_self_approval"));
        assertTrue(tests.contains("test_evaluate_defaults_deny_on_unknown_input"));
        assertTrue(tests.contains("decision.code == \"POL-SV-003\""));
    }

    private static String read(String path) throws IOException {
        return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
    }
}
