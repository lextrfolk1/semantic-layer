package lextr.semantic.filter_lookup

default evaluate := {
    "allowed": false,
    "code": "GOV-FL-001",
    "message": "GOV-FL-001: unknown or invalid input"
}

evaluate := {
    "allowed": true,
    "code": "GOV-FL-001",
    "message": "GOV-FL-001: allow"
} if {
    valid_input
    override_absent
}

evaluate := {
    "allowed": true,
    "code": "GOV-FL-001",
    "message": "GOV-FL-001: allow"
} if {
    valid_input
    override_within_floor
}

evaluate := {
    "allowed": false,
    "code": "GOV-FL-001",
    "message": sprintf(
        "GOV-FL-001: review period override %d cannot be looser than governance floor %d",
        [input.review_period_days_override, input.review_period_floor_days]
    )
} if {
    valid_input
    not override_absent
    not override_within_floor
}

valid_input if {
    is_string(input.client_id)
    input.client_id != ""
    is_string(input.lookup_cd)
    input.lookup_cd != ""
    input.policy_cd == "GOV-FL-001"
    is_number(input.review_period_floor_days)
    input.review_period_floor_days > 0
}

override_absent if {
    input.review_period_days_override == null
}

override_within_floor if {
    is_number(input.review_period_days_override)
    input.review_period_days_override > 0
    input.review_period_days_override <= input.review_period_floor_days
}
