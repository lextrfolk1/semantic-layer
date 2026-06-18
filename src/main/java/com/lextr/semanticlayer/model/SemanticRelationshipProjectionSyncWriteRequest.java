package com.lextr.semanticlayer.model;

import java.time.OffsetDateTime;

public record SemanticRelationshipProjectionSyncWriteRequest(
        String relationship_cd,
        OffsetDateTime neo4j_synced_ts,
        OffsetDateTime updated_ts,
        String updated_by
) {
}
