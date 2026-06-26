package lextr.semantic.dq_request_observe

default evaluate := {
    "allowed": false,
    "code": "POL-DQ-001",
    "reason": "POL-DQ-001: unknown or invalid input",
    "message": "POL-DQ-001: unknown or invalid input"
}

evaluate := {
    "allowed": true,
    "code": "POL-DQ-001",
    "reason": "POL-DQ-001: allow",
    "message": "POL-DQ-001: allow"
} if {
    request_operation
    tenant_scope_present
}

evaluate := {
    "allowed": false,
    "code": "POL-DQ-001",
    "reason": "POL-DQ-001: tenant scope is required for DQ requests",
    "message": "POL-DQ-001: tenant scope is required for DQ requests"
} if {
    request_operation
    not tenant_scope_present
}

evaluate := {
    "allowed": true,
    "code": "POL-DQ-001",
    "reason": "POL-DQ-001: allow",
    "message": "POL-DQ-001: allow"
} if {
    result_operation
    tenant_scope_present
    engine_principal_present
}

evaluate := {
    "allowed": false,
    "code": "POL-DQ-001",
    "reason": sprintf(
        "POL-DQ-001: only engine principal may post results; principal %s is denied",
        [input.principal_cd]
    ),
    "message": sprintf(
        "POL-DQ-001: only engine principal may post results; principal %s is denied",
        [input.principal_cd]
    )
} if {
    result_operation
    tenant_scope_present
    is_string(input.principal_cd)
    not engine_principal_present
}

request_operation if {
    input.request_type_cd == "REQUEST"
}

result_operation if {
    input.request_type_cd == "RESULT"
}

tenant_scope_present if {
    is_string(input.client_id)
    input.client_id != ""
}

engine_principal_present if {
    is_string(input.principal_cd)
    input.principal_cd == "ENGINE"
}
