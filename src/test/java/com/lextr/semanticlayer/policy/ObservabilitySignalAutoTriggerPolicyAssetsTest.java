package com.lextr.semanticlayer.policy;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ObservabilitySignalAutoTriggerPolicyAssetsTest {

    @Test
    void policyAssetProvidesSingleEvaluateEntryPointAndReasonCode() throws IOException {
        String policy = read("opa/observability_signal/auto_trigger_gate.rego");

        assertTrue(policy.contains("package lextr.semantic.observability_signal"));
        assertTrue(policy.contains("default evaluate :="));
        assertTrue(policy.contains("\"code\": \"POL-OS-001\""));
        assertTrue(policy.contains("\"reason\": \"POL-OS-001: unknown or invalid input\""));
        assertTrue(policy.contains("input.trigger_cd == \"WORKFLOW_ROUTE\""));
        assertTrue(policy.contains("input.trigger_cd == \"DQ_RERUN\""));
        assertTrue(policy.contains("severity_rank[\"HIGH\"] := 3"));
    }

    @Test
    void regoTestAssetCoversAllowAndDenyBranches() throws IOException {
        String tests = read("opa/observability_signal/auto_trigger_gate_test.rego");

        assertTrue(tests.contains("package lextr.semantic.observability_signal_test"));
        assertTrue(tests.contains("import data.lextr.semantic.observability_signal"));
        assertTrue(tests.contains("test_evaluate_allows_workflow_route_when_severity_meets_threshold"));
        assertTrue(tests.contains("test_evaluate_denies_workflow_route_when_severity_below_threshold"));
        assertTrue(tests.contains("test_evaluate_allows_dq_rerun_when_severity_meets_threshold"));
        assertTrue(tests.contains("test_evaluate_denies_dq_rerun_when_severity_below_threshold"));
        assertTrue(tests.contains("test_evaluate_defaults_deny_on_unknown_input"));
        assertTrue(tests.contains("decision.code == \"POL-OS-001\""));
        assertTrue(tests.contains("decision.reason == \"POL-OS-001: allow\""));
        assertTrue(tests.contains("decision.reason == \"POL-OS-001: auto-trigger denied for WORKFLOW_ROUTE because severity INFO is below threshold WARN\""));
        assertTrue(tests.contains("decision.reason == \"POL-OS-001: auto-trigger denied for DQ_RERUN because severity INFO is below threshold WARN\""));
        assertTrue(tests.contains("decision.reason == \"POL-OS-001: unknown or invalid input\""));
    }

    private static String read(String path) throws IOException {
        return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
    }
}
