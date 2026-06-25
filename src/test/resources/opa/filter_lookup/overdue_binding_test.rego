package lextr.semantic.filter_lookup_binding_test

import data.lextr.semantic.filter_lookup_binding

test_evaluate_allows_binding_when_not_overdue if {
    decision := filter_lookup_binding.evaluate with input as {
        "client_id": "client-a",
        "lookup_cd": "LEDGER_SCOPE",
        "binding_context_cd": "PIPELINE",
        "is_overdue": false
    }

    decision.allowed
    decision.code == "POL-SV-002"
    contains(decision.message, "POL-SV-002")
}

test_evaluate_allows_binding_when_overdue_but_query_studio if {
    decision := filter_lookup_binding.evaluate with input as {
        "client_id": "client-a",
        "lookup_cd": "LEDGER_SCOPE",
        "binding_context_cd": "QUERY_STUDIO",
        "is_overdue": true
    }

    decision.allowed
    decision.code == "POL-SV-002"
    contains(decision.message, "POL-SV-002")
}

test_evaluate_denies_binding_when_overdue_and_pipeline if {
    decision := filter_lookup_binding.evaluate with input as {
        "client_id": "client-a",
        "lookup_cd": "LEDGER_SCOPE",
        "binding_context_cd": "PIPELINE",
        "is_overdue": true
    }

    not decision.allowed
    decision.code == "POL-SV-002"
    contains(decision.message, "POL-SV-002")
    decision.message == "POL-SV-002: overdue lookup binding is blocked for PIPELINE"
}

test_evaluate_defaults_deny_on_invalid_input if {
    decision := filter_lookup_binding.evaluate with input as {
        "client_id": "client-a",
        "lookup_cd": "",
        "binding_context_cd": "PIPELINE",
        "is_overdue": true
    }

    not decision.allowed
    decision.code == "POL-SV-002"
    contains(decision.message, "POL-SV-002")
    decision.message == "POL-SV-002: unknown or invalid input"
}
