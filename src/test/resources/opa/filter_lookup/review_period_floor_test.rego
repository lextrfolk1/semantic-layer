package lextr.semantic.filter_lookup_test

import data.lextr.semantic.filter_lookup

test_evaluate_allows_when_override_absent if {
    decision := filter_lookup.evaluate with input as {
        "client_id": "client-a",
        "lookup_cd": "LEDGER_SCOPE",
        "policy_cd": "GOV-FL-001",
        "review_period_floor_days": 90,
        "review_period_days_override": null
    }

    decision.allowed
    decision.code == "GOV-FL-001"
    contains(decision.message, "GOV-FL-001")
}

test_evaluate_allows_when_override_is_stricter_than_floor if {
    decision := filter_lookup.evaluate with input as {
        "client_id": "client-a",
        "lookup_cd": "LEDGER_SCOPE",
        "policy_cd": "GOV-FL-001",
        "review_period_floor_days": 90,
        "review_period_days_override": 45
    }

    decision.allowed
    decision.code == "GOV-FL-001"
    contains(decision.message, "GOV-FL-001")
}

test_evaluate_denies_when_override_is_looser_than_floor if {
    decision := filter_lookup.evaluate with input as {
        "client_id": "client-a",
        "lookup_cd": "LEDGER_SCOPE",
        "policy_cd": "GOV-FL-001",
        "review_period_floor_days": 90,
        "review_period_days_override": 120
    }

    not decision.allowed
    decision.code == "GOV-FL-001"
    contains(decision.message, "GOV-FL-001")
    contains(decision.message, "120")
    contains(decision.message, "90")
}

test_evaluate_defaults_deny_on_unknown_input if {
    decision := filter_lookup.evaluate with input as {
        "client_id": "client-a",
        "lookup_cd": "LEDGER_SCOPE",
        "policy_cd": "GOV-FL-999",
        "review_period_floor_days": 90,
        "review_period_days_override": 45
    }

    not decision.allowed
    decision.code == "GOV-FL-001"
    decision.message == "GOV-FL-001: unknown or invalid input"
}
