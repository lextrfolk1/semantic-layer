package lextr.taxonomy_test

import data.lextr.taxonomy

test_evaluate_allows_valid_mdrm_input if {
    decision := taxonomy.evaluate with input as {
        "client_id": "client-a",
        "taxonomy_cd": "MDRM12345678",
        "taxonomy_source_cd": "MDRM",
        "taxonomy_jurisdiction_cd": "US"
    }

    decision.allowed
    decision.code == "taxonomy.jurisdiction_valid"
    contains(decision.message, "taxonomy.jurisdiction_valid")
}

test_evaluate_denies_invalid_taxonomy_cd_length if {
    decision := taxonomy.evaluate with input as {
        "client_id": "client-a",
        "taxonomy_cd": "MDRM1234567",
        "taxonomy_source_cd": "MDRM",
        "taxonomy_jurisdiction_cd": "US"
    }

    not decision.allowed
    decision.code == "taxonomy.jurisdiction_valid"
    contains(decision.message, "taxonomy.jurisdiction_valid")
    contains(decision.message, "taxonomy_cd must be 12 characters")
}

test_evaluate_denies_invalid_jurisdiction_length if {
    decision := taxonomy.evaluate with input as {
        "client_id": "client-a",
        "taxonomy_cd": "MDRM12345678",
        "taxonomy_source_cd": "MDRM",
        "taxonomy_jurisdiction_cd": "USA"
    }

    not decision.allowed
    decision.code == "taxonomy.jurisdiction_valid"
    contains(decision.message, "taxonomy.jurisdiction_valid")
    contains(decision.message, "taxonomy_jurisdiction_cd length must be 2")
}

test_evaluate_defaults_deny_on_unknown_input if {
    decision := taxonomy.evaluate with input as {
        "client_id": "client-a",
        "taxonomy_cd": "MDRM12345678",
        "taxonomy_source_cd": "UNKNOWN",
        "taxonomy_jurisdiction_cd": "US"
    }

    not decision.allowed
    decision.code == "taxonomy.jurisdiction_valid"
    decision.message == "taxonomy.jurisdiction_valid: unknown or invalid input"
}
