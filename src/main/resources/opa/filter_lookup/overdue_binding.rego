package lextr.semantic.filter_lookup_binding

default evaluate := {
    "allowed": false,
    "code": "POL-SV-002",
    "message": "POL-SV-002: unknown or invalid input"
}

evaluate := {
    "allowed": true,
    "code": "POL-SV-002",
    "message": "POL-SV-002: allow"
} if {
    valid_input
    not is_blocked
}

evaluate := {
    "allowed": false,
    "code": "POL-SV-002",
    "message": "POL-SV-002: overdue lookup binding is blocked for PIPELINE"
} if {
    valid_input
    is_blocked
}

valid_input if {
    is_string(input.client_id)
    input.client_id != ""
    is_string(input.lookup_cd)
    input.lookup_cd != ""
    is_string(input.binding_context_cd)
    input.binding_context_cd != ""
    is_boolean(input.is_overdue)
}

is_blocked if {
    input.is_overdue == true
    input.binding_context_cd == "PIPELINE"
}
