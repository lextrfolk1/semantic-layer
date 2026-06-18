package com.lextr.semanticlayer.model;

public record FilterLookupValueCountRecord(
        String lookup_cd,
        String client_id,
        long value_count
) {
}
