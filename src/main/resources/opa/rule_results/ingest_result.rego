package lextr.semantic.rule_results

default evaluate := {
    "allowed": false,
    "code": "POL-RR-001",
    "reason": "POL-RR-001: unknown or invalid input",
    "message": "POL-RR-001: unknown or invalid input"
}

evaluate := {
    "allowed": true,
    "code": "POL-RR-001",
    "reason": "POL-RR-001: allow",
    "message": "POL-RR-001: allow"
} if {
    valid_input
    engine_principal
}

evaluate := {
    "allowed": false,
    "code": "POL-RR-001",
    "reason": sprintf(
        "POL-RR-001: rule result ingest denied for non-engine principal %s",
        [input.principal_cd]
    ),
    "message": sprintf(
        "POL-RR-001: rule result ingest denied for non-engine principal %s",
        [input.principal_cd]
    )
} if {
    valid_input
    not engine_principal
}

valid_input if {
    input.policy_cd == "POL-RR-001"
    input.request_type_cd == "RULE_RESULT"
    is_string(input.client_id)
    input.client_id != ""
    is_number(input.outbound_id)
    is_string(input.rule_ref_cd)
    input.rule_ref_cd != ""
    is_string(input.output_kind_cd)
    input.output_kind_cd != ""
}

engine_principal if {
    is_string(input.principal_cd)
    upper(input.principal_cd) == "ENGINE"
}
