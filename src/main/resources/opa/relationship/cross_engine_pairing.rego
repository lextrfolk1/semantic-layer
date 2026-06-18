package lextr.semantic.attribute_pairing

default evaluate := {
    "allowed": false,
    "code": "POL-CE-002",
    "message": "POL-CE-002: unknown or invalid input"
}

evaluate := {
    "allowed": true,
    "code": "POL-CE-002",
    "message": "POL-CE-002: allow"
} if {
    valid_input
    not cross_engine_detected
}

evaluate := {
    "allowed": false,
    "code": "POL-CE-002",
    "message": sprintf(
        "POL-CE-002: cross-engine pairing is not allowed for pairing_cd %s",
        [input.pairing_cd]
    )
} if {
    valid_input
    cross_engine_detected
}

valid_input if {
    is_string(input.client_id)
    input.client_id != ""
    is_string(input.pairing_cd)
    input.pairing_cd != ""
    is_boolean(input.is_cross_engine_flg)
}

cross_engine_detected if {
    input.is_cross_engine_flg
}
