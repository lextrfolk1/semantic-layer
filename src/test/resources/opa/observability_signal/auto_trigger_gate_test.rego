package lextr.semantic.observability_signal_test

import data.lextr.semantic.observability_signal

test_evaluate_allows_workflow_route_when_severity_meets_threshold if {
    decision := observability_signal.evaluate with input as {
        "policy_cd": "POL-OS-001",
        "client_id": "client-a",
        "signal_type_cd": "FRESHNESS",
        "trigger_cd": "WORKFLOW_ROUTE",
        "severity_cd": "WARN",
        "threshold_severity_cd": "WARN"
    }

    decision.allowed
    decision.code == "POL-OS-001"
    decision.reason == "POL-OS-001: allow"
}

test_evaluate_denies_workflow_route_when_severity_below_threshold if {
    decision := observability_signal.evaluate with input as {
        "policy_cd": "POL-OS-001",
        "client_id": "client-a",
        "signal_type_cd": "FRESHNESS",
        "trigger_cd": "WORKFLOW_ROUTE",
        "severity_cd": "INFO",
        "threshold_severity_cd": "WARN"
    }

    not decision.allowed
    decision.code == "POL-OS-001"
    decision.reason == "POL-OS-001: auto-trigger denied for WORKFLOW_ROUTE because severity INFO is below threshold WARN"
}

test_evaluate_allows_dq_rerun_when_severity_meets_threshold if {
    decision := observability_signal.evaluate with input as {
        "policy_cd": "POL-OS-001",
        "client_id": "client-a",
        "signal_type_cd": "FRESHNESS",
        "trigger_cd": "DQ_RERUN",
        "severity_cd": "HIGH",
        "threshold_severity_cd": "WARN"
    }

    decision.allowed
    decision.code == "POL-OS-001"
    decision.reason == "POL-OS-001: allow"
}

test_evaluate_denies_dq_rerun_when_severity_below_threshold if {
    decision := observability_signal.evaluate with input as {
        "policy_cd": "POL-OS-001",
        "client_id": "client-a",
        "signal_type_cd": "FRESHNESS",
        "trigger_cd": "DQ_RERUN",
        "severity_cd": "INFO",
        "threshold_severity_cd": "WARN"
    }

    not decision.allowed
    decision.code == "POL-OS-001"
    decision.reason == "POL-OS-001: auto-trigger denied for DQ_RERUN because severity INFO is below threshold WARN"
}

test_evaluate_defaults_deny_on_unknown_input if {
    decision := observability_signal.evaluate with input as {
        "policy_cd": "POL-OS-001",
        "client_id": "client-a",
        "signal_type_cd": "FRESHNESS",
        "trigger_cd": "BOGUS",
        "severity_cd": "WARN",
        "threshold_severity_cd": "HIGH"
    }

    not decision.allowed
    decision.code == "POL-OS-001"
    decision.reason == "POL-OS-001: unknown or invalid input"
    decision.message == "POL-OS-001: unknown or invalid input"
}
