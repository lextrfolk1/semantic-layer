package lextr.semantic.consumption_layer_test

import data.lextr.semantic.consumption_layer

test_evaluate_allows_single_engine_promotion_with_governance_approval if {
    decision := consumption_layer.evaluate with input as {
        "policy_cd": "POL-CL-001",
        "client_id": "client-a",
        "source_sdlc_status_cd": "DEV",
        "target_sdlc_status_cd": "QA",
        "single_engine_flg": true,
        "governance_status_cd": "APPROVED",
        "approved_by": "governance-owner",
        "approved_ts": "2026-06-20T10:15:30Z"
    }

    decision.allowed
    decision.code == "POL-CL-001"
    decision.reason == "POL-CL-001: allow"
    decision.message == "POL-CL-001: allow"
}

test_evaluate_denies_multi_engine_promotion if {
    decision := consumption_layer.evaluate with input as {
        "policy_cd": "POL-CL-001",
        "client_id": "client-a",
        "source_sdlc_status_cd": "DEV",
        "target_sdlc_status_cd": "QA",
        "single_engine_flg": false,
        "governance_status_cd": "APPROVED",
        "approved_by": "governance-owner",
        "approved_ts": "2026-06-20T10:15:30Z"
    }

    not decision.allowed
    decision.code == "POL-CL-001"
    contains(decision.reason, "POL-CL-001")
    contains(decision.reason, "single-engine promotion gate denied")
}

test_evaluate_denies_unapproved_promotion_when_approval_missing if {
    decision := consumption_layer.evaluate with input as {
        "policy_cd": "POL-CL-001",
        "client_id": "client-a",
        "source_sdlc_status_cd": "DEV",
        "target_sdlc_status_cd": "QA",
        "single_engine_flg": true,
        "governance_status_cd": "APPROVED",
        "approved_by": null,
        "approved_ts": "2026-06-20T10:15:30Z"
    }

    not decision.allowed
    decision.code == "POL-CL-001"
    contains(decision.reason, "POL-CL-001")
    contains(decision.reason, "governance approval missing or incomplete")
}

test_evaluate_denies_when_governance_state_not_approved if {
    decision := consumption_layer.evaluate with input as {
        "policy_cd": "POL-CL-001",
        "client_id": "client-a",
        "source_sdlc_status_cd": "DEV",
        "target_sdlc_status_cd": "QA",
        "single_engine_flg": true,
        "governance_status_cd": "REVIEW",
        "approved_by": "governance-owner",
        "approved_ts": "2026-06-20T10:15:30Z"
    }

    not decision.allowed
    decision.code == "POL-CL-001"
    contains(decision.reason, "POL-CL-001")
    contains(decision.reason, "governance state must be APPROVED")
}

test_evaluate_defaults_deny_on_unknown_input if {
    decision := consumption_layer.evaluate with input as {
        "policy_cd": "POL-CL-999",
        "client_id": "client-a",
        "source_sdlc_status_cd": "DEV",
        "target_sdlc_status_cd": "QA",
        "single_engine_flg": true,
        "governance_status_cd": "APPROVED",
        "approved_by": "governance-owner",
        "approved_ts": "2026-06-20T10:15:30Z"
    }

    not decision.allowed
    decision.code == "POL-CL-001"
    decision.reason == "POL-CL-001: unknown or invalid input"
    decision.message == "POL-CL-001: unknown or invalid input"
}
