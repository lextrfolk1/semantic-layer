package lextr.semantic.filter_lookup_certification

default evaluate := {
    "allowed": false,
    "code": "POL-SV-001",
    "message": "POL-SV-001: unknown or invalid input"
}

evaluate := {
    "allowed": true,
    "code": "POL-SV-001",
    "message": "POL-SV-001: allow"
} if {
    valid_input
    no_stale_values
}

evaluate := {
    "allowed": false,
    "code": "POL-SV-001",
    "message": sprintf(
        "POL-SV-001: certification blocked for %s because %d values are inactive in source",
        [input.lookup_cd, input.stale_value_count]
    )
} if {
    valid_input
    has_stale_values
}

valid_input if {
    is_string(input.client_id)
    input.client_id != ""
    is_string(input.lookup_cd)
    input.lookup_cd != ""
    is_string(input.certified_by)
    input.certified_by != ""
    is_string(input.current_health_status_cd)
    input.current_health_status_cd != ""
    is_number(input.stale_value_count)
    input.stale_value_count >= 0
}

no_stale_values if {
    input.stale_value_count == 0
}

has_stale_values if {
    input.stale_value_count > 0
}
