package lextr.semantic.taxonomy

default evaluate := {
    "allowed": false,
    "code": "taxonomy.jurisdiction_valid",
    "message": "taxonomy.jurisdiction_valid: unknown or invalid input"
}

evaluate := {
    "allowed": true,
    "code": "taxonomy.jurisdiction_valid",
    "message": "taxonomy.jurisdiction_valid: allow"
} if {
    valid_input
    valid_taxonomy_code
    valid_jurisdiction_length
}

evaluate := {
    "allowed": false,
    "code": "taxonomy.jurisdiction_valid",
    "message": sprintf(
        "taxonomy.jurisdiction_valid: taxonomy_cd must be 12 characters for source %s",
        [input.taxonomy_source_cd]
    )
} if {
    valid_input
    not valid_taxonomy_code
}

evaluate := {
    "allowed": false,
    "code": "taxonomy.jurisdiction_valid",
    "message": sprintf(
        "taxonomy.jurisdiction_valid: taxonomy_jurisdiction_cd length must be %d for source %s",
        [expected_jurisdiction_length[input.taxonomy_source_cd], input.taxonomy_source_cd]
    )
} if {
    valid_input
    valid_taxonomy_code
    not valid_jurisdiction_length
}

valid_input if {
    is_string(input.client_id)
    input.client_id != ""
    is_string(input.taxonomy_cd)
    input.taxonomy_cd != ""
    is_string(input.taxonomy_source_cd)
    input.taxonomy_source_cd != ""
    is_string(input.taxonomy_jurisdiction_cd)
    input.taxonomy_jurisdiction_cd != ""
    expected_jurisdiction_length[input.taxonomy_source_cd]
}

valid_taxonomy_code if {
    count(input.taxonomy_cd) == 12
}

valid_jurisdiction_length if {
    expected := expected_jurisdiction_length[input.taxonomy_source_cd]
    count(input.taxonomy_jurisdiction_cd) == expected
}

expected_jurisdiction_length["MDRM"] := 2
expected_jurisdiction_length["LEXTR_ASSIGNED"] := 2
expected_jurisdiction_length["REG_DEFINED"] := 2
