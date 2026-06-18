package lextr.semantic.filter_lookup_certification_test

import data.lextr.semantic.filter_lookup_certification

test_evaluate_allows_certification_when_no_stale_values_exist if {
    decision := filter_lookup_certification.evaluate with input as {
        "client_id": "client-a",
        "lookup_cd": "LEDGER_SCOPE",
        "certified_by": "certifier",
        "current_health_status_cd": "PENDING",
        "stale_value_count": 0
    }

    decision.allowed
    decision.code == "POL-SV-001"
    contains(decision.message, "POL-SV-001")
}

test_evaluate_denies_certification_when_stale_values_exist if {
    decision := filter_lookup_certification.evaluate with input as {
        "client_id": "client-a",
        "lookup_cd": "LEDGER_SCOPE",
        "certified_by": "certifier",
        "current_health_status_cd": "PENDING",
        "stale_value_count": 3
    }

    not decision.allowed
    decision.code == "POL-SV-001"
    contains(decision.message, "POL-SV-001")
    contains(decision.message, "LEDGER_SCOPE")
    contains(decision.message, "3")
    contains(decision.message, "inactive in source")
}

test_evaluate_defaults_deny_on_unknown_input if {
    decision := filter_lookup_certification.evaluate with input as {
        "client_id": "client-a",
        "lookup_cd": "LEDGER_SCOPE",
        "certified_by": "",
        "current_health_status_cd": "PENDING",
        "stale_value_count": -1
    }

    not decision.allowed
    decision.code == "POL-SV-001"
    decision.message == "POL-SV-001: unknown or invalid input"
}
