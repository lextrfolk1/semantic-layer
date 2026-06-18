package lextr.semantic.attribute_pairing_test

import data.lextr.semantic.attribute_pairing

test_evaluate_allows_non_cross_engine_pairing if {
    decision := attribute_pairing.evaluate with input as {
        "client_id": "client-a",
        "pairing_cd": "GL_TO_LEDGER",
        "is_cross_engine_flg": false
    }

    decision.allowed
    decision.code == "POL-CE-002"
    contains(decision.message, "POL-CE-002")
}

test_evaluate_denies_cross_engine_pairing if {
    decision := attribute_pairing.evaluate with input as {
        "client_id": "client-a",
        "pairing_cd": "GL_TO_LEDGER",
        "is_cross_engine_flg": true
    }

    not decision.allowed
    decision.code == "POL-CE-002"
    contains(decision.message, "POL-CE-002")
    contains(decision.message, "cross-engine pairing is not allowed")
}

test_evaluate_defaults_deny_on_invalid_input if {
    decision := attribute_pairing.evaluate with input as {
        "client_id": "",
        "pairing_cd": "GL_TO_LEDGER",
        "is_cross_engine_flg": false
    }

    not decision.allowed
    decision.code == "POL-CE-002"
    decision.message == "POL-CE-002: unknown or invalid input"
}
