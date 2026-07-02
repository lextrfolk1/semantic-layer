package lextr.semantic.observability_signal

default evaluate := {
    "allowed": false,
    "code": "POL-OS-001",
    "reason": "POL-OS-001: unknown or invalid input",
    "message": "POL-OS-001: unknown or invalid input"
}

evaluate := {
    "allowed": true,
    "code": "POL-OS-001",
    "reason": "POL-OS-001: allow",
    "message": "POL-OS-001: allow"
} if {
    valid_input
    valid_trigger
    severity_rank[input.severity_cd] >= severity_rank[input.threshold_severity_cd]
}

evaluate := {
    "allowed": false,
    "code": "POL-OS-001",
    "reason": sprintf(
        "POL-OS-001: auto-trigger denied for %s because severity %s is below threshold %s",
        [input.trigger_cd, input.severity_cd, input.threshold_severity_cd]
    ),
    "message": sprintf(
        "POL-OS-001: auto-trigger denied for %s because severity %s is below threshold %s",
        [input.trigger_cd, input.severity_cd, input.threshold_severity_cd]
    )
} if {
    valid_input
    valid_trigger
    severity_rank[input.severity_cd] < severity_rank[input.threshold_severity_cd]
}

valid_input if {
    input.policy_cd == "POL-OS-001"
    is_string(input.client_id)
    input.client_id != ""
    is_string(input.signal_type_cd)
    input.signal_type_cd != ""
    is_string(input.trigger_cd)
    input.trigger_cd != ""
    is_string(input.severity_cd)
    input.severity_cd != ""
    is_string(input.threshold_severity_cd)
    input.threshold_severity_cd != ""
    severity_rank[input.severity_cd]
    severity_rank[input.threshold_severity_cd]
}

valid_trigger if {
    input.trigger_cd == "WORKFLOW_ROUTE"
}

valid_trigger if {
    input.trigger_cd == "DQ_RERUN"
}

severity_rank["HIGH"] := 3
severity_rank["WARN"] := 2
severity_rank["INFO"] := 1
