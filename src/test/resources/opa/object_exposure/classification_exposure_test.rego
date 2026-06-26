package lextr.semantic.object_exposure.classification_test

import data.lextr.semantic.object_exposure.classification

test_evaluate_allows_non_sensitive_object_exposure if {
    decision := classification.evaluate with input as {
        "policy_cd": "POL-DC-001",
        "request_type_cd": "LIST",
        "client_id": "client-a",
        "actor_id": "analyst-1",
        "role_cd": "FINANCE",
        "purpose_cd": "REPORTING",
        "object_id": "00000000-0000-0000-0000-000000000101",
        "schema_cd": "meta",
        "object_cd": "GL_BALANCE",
        "object_data_classification_cd": "INTERNAL",
        "object_pii_flg": false,
        "object_confidential_flg": false,
        "attribute_cd": null,
        "attribute_data_classification_cd": null,
        "attribute_pii_flg": false,
        "attribute_confidential_flg": false,
        "masking_policy_cd": null,
        "mnpi_flg": false,
        "csi_flg": false,
        "ai_exposure_cd": null,
        "taxonomy_jurisdiction_cd": null
    }

    decision.allowed
    not decision.masked
    not decision.withheld
    decision.code == "POL-DC-001"
    contains(decision.reason, "POL-DC-001")
}

test_evaluate_masks_restricted_attribute_when_masking_policy_present if {
    decision := classification.evaluate with input as {
        "policy_cd": "POL-DC-001",
        "request_type_cd": "DETAIL",
        "client_id": "client-a",
        "actor_id": "analyst-1",
        "role_cd": "FINANCE",
        "purpose_cd": "REPORTING",
        "object_id": "00000000-0000-0000-0000-000000000101",
        "schema_cd": "meta",
        "object_cd": "GL_BALANCE",
        "object_data_classification_cd": "INTERNAL",
        "object_pii_flg": false,
        "object_confidential_flg": false,
        "attribute_cd": "ACCOUNT_NO",
        "attribute_data_classification_cd": "RESTRICTED",
        "attribute_pii_flg": true,
        "attribute_confidential_flg": true,
        "masking_policy_cd": "MASK_FULL",
        "mnpi_flg": false,
        "csi_flg": false,
        "ai_exposure_cd": "RESTRICTED",
        "taxonomy_jurisdiction_cd": "US"
    }

    decision.allowed
    decision.masked
    not decision.withheld
    decision.mask_value_txt == "REDACTED"
    decision.code == "POL-DC-001"
    contains(decision.reason, "POL-DC-001")
    decision.masked_fields == ["attribute_nm", "taxonomy_cd", "taxonomy_source_cd", "taxonomy_jurisdiction_cd"]
}

test_evaluate_withholds_restricted_attribute_when_mnpi_or_csi_present if {
    decision := classification.evaluate with input as {
        "policy_cd": "POL-DC-001",
        "request_type_cd": "DETAIL",
        "client_id": "client-a",
        "actor_id": "analyst-1",
        "role_cd": "FINANCE",
        "purpose_cd": "REPORTING",
        "object_id": "00000000-0000-0000-0000-000000000101",
        "schema_cd": "meta",
        "object_cd": "GL_BALANCE",
        "object_data_classification_cd": "CONFIDENTIAL",
        "object_pii_flg": false,
        "object_confidential_flg": true,
        "attribute_cd": "INSIDER_CODE",
        "attribute_data_classification_cd": "RESTRICTED",
        "attribute_pii_flg": false,
        "attribute_confidential_flg": true,
        "masking_policy_cd": null,
        "mnpi_flg": true,
        "csi_flg": false,
        "ai_exposure_cd": "ALLOWED",
        "taxonomy_jurisdiction_cd": "US"
    }

    decision.allowed
    not decision.masked
    decision.withheld
    decision.code == "POL-DC-001"
    contains(decision.reason, "POL-DC-001")
    contains(decision.reason, "withheld")
}

test_evaluate_blocks_ai_principal_from_blocked_ai_exposure if {
    decision := classification.evaluate with input as {
        "policy_cd": "POL-DC-001",
        "request_type_cd": "DETAIL",
        "client_id": "client-a",
        "actor_id": "assistant-1",
        "role_cd": "AI_AGENT",
        "purpose_cd": "AI_ASSIST",
        "object_id": "00000000-0000-0000-0000-000000000101",
        "schema_cd": "meta",
        "object_cd": "GL_BALANCE",
        "object_data_classification_cd": "CONFIDENTIAL",
        "object_pii_flg": false,
        "object_confidential_flg": true,
        "attribute_cd": "ACCOUNT_NO",
        "attribute_data_classification_cd": "CONFIDENTIAL",
        "attribute_pii_flg": true,
        "attribute_confidential_flg": true,
        "masking_policy_cd": "MASK_FULL",
        "mnpi_flg": false,
        "csi_flg": false,
        "ai_exposure_cd": "BLOCKED",
        "taxonomy_jurisdiction_cd": "US"
    }

    not decision.allowed
    not decision.masked
    decision.withheld
    decision.code == "POL-DC-001"
    contains(decision.reason, "POL-DC-001")
    contains(decision.reason, "AI exposure is blocked")
}

test_evaluate_masks_ai_principal_when_ai_exposure_is_restricted if {
    decision := classification.evaluate with input as {
        "policy_cd": "POL-DC-001",
        "request_type_cd": "DETAIL",
        "client_id": "client-a",
        "actor_id": "assistant-1",
        "role_cd": "AI_AGENT",
        "purpose_cd": "AI_ASSIST",
        "object_id": "00000000-0000-0000-0000-000000000101",
        "schema_cd": "meta",
        "object_cd": "GL_BALANCE",
        "object_data_classification_cd": "INTERNAL",
        "object_pii_flg": false,
        "object_confidential_flg": false,
        "attribute_cd": "AMOUNT",
        "attribute_data_classification_cd": "INTERNAL",
        "attribute_pii_flg": false,
        "attribute_confidential_flg": false,
        "masking_policy_cd": "MASK_PARTIAL",
        "mnpi_flg": false,
        "csi_flg": false,
        "ai_exposure_cd": "RESTRICTED",
        "taxonomy_jurisdiction_cd": "US"
    }

    decision.allowed
    decision.masked
    not decision.withheld
    decision.code == "POL-DC-001"
    contains(decision.reason, "POL-DC-001")
}

test_evaluate_defaults_deny_on_unknown_input if {
    decision := classification.evaluate with input as {
        "policy_cd": "POL-DC-999",
        "request_type_cd": "DETAIL",
        "client_id": "client-a",
        "actor_id": "analyst-1",
        "role_cd": "FINANCE",
        "purpose_cd": "REPORTING",
        "object_id": "00000000-0000-0000-0000-000000000101",
        "schema_cd": "meta",
        "object_cd": "GL_BALANCE",
        "object_data_classification_cd": "INTERNAL",
        "object_pii_flg": false,
        "object_confidential_flg": false,
        "attribute_cd": null,
        "attribute_data_classification_cd": null,
        "attribute_pii_flg": false,
        "attribute_confidential_flg": false,
        "masking_policy_cd": null,
        "mnpi_flg": false,
        "csi_flg": false,
        "ai_exposure_cd": null,
        "taxonomy_jurisdiction_cd": null
    }

    not decision.allowed
    not decision.masked
    not decision.withheld
    decision.code == "POL-DC-001"
    decision.reason == "POL-DC-001: unknown or invalid input"
    decision.message == "POL-DC-001: unknown or invalid input"
}
