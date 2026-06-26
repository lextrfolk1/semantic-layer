package lextr.semantic.semantic_resolve

default evaluate := {
    "allowed": false,
    "code": "POL-RS-001",
    "reason": "POL-RS-001: unknown or invalid input",
    "message": "POL-RS-001: unknown or invalid input"
}

evaluate := {
    "allowed": false,
    "code": "POL-RS-001",
    "reason": sprintf(
        "POL-RS-001: cross-tenant resolve denied for actor client %s to resource client %s",
        [input.client_id, input.resource_client_id]
    ),
    "message": sprintf(
        "POL-RS-001: cross-tenant resolve denied for actor client %s to resource client %s",
        [input.client_id, input.resource_client_id]
    )
} if {
    valid_input
    cross_tenant_access
}

evaluate := {
    "allowed": false,
    "code": "POL-RS-001",
    "reason": sprintf(
        "POL-RS-001: resolve denied for role %s and purpose %s",
        [input.role_cd, input.purpose_cd]
    ),
    "message": sprintf(
        "POL-RS-001: resolve denied for role %s and purpose %s",
        [input.role_cd, input.purpose_cd]
    )
} if {
    valid_input
    not cross_tenant_access
    not role_and_purpose_present
}

evaluate := {
    "allowed": true,
    "code": "POL-RS-001",
    "reason": "POL-RS-001: allow",
    "message": "POL-RS-001: allow"
} if {
    valid_input
    not cross_tenant_access
    role_and_purpose_present
}

valid_input if {
    input.policy_cd == "POL-RS-001"
    valid_request_type
    is_string(input.client_id)
    input.client_id != ""
    is_string(input.resource_ref_txt)
    input.resource_ref_txt != ""
    role_field_present
    purpose_field_present
}

valid_request_type if {
    input.request_type_cd == "SEMANTIC"
}

valid_request_type if {
    input.request_type_cd == "CONSUMPTION"
}

valid_request_type if {
    input.request_type_cd == "RESOLVE"
}

role_field_present if {
    is_string(input.role_cd)
}

purpose_field_present if {
    is_string(input.purpose_cd)
}

role_and_purpose_present if {
    input.role_cd != ""
    input.purpose_cd != ""
}

cross_tenant_access if {
    is_string(input.resource_client_id)
    input.resource_client_id != ""
    input.client_id != input.resource_client_id
}
