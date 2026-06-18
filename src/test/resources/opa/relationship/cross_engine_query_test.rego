package lextr.semantic.cross_engine_query_test

import data.lextr.semantic.cross_engine_query

test_evaluate_allows_non_cross_engine_query if {
    decision := cross_engine_query.evaluate with input as {
        "client_id": "client-a",
        "query_cd": "GET_CUSTOMER_DATA",
        "is_cross_engine_query_flg": false
    }

    decision.allowed
    decision.code == "POL-CE-003"
    contains(decision.message, "POL-CE-003")
}

test_evaluate_denies_cross_engine_query if {
    decision := cross_engine_query.evaluate with input as {
        "client_id": "client-a",
        "query_cd": "GET_CUSTOMER_DATA",
        "is_cross_engine_query_flg": true
    }

    not decision.allowed
    decision.code == "POL-CE-003"
    contains(decision.message, "POL-CE-003")
    contains(decision.message, "cross-engine query execution is not allowed")
}

test_evaluate_defaults_deny_on_invalid_input if {
    decision := cross_engine_query.evaluate with input as {
        "client_id": "",
        "query_cd": "GET_CUSTOMER_DATA",
        "is_cross_engine_query_flg": false
    }

    not decision.allowed
    decision.code == "POL-CE-003"
    decision.message == "POL-CE-003: unknown or invalid input"
}
