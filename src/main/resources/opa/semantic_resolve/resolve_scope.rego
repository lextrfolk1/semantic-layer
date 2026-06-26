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
        "POL-RS-001: resolve denied for non-engine principal role %s and purpose %s",
        [input.role_cd, input.purpose_cd]
    ),
    "message": sprintf(
        "POL-RS-001: resolve denied for non-engine principal role %s and purpose %s",
        [input.role_cd, input.purpose_cd]
    )
} if {
    valid_input
    not cross_tenant_access
    not authorized_engine_principal
}

evaluate := {
    "allowed": true,
    "code": "POL-RS-001",
    "reason": "POL-RS-001: allow",
    "message": "POL-RS-001: allow"
} if {
    valid_input
    not cross_tenant_access
    authorized_engine_principal
}

valid_input if {
    input.policy_cd == "POL-RS-001"
    valid_request_type
    is_string(input.client_id)
    input.client_id != ""
    is_string(input.resource_ref_txt)
    input.resource_ref_txt != ""
    role_and_purpose_present
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

role_and_purpose_present if {
    is_string(input.role_cd)
    input.role_cd != ""
    is_string(input.purpose_cd)
    input.purpose_cd != ""
}

authorized_engine_principal if {
    engine_principal
    resolution_purpose
}

engine_principal if {
    upper(input.role_cd) == "ENGINE"
}

resolution_purpose if {
    upper(input.purpose_cd) == "RESOLUTION"
}

cross_tenant_access if {
    is_string(input.resource_client_id)
    input.resource_client_id != ""
    input.client_id != input.resource_client_id
}
