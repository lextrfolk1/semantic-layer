package lextr.semantic.semantic_resolve_test

import data.lextr.semantic.semantic_resolve

test_evaluate_allows_same_tenant_semantic_resolve {
    decision := semantic_resolve.evaluate with input as {
        "policy_cd": "POL-RS-001",
        "request_type_cd": "SEMANTIC",
        "client_id": "client-a",
        "actor_id": "engine-1",
        "role_cd": "ENGINE",
        "purpose_cd": "RESOLUTION",
        "resource_client_id": "client-a",
        "resource_ref_txt": "meta.ledger"
    }

    decision.allowed == true
    decision.code == "POL-RS-001"
}

test_evaluate_allows_same_tenant_consumption_resolve {
    decision := semantic_resolve.evaluate with input as {
        "policy_cd": "POL-RS-001",
        "request_type_cd": "CONSUMPTION",
        "client_id": "client-a",
        "actor_id": "engine-1",
        "role_cd": "ENGINE",
        "purpose_cd": "RESOLUTION",
        "resource_client_id": "client-a",
        "resource_ref_txt": "101"
    }

    decision.allowed == true
}

test_evaluate_denies_cross_tenant_resolve {
    decision := semantic_resolve.evaluate with input as {
        "policy_cd": "POL-RS-001",
        "request_type_cd": "SEMANTIC",
        "client_id": "client-a",
        "actor_id": "engine-1",
        "role_cd": "ENGINE",
        "purpose_cd": "RESOLUTION",
        "resource_client_id": "client-b",
        "resource_ref_txt": "meta.ledger"
    }

    decision.allowed == false
    decision.code == "POL-RS-001"
}

test_evaluate_denies_missing_role_or_purpose {
    decision := semantic_resolve.evaluate with input as {
        "policy_cd": "POL-RS-001",
        "request_type_cd": "RESOLVE",
        "client_id": "client-a",
        "actor_id": "engine-1",
        "role_cd": "",
        "purpose_cd": "",
        "resource_client_id": "client-a",
        "resource_ref_txt": "meta.ledger"
    }

    decision.allowed == false
}

test_evaluate_defaults_deny_on_unknown_input {
    decision := semantic_resolve.evaluate with input as {}

    decision.allowed == false
    decision.code == "POL-RS-001"
    decision.reason == "POL-RS-001: unknown or invalid input"
}
