package com.lextr.semanticlayer.policy;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ConsumptionLayerPromotionPolicyAssetsTest {

    @Test
    void policyAssetProvidesSingleEvaluateEntryPointAndReasonCode() throws IOException {
        String policy = read("opa/consumption_layer/promotion_gate.rego");

        assertTrue(policy.contains("package lextr.semantic.consumption_layer"));
        assertTrue(policy.contains("default evaluate :="));
        assertTrue(policy.contains("\"code\": \"POL-CL-001\""));
        assertTrue(policy.contains("\"reason\": \"POL-CL-001: unknown or invalid input\""));
        assertTrue(policy.contains("input.single_engine_flg == true"));
        assertTrue(policy.contains("upper(input.governance_status_cd) == \"APPROVED\""));
        assertTrue(policy.contains("input.approved_by != \"\""));
        assertTrue(policy.contains("input.approved_ts != null"));
    }

    @Test
    void regoTestAssetCoversAllowAndDenyBranches() throws IOException {
        String tests = read("opa/consumption_layer/promotion_gate_test.rego");

        assertTrue(tests.contains("package lextr.semantic.consumption_layer_test"));
        assertTrue(tests.contains("import data.lextr.semantic.consumption_layer"));
        assertTrue(tests.contains("test_evaluate_allows_single_engine_promotion_with_governance_approval"));
        assertTrue(tests.contains("test_evaluate_denies_multi_engine_promotion"));
        assertTrue(tests.contains("test_evaluate_denies_unapproved_promotion_when_approval_missing"));
        assertTrue(tests.contains("test_evaluate_denies_when_governance_state_not_approved"));
        assertTrue(tests.contains("test_evaluate_defaults_deny_on_unknown_input"));
        assertTrue(tests.contains("decision.code == \"POL-CL-001\""));
        assertTrue(tests.contains("decision.reason == \"POL-CL-001: allow\""));
        assertTrue(tests.contains("decision.reason == \"POL-CL-001: unknown or invalid input\""));
    }

    private static String read(String path) throws IOException {
        return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
    }
}
