package lextr.semantic.workflow_approval_test

import data.lextr.semantic.workflow_approval

test_evaluate_allows_when_approver_differs_from_submitter if {
    decision := workflow_approval.evaluate with input as {
        "client_id": "client-a",
        "task_id": 301,
        "task_type_cd": "FILTER_LOOKUP_REGISTRATION",
        "entity_type_cd": "FILTER_LOOKUP",
        "entity_ref": "LEDGER_SCOPE",
        "submitted_by": "producer",
        "approved_by": "approver"
    }

    decision.allowed == true
    decision.code == "POL-SV-003"
}

test_evaluate_denies_self_approval if {
    decision := workflow_approval.evaluate with input as {
        "client_id": "client-a",
        "task_id": 301,
        "task_type_cd": "FILTER_LOOKUP_REGISTRATION",
        "entity_type_cd": "FILTER_LOOKUP",
        "entity_ref": "LEDGER_SCOPE",
        "submitted_by": "producer",
        "approved_by": "producer"
    }

    decision.allowed == false
    decision.code == "POL-SV-003"
}

test_evaluate_defaults_deny_on_unknown_input if {
    decision := workflow_approval.evaluate with input as {}

    decision.allowed == false
    decision.code == "POL-SV-003"
}
