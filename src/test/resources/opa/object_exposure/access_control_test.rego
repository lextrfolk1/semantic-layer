package lextr.semantic.object_exposure_test

import data.lextr.semantic.object_exposure

test_evaluate_allows_same_tenant_object_request if {
    decision := object_exposure.evaluate with input as {
        "policy_cd": "POL-AC-001",
        "request_type_cd": "LIST",
        "client_id": "client-a",
        "actor_id": "analyst-1",
        "role_cd": "FINANCE",
        "purpose_cd": "REPORTING",
        "object_id": "00000000-0000-0000-0000-000000000101",
        "schema_cd": "meta",
        "object_cd": "GL_BALANCE",
        "object_type_cd": "TABLE",
        "object_client_id": "client-a",
        "object_data_classification_cd": "CONFIDENTIAL",
        "attribute_cd": null,
        "grant_scope_cds": [],
        "grant_status_cds": []
    }

    decision.allowed
    decision.code == "POL-AC-001"
    contains(decision.reason, "POL-AC-001")
}

test_evaluate_allows_same_tenant_attribute_request_with_active_read_grant if {
    decision := object_exposure.evaluate with input as {
        "policy_cd": "POL-AC-001",
        "request_type_cd": "DETAIL",
        "client_id": "client-a",
        "actor_id": "analyst-1",
        "role_cd": "FINANCE",
        "purpose_cd": "REPORTING",
        "object_id": "00000000-0000-0000-0000-000000000101",
        "schema_cd": "meta",
        "object_cd": "GL_BALANCE",
        "object_type_cd": "TABLE",
        "object_client_id": "client-a",
        "object_data_classification_cd": "CONFIDENTIAL",
        "attribute_cd": "AMOUNT",
        "grant_scope_cds": ["READ"],
        "grant_status_cds": ["ACTIVE"]
    }

    decision.allowed
    decision.code == "POL-AC-001"
    contains(decision.reason, "POL-AC-001")
}

test_evaluate_denies_cross_tenant_access if {
    decision := object_exposure.evaluate with input as {
        "policy_cd": "POL-AC-001",
        "request_type_cd": "DETAIL",
        "client_id": "client-b",
        "actor_id": "analyst-2",
        "role_cd": "FINANCE",
        "purpose_cd": "REPORTING",
        "object_id": "00000000-0000-0000-0000-000000000101",
        "schema_cd": "meta",
        "object_cd": "GL_BALANCE",
        "object_type_cd": "TABLE",
        "object_client_id": "client-a",
        "object_data_classification_cd": "CONFIDENTIAL",
        "attribute_cd": null,
        "grant_scope_cds": [],
        "grant_status_cds": []
    }

    not decision.allowed
    decision.code == "POL-AC-001"
    contains(decision.reason, "POL-AC-001")
    contains(decision.reason, "cross-tenant access denied")
}

test_evaluate_denies_unauthorized_role_or_purpose if {
    decision := object_exposure.evaluate with input as {
        "policy_cd": "POL-AC-001",
        "request_type_cd": "DETAIL",
        "client_id": "client-a",
        "actor_id": "analyst-1",
        "role_cd": "",
        "purpose_cd": "REPORTING",
        "object_id": "00000000-0000-0000-0000-000000000101",
        "schema_cd": "meta",
        "object_cd": "GL_BALANCE",
        "object_type_cd": "TABLE",
        "object_client_id": "client-a",
        "object_data_classification_cd": "CONFIDENTIAL",
        "attribute_cd": null,
        "grant_scope_cds": [],
        "grant_status_cds": []
    }

    not decision.allowed
    decision.code == "POL-AC-001"
    contains(decision.reason, "POL-AC-001")
    contains(decision.reason, "need-to-know denied")
}

test_evaluate_denies_attribute_request_without_active_read_grant if {
    decision := object_exposure.evaluate with input as {
        "policy_cd": "POL-AC-001",
        "request_type_cd": "DETAIL",
        "client_id": "client-a",
        "actor_id": "analyst-1",
        "role_cd": "FINANCE",
        "purpose_cd": "REPORTING",
        "object_id": "00000000-0000-0000-0000-000000000101",
        "schema_cd": "meta",
        "object_cd": "GL_BALANCE",
        "object_type_cd": "TABLE",
        "object_client_id": "client-a",
        "object_data_classification_cd": "CONFIDENTIAL",
        "attribute_cd": "AMOUNT",
        "grant_scope_cds": ["READ"],
        "grant_status_cds": ["PENDING"]
    }

    not decision.allowed
    decision.code == "POL-AC-001"
    contains(decision.reason, "POL-AC-001")
    contains(decision.reason, "no ACTIVE READ grant exists")
}

test_evaluate_defaults_deny_on_unknown_input if {
    decision := object_exposure.evaluate with input as {
        "policy_cd": "POL-AC-999",
        "request_type_cd": "DETAIL",
        "client_id": "client-a",
        "actor_id": "analyst-1",
        "role_cd": "FINANCE",
        "purpose_cd": "REPORTING",
        "object_id": "00000000-0000-0000-0000-000000000101",
        "schema_cd": "meta",
        "object_cd": "GL_BALANCE",
        "object_type_cd": "TABLE",
        "object_client_id": "client-a",
        "object_data_classification_cd": "CONFIDENTIAL",
        "attribute_cd": null,
        "grant_scope_cds": [],
        "grant_status_cds": []
    }

    not decision.allowed
    decision.code == "POL-AC-001"
    decision.reason == "POL-AC-001: unknown or invalid input"
    decision.message == "POL-AC-001: unknown or invalid input"
}
