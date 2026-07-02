package lextr.semantic.rule_results_test

import data.lextr.semantic.rule_results

test_evaluate_allows_engine_principal_rule_result_ingest if {
    decision := rule_results.evaluate with input as {
        "policy_cd": "POL-RR-001",
        "request_type_cd": "RULE_RESULT",
        "client_id": "client-a",
        "outbound_id": 101,
        "rule_ref_cd": "RULE-100",
        "output_kind_cd": "VALUE",
        "principal_cd": "ENGINE"
    }

    decision.allowed == true
    decision.code == "POL-RR-001"
    decision.reason == "POL-RR-001: allow"
}

test_evaluate_denies_non_engine_principal_rule_result_ingest if {
    decision := rule_results.evaluate with input as {
        "policy_cd": "POL-RR-001",
        "request_type_cd": "RULE_RESULT",
        "client_id": "client-a",
        "outbound_id": 101,
        "rule_ref_cd": "RULE-100",
        "output_kind_cd": "EDITCHECK",
        "principal_cd": "ANALYST"
    }

    decision.allowed == false
    decision.code == "POL-RR-001"
    decision.reason == "POL-RR-001: rule result ingest denied for non-engine principal ANALYST"
}

test_evaluate_denies_rule_result_ingest_without_tenant_scope if {
    decision := rule_results.evaluate with input as {
        "policy_cd": "POL-RR-001",
        "request_type_cd": "RULE_RESULT",
        "outbound_id": 101,
        "rule_ref_cd": "RULE-100",
        "output_kind_cd": "VALUE",
        "principal_cd": "ENGINE"
    }

    decision.allowed == false
    decision.code == "POL-RR-001"
    decision.reason == "POL-RR-001: tenant scope is required for rule result ingest"
}

test_evaluate_defaults_deny_on_unknown_input if {
    decision := rule_results.evaluate with input as {"policy_cd": "POL-RR-001"}

    decision.allowed == false
    decision.code == "POL-RR-001"
    decision.reason == "POL-RR-001: unknown or invalid input"
}
