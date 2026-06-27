package lextr.semantic.workflow_approval

default evaluate := {
    "allowed": false,
    "code": "POL-SV-003",
    "message": "POL-SV-003: unknown or invalid input"
}

evaluate := {
    "allowed": true,
    "code": "POL-SV-003",
    "message": "POL-SV-003: allow"
} if {
    valid_input
    not self_approval
}

evaluate := {
    "allowed": false,
    "code": "POL-SV-003",
    "message": sprintf(
        "POL-SV-003: approver %s cannot be the same as submitter",
        [input.approved_by]
    )
} if {
    valid_input
    self_approval
}

valid_input if {
    is_string(input.client_id)
    input.client_id != ""
    is_number(input.task_id)
    input.task_id > 0
    is_string(input.task_type_cd)
    input.task_type_cd != ""
    is_string(input.entity_type_cd)
    input.entity_type_cd != ""
    is_string(input.entity_ref)
    input.entity_ref != ""
    is_string(input.submitted_by)
    input.submitted_by != ""
    is_string(input.approved_by)
    input.approved_by != ""
}

self_approval if {
    input.submitted_by == input.approved_by
}
