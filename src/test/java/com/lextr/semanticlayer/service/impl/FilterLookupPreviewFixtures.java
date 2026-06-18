package com.lextr.semanticlayer.service.impl;

import com.lextr.semanticlayer.model.SemanticFilterLookupRecord;

import java.time.LocalDate;
import java.time.OffsetDateTime;

final class FilterLookupPreviewFixtures {

    private FilterLookupPreviewFixtures() {
    }

    static SemanticFilterLookupRecord sqlLookup(String lookupCode) {
        return new SemanticFilterLookupRecord(
                101L,
                lookupCode,
                "SQL_QUERY",
                null,
                "meta.gl_balance",
                "ledger_status = 'ACTIVE'",
                "ledger_id",
                "meta.ledger",
                "ledger_id",
                "ledger_id",
                "DISTINCT",
                500,
                250,
                60,
                null,
                true,
                true,
                false,
                false,
                "SQL ledger scope values",
                "client-a",
                "ACTIVE",
                "HEALTHY",
                null,
                null,
                LocalDate.parse("2026-08-02"),
                "ACTIVE",
                OffsetDateTime.parse("2026-06-16T10:15:30Z"),
                "producer",
                OffsetDateTime.parse("2026-06-17T10:15:30Z"),
                "platform"
        );
    }
}
