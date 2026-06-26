package com.lextr.semanticlayer.policy;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ObjectExposureClassificationPolicyAssetsTest {

    @Test
    void policyAssetProvidesSingleEvaluateEntryPointAndReasonCode() throws IOException {
        String policy = read("opa/object_exposure/classification_exposure.rego");

        assertTrue(policy.contains("package lextr.semantic.object_exposure.classification"));
        assertTrue(policy.contains("default evaluate :="));
        assertTrue(policy.contains("\"code\": \"POL-DC-001\""));
        assertTrue(policy.contains("\"reason\": \"POL-DC-001: unknown or invalid input\""));
        assertTrue(policy.contains("effective_ai_exposure_cd == \"BLOCKED\""));
        assertTrue(policy.contains("upper(effective_classification_cd) == \"RESTRICTED\""));
        assertTrue(policy.contains("\"masked_fields\": masked_fields_for_request"));
    }

    @Test
    void regoTestAssetCoversAllowMaskWithholdAndBlockedBranches() throws IOException {
        String tests = read("opa/object_exposure/classification_exposure_test.rego");

        assertTrue(tests.contains("package lextr.semantic.object_exposure.classification_test"));
        assertTrue(tests.contains("import data.lextr.semantic.object_exposure.classification"));
        assertTrue(tests.contains("test_evaluate_allows_non_sensitive_object_exposure"));
        assertTrue(tests.contains("test_evaluate_masks_restricted_attribute_when_masking_policy_present"));
        assertTrue(tests.contains("test_evaluate_withholds_restricted_attribute_when_mnpi_or_csi_present"));
        assertTrue(tests.contains("test_evaluate_blocks_ai_principal_from_blocked_ai_exposure"));
        assertTrue(tests.contains("test_evaluate_masks_ai_principal_when_ai_exposure_is_restricted"));
        assertTrue(tests.contains("test_evaluate_defaults_deny_on_unknown_input"));
        assertTrue(tests.contains("decision.code == \"POL-DC-001\""));
        assertTrue(tests.contains("decision.reason == \"POL-DC-001: unknown or invalid input\""));
    }

    private static String read(String path) throws IOException {
        return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
    }
}
