package lextr.semantic.consumption_layer

default evaluate := {
    "allowed": false,
    "code": "POL-CL-001",
    "reason": "POL-CL-001: unknown or invalid input",
    "message": "POL-CL-001: unknown or invalid input"
}

evaluate := {
    "allowed": false,
    "code": "POL-CL-001",
    "reason": "POL-CL-001: single-engine promotion gate denied",
    "message": "POL-CL-001: single-engine promotion gate denied"
} if {
    valid_input
    not single_engine_enforced
}

evaluate := {
    "allowed": false,
    "code": "POL-CL-001",
    "reason": "POL-CL-001: governance approval missing or incomplete",
    "message": "POL-CL-001: governance approval missing or incomplete"
} if {
    valid_input
    single_engine_enforced
    not governance_approval_present
}

evaluate := {
    "allowed": false,
    "code": "POL-CL-001",
    "reason": "POL-CL-001: governance state must be APPROVED",
    "message": "POL-CL-001: governance state must be APPROVED"
} if {
    valid_input
    single_engine_enforced
    governance_approval_present
    not approved_governance_state
}

evaluate := {
    "allowed": true,
    "code": "POL-CL-001",
    "reason": "POL-CL-001: allow",
    "message": "POL-CL-001: allow"
} if {
    valid_input
    single_engine_enforced
    governance_approval_present
    approved_governance_state
}

valid_input if {
    input.policy_cd == "POL-CL-001"
    is_string(input.client_id)
    input.client_id != ""
    is_string(input.source_sdlc_status_cd)
    input.source_sdlc_status_cd != ""
    is_string(input.target_sdlc_status_cd)
    input.target_sdlc_status_cd != ""
    is_boolean(input.single_engine_flg)
    is_string(input.governance_status_cd)
    input.governance_status_cd != ""
}

single_engine_enforced if {
    input.single_engine_flg == true
}

governance_approval_present if {
    is_string(input.approved_by)
    input.approved_by != ""
    input.approved_ts != null
}

approved_governance_state if {
    upper(input.governance_status_cd) == "APPROVED"
}
