package lextr.semantic.object_exposure.classification

default evaluate := {
    "allowed": false,
    "masked": false,
    "withheld": false,
    "mask_value_txt": null,
    "masked_fields": [],
    "code": "POL-DC-001",
    "reason": "POL-DC-001: unknown or invalid input",
    "message": "POL-DC-001: unknown or invalid input"
}

evaluate := {
    "allowed": false,
    "masked": false,
    "withheld": true,
    "mask_value_txt": null,
    "masked_fields": [],
    "code": "POL-DC-001",
    "reason": "POL-DC-001: AI exposure is blocked for this class",
    "message": "POL-DC-001: AI exposure is blocked for this class"
} if {
    valid_input
    ai_blocked
}

evaluate := {
    "allowed": true,
    "masked": false,
    "withheld": true,
    "mask_value_txt": null,
    "masked_fields": [],
    "code": "POL-DC-001",
    "reason": "POL-DC-001: restricted classification is withheld",
    "message": "POL-DC-001: restricted classification is withheld"
} if {
    valid_input
    not ai_blocked
    withhold_required
}

evaluate := {
    "allowed": true,
    "masked": true,
    "withheld": false,
    "mask_value_txt": "REDACTED",
    "masked_fields": masked_fields_for_request,
    "code": "POL-DC-001",
    "reason": "POL-DC-001: classification requires masked exposure",
    "message": "POL-DC-001: classification requires masked exposure"
} if {
    valid_input
    not ai_blocked
    not withhold_required
    mask_required
}

evaluate := {
    "allowed": true,
    "masked": false,
    "withheld": false,
    "mask_value_txt": null,
    "masked_fields": [],
    "code": "POL-DC-001",
    "reason": "POL-DC-001: allow",
    "message": "POL-DC-001: allow"
} if {
    valid_input
    not ai_blocked
    not withhold_required
    not mask_required
}

valid_input if {
    input.policy_cd == "POL-DC-001"
    valid_request_type
    is_string(input.client_id)
    input.client_id != ""
    is_string(input.schema_cd)
    input.schema_cd != ""
    is_string(input.object_cd)
    input.object_cd != ""
    input.object_id != null
    is_string(input.object_data_classification_cd)
    input.object_data_classification_cd != ""
    role_field_present
    purpose_field_present
    valid_attribute_context
}

valid_request_type if {
    input.request_type_cd == "LIST"
}

valid_request_type if {
    input.request_type_cd == "DETAIL"
}

role_field_present if {
    is_string(input.role_cd)
}

purpose_field_present if {
    is_string(input.purpose_cd)
}

valid_attribute_context if {
    object_level_request
}

valid_attribute_context if {
    attribute_request
}

object_level_request if {
    input.attribute_cd == null
}

attribute_request if {
    is_string(input.attribute_cd)
    input.attribute_cd != ""
}

effective_classification_cd := input.attribute_data_classification_cd if {
    attribute_request
    is_string(input.attribute_data_classification_cd)
    input.attribute_data_classification_cd != ""
}

effective_classification_cd := input.object_data_classification_cd if {
    not attribute_request
}

effective_classification_cd := input.object_data_classification_cd if {
    attribute_request
    not is_string(input.attribute_data_classification_cd)
}

effective_classification_cd := input.object_data_classification_cd if {
    attribute_request
    is_string(input.attribute_data_classification_cd)
    input.attribute_data_classification_cd == ""
}

ai_principal if {
    is_string(input.role_cd)
    contains(upper(input.role_cd), "AI")
}

ai_principal if {
    is_string(input.purpose_cd)
    contains(upper(input.purpose_cd), "AI")
}

effective_ai_exposure_cd := upper(input.ai_exposure_cd) if {
    is_string(input.ai_exposure_cd)
    input.ai_exposure_cd != ""
}

effective_ai_exposure_cd := "ALLOWED" if {
    not is_string(input.ai_exposure_cd)
}

effective_ai_exposure_cd := "ALLOWED" if {
    is_string(input.ai_exposure_cd)
    input.ai_exposure_cd == ""
}

ai_blocked if {
    ai_principal
    effective_ai_exposure_cd == "BLOCKED"
}

withhold_required if {
    input.mnpi_flg
}

withhold_required if {
    input.csi_flg
}

withhold_required if {
    upper(effective_classification_cd) == "RESTRICTED"
    not masking_policy_present
}

mask_required if {
    upper(effective_classification_cd) == "RESTRICTED"
    masking_policy_present
}

mask_required if {
    effective_pii_flg
    masking_policy_present
}

mask_required if {
    effective_confidential_flg
    masking_policy_present
}

mask_required if {
    ai_principal
    effective_ai_exposure_cd == "RESTRICTED"
}

masking_policy_present if {
    is_string(input.masking_policy_cd)
    input.masking_policy_cd != ""
}

effective_pii_flg if {
    attribute_request
    input.attribute_pii_flg
}

effective_pii_flg if {
    not attribute_request
    input.object_pii_flg
}

effective_confidential_flg if {
    attribute_request
    input.attribute_confidential_flg
}

effective_confidential_flg if {
    not attribute_request
    input.object_confidential_flg
}

masked_fields_for_request := ["attribute_nm", "taxonomy_cd", "taxonomy_source_cd", "taxonomy_jurisdiction_cd"] if {
    attribute_request
}

masked_fields_for_request := ["object_nm"] if {
    object_level_request
}
