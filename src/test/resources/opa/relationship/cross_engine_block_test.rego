package lextr.semantic.relationship_test

import data.lextr.semantic.relationship

test_evaluate_allows_same_engine_relationship if {
    decision := relationship.evaluate with input as {
        "client_id": "client-a",
        "relationship_cd": "GL_TO_LEDGER",
        "parent_engine_cd": "POSTGRES",
        "child_engine_cd": "POSTGRES",
        "is_cross_engine_flg": false
    }

    decision.allowed
    decision.code == "POL-CE-001"
    contains(decision.message, "POL-CE-001")
}

test_evaluate_denies_flagged_cross_engine_relationship if {
    decision := relationship.evaluate with input as {
        "client_id": "client-a",
        "relationship_cd": "GL_TO_LEDGER",
        "parent_engine_cd": "POSTGRES",
        "child_engine_cd": "CLICKHOUSE",
        "is_cross_engine_flg": true
    }

    not decision.allowed
    decision.code == "POL-CE-001"
    contains(decision.message, "POL-CE-001")
    contains(decision.message, "cross-engine relationships are not allowed")
}

test_evaluate_denies_engine_mismatch_even_when_flag_false if {
    decision := relationship.evaluate with input as {
        "client_id": "client-a",
        "relationship_cd": "GL_TO_LEDGER",
        "parent_engine_cd": "POSTGRES",
        "child_engine_cd": "CLICKHOUSE",
        "is_cross_engine_flg": false
    }

    not decision.allowed
    decision.code == "POL-CE-001"
    contains(decision.message, "POL-CE-001")
    contains(decision.message, "POSTGRES")
    contains(decision.message, "CLICKHOUSE")
}

test_evaluate_defaults_deny_on_unknown_input if {
    decision := relationship.evaluate with input as {
        "client_id": "client-a",
        "relationship_cd": "GL_TO_LEDGER",
        "parent_engine_cd": "POSTGRES",
        "child_engine_cd": "SPARK",
        "is_cross_engine_flg": false
    }

    not decision.allowed
    decision.code == "POL-CE-001"
    decision.message == "POL-CE-001: unknown or invalid input"
}
