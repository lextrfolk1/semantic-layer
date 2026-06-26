package lextr.semantic.object_exposure

default evaluate := {
    "allowed": false,
    "code": "POL-AC-001",
    "reason": "POL-AC-001: unknown or invalid input",
    "message": "POL-AC-001: unknown or invalid input"
}

evaluate := {
    "allowed": false,
    "code": "POL-AC-001",
    "reason": sprintf(
        "POL-AC-001: cross-tenant access denied for actor client %s to object client %s",
        [input.client_id, input.object_client_id]
    ),
    "message": sprintf(
        "POL-AC-001: cross-tenant access denied for actor client %s to object client %s",
        [input.client_id, input.object_client_id]
    )
} if {
    valid_input
    cross_tenant_access
}

evaluate := {
    "allowed": false,
    "code": "POL-AC-001",
    "reason": sprintf(
        "POL-AC-001: need-to-know denied for role %s and purpose %s",
        [input.role_cd, input.purpose_cd]
    ),
    "message": sprintf(
        "POL-AC-001: need-to-know denied for role %s and purpose %s",
        [input.role_cd, input.purpose_cd]
    )
} if {
    valid_input
    not cross_tenant_access
    not role_and_purpose_present
}

evaluate := {
    "allowed": false,
    "code": "POL-AC-001",
    "reason": sprintf(
        "POL-AC-001: need-to-know denied because no ACTIVE READ grant exists for %s.%s.%s",
        [input.schema_cd, input.object_cd, input.attribute_cd]
    ),
    "message": sprintf(
        "POL-AC-001: need-to-know denied because no ACTIVE READ grant exists for %s.%s.%s",
        [input.schema_cd, input.object_cd, input.attribute_cd]
    )
} if {
    valid_input
    not cross_tenant_access
    role_and_purpose_present
    attribute_request
    not active_read_grant_present
}

evaluate := {
    "allowed": true,
    "code": "POL-AC-001",
    "reason": "POL-AC-001: allow",
    "message": "POL-AC-001: allow"
} if {
    valid_input
    not cross_tenant_access
    role_and_purpose_present
    object_level_request
}

evaluate := {
    "allowed": true,
    "code": "POL-AC-001",
    "reason": "POL-AC-001: allow",
    "message": "POL-AC-001: allow"
} if {
    valid_input
    not cross_tenant_access
    role_and_purpose_present
    attribute_request
    active_read_grant_present
}

valid_input if {
    input.policy_cd == "POL-AC-001"
    valid_request_type
    is_string(input.client_id)
    input.client_id != ""
    is_string(input.schema_cd)
    input.schema_cd != ""
    is_string(input.object_cd)
    input.object_cd != ""
    input.object_id != null
    is_string(input.object_client_id)
    input.object_client_id != ""
    role_field_present
    purpose_field_present
    valid_attribute_context
}

valid_request_type if {
    input.request_type_cd == "LIST"
}

valid_request_type if {
    input.request_type_cd == "DETAIL"
}

role_field_present if {
    is_string(input.role_cd)
}

purpose_field_present if {
    is_string(input.purpose_cd)
}

role_and_purpose_present if {
    input.role_cd != ""
    input.purpose_cd != ""
}

valid_attribute_context if {
    object_level_request
}

valid_attribute_context if {
    attribute_request
    is_array(input.grant_scope_cds)
    is_array(input.grant_status_cds)
}

object_level_request if {
    input.attribute_cd == null
}

attribute_request if {
    is_string(input.attribute_cd)
    input.attribute_cd != ""
}

cross_tenant_access if {
    input.client_id != input.object_client_id
}

active_read_grant_present if {
    some scope_index
    input.grant_scope_cds[scope_index] == "READ"
    some status_index
    input.grant_status_cds[status_index] == "ACTIVE"
}
