package lextr.semantic.relationship

default evaluate := {
    "allowed": false,
    "code": "POL-CE-001",
    "message": "POL-CE-001: unknown or invalid input"
}

evaluate := {
    "allowed": true,
    "code": "POL-CE-001",
    "message": "POL-CE-001: allow"
} if {
    valid_input
    not cross_engine_detected
}

evaluate := {
    "allowed": false,
    "code": "POL-CE-001",
    "message": sprintf(
        "POL-CE-001: cross-engine relationships are not allowed between %s and %s",
        [input.parent_engine_cd, input.child_engine_cd]
    )
} if {
    valid_input
    cross_engine_detected
}

valid_input if {
    is_string(input.client_id)
    input.client_id != ""
    is_string(input.relationship_cd)
    input.relationship_cd != ""
    is_string(input.parent_engine_cd)
    input.parent_engine_cd != ""
    allowed_engine[input.parent_engine_cd]
    is_string(input.child_engine_cd)
    input.child_engine_cd != ""
    allowed_engine[input.child_engine_cd]
    input.is_cross_engine_flg == true
}

valid_input if {
    is_string(input.client_id)
    input.client_id != ""
    is_string(input.relationship_cd)
    input.relationship_cd != ""
    is_string(input.parent_engine_cd)
    input.parent_engine_cd != ""
    allowed_engine[input.parent_engine_cd]
    is_string(input.child_engine_cd)
    input.child_engine_cd != ""
    allowed_engine[input.child_engine_cd]
    input.is_cross_engine_flg == false
}

cross_engine_detected if {
    input.is_cross_engine_flg
}

cross_engine_detected if {
    input.parent_engine_cd != input.child_engine_cd
}

allowed_engine["POSTGRES"]
allowed_engine["CLICKHOUSE"]
allowed_engine["NEO4J"]
