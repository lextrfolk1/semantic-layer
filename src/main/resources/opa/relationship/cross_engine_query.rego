package lextr.semantic.cross_engine_query

default evaluate := {
    "allowed": false,
    "code": "POL-CE-003",
    "message": "POL-CE-003: unknown or invalid input"
}

evaluate := {
    "allowed": true,
    "code": "POL-CE-003",
    "message": "POL-CE-003: allow"
} if {
    valid_input
    not cross_engine_detected
}

evaluate := {
    "allowed": false,
    "code": "POL-CE-003",
    "message": sprintf(
        "POL-CE-003: cross-engine query execution is not allowed for query_cd %s",
        [input.query_cd]
    )
} if {
    valid_input
    cross_engine_detected
}

valid_input if {
    is_string(input.client_id)
    input.client_id != ""
    is_string(input.query_cd)
    input.query_cd != ""
    is_boolean(input.is_cross_engine_query_flg)
}

cross_engine_detected if {
    input.is_cross_engine_query_flg
}
