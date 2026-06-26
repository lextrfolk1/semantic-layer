package lextr.semantic.dq_request_observe_test

import data.lextr.semantic.dq_request_observe

test_evaluate_allows_request_when_tenant_scope_present if {
    decision := dq_request_observe.evaluate with input as {
        "request_type_cd": "REQUEST",
        "client_id": "client-a",
        "requested_by": "steward-1"
    }

    decision.allowed
    decision.code == "POL-DQ-001"
    contains(decision.reason, "POL-DQ-001")
}

test_evaluate_denies_request_without_tenant_scope if {
    decision := dq_request_observe.evaluate with input as {
        "request_type_cd": "REQUEST",
        "client_id": "",
        "requested_by": "steward-1"
    }

    not decision.allowed
    decision.code == "POL-DQ-001"
    contains(decision.reason, "tenant scope is required for DQ requests")
}

test_evaluate_allows_result_post_when_principal_is_engine if {
    decision := dq_request_observe.evaluate with input as {
        "request_type_cd": "RESULT",
        "client_id": "client-a",
        "principal_cd": "ENGINE"
    }

    decision.allowed
    decision.code == "POL-DQ-001"
    contains(decision.reason, "POL-DQ-001")
}

test_evaluate_denies_result_post_when_principal_is_not_engine if {
    decision := dq_request_observe.evaluate with input as {
        "request_type_cd": "RESULT",
        "client_id": "client-a",
        "principal_cd": "ANALYST"
    }

    not decision.allowed
    decision.code == "POL-DQ-001"
    contains(decision.reason, "POL-DQ-001")
    contains(decision.reason, "only engine principal may post results")
}

test_evaluate_defaults_deny_on_unknown_input if {
    decision := dq_request_observe.evaluate with input as {
        "request_type_cd": "BOGUS",
        "client_id": "client-a"
    }

    not decision.allowed
    decision.code == "POL-DQ-001"
    decision.reason == "POL-DQ-001: unknown or invalid input"
    decision.message == "POL-DQ-001: unknown or invalid input"
}
