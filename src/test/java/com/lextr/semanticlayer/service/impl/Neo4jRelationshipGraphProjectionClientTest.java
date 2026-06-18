package com.lextr.semanticlayer.service.impl;

import com.lextr.semanticlayer.model.RelationshipGraphProjectionRequest;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Neo4jRelationshipGraphProjectionClientTest {

    @Test
    void createsNodesAndEdgeOnProjectionSave() {
        RecordingCypherExecutor executor = new RecordingCypherExecutor();
        Neo4jRelationshipGraphProjectionClient client = new Neo4jRelationshipGraphProjectionClient(executor);

        boolean projected = client.projectRelationship(request(
                "GL_TO_LEDGER",
                "POSTGRES",
                "POSTGRES",
                false,
                OffsetDateTime.parse("2026-06-18T10:15:30Z")
        ));

        assertTrue(projected);
        assertTrue(executor.cypher.contains("MERGE (parent:Object"));
        assertTrue(executor.cypher.contains("MERGE (child:Object"));
        assertTrue(executor.cypher.contains("MERGE (parent)-[relationship:SEMANTIC_RELATIONSHIP"));
        assertEquals(Set.of("meta.gl_balance", "meta.ledger"), executor.nodeKeys);
        assertEquals(Set.of("GL_TO_LEDGER"), executor.relationshipKeys);
    }

    @Test
    void mergeProjectionIsIdempotentAcrossRepeatedSaves() {
        RecordingCypherExecutor executor = new RecordingCypherExecutor();
        Neo4jRelationshipGraphProjectionClient client = new Neo4jRelationshipGraphProjectionClient(executor);

        client.projectRelationship(request(
                "GL_TO_LEDGER",
                "POSTGRES",
                "POSTGRES",
                false,
                OffsetDateTime.parse("2026-06-18T10:15:30Z")
        ));
        client.projectRelationship(request(
                "GL_TO_LEDGER",
                "POSTGRES",
                "POSTGRES",
                false,
                OffsetDateTime.parse("2026-06-18T10:20:30Z")
        ));

        assertEquals(2, executor.nodeKeys.size());
        assertEquals(1, executor.relationshipKeys.size());
        assertEquals(OffsetDateTime.parse("2026-06-18T10:20:30Z"), executor.relationshipTimestamps.get("GL_TO_LEDGER"));
    }

    @Test
    void retainsProjectedTimestampInCypherBindings() {
        RecordingCypherExecutor executor = new RecordingCypherExecutor();
        Neo4jRelationshipGraphProjectionClient client = new Neo4jRelationshipGraphProjectionClient(executor);
        OffsetDateTime projectedAt = OffsetDateTime.parse("2026-06-18T10:15:30Z");

        client.projectRelationship(request(
                "GL_TO_LEDGER",
                "POSTGRES",
                "CLICKHOUSE",
                true,
                projectedAt
        ));

        assertEquals(projectedAt, executor.parameters.get("projected_ts"));
        assertEquals(Boolean.TRUE, executor.parameters.get("is_cross_engine_flg"));
    }

    private static RelationshipGraphProjectionRequest request(String relationshipCode,
                                                              String parentEngineCode,
                                                              String childEngineCode,
                                                              boolean crossEngine,
                                                              OffsetDateTime projectedAt) {
        return new RelationshipGraphProjectionRequest(
                relationshipCode,
                "meta",
                "gl_balance",
                "ledger_id",
                parentEngineCode,
                "meta",
                "ledger",
                "ledger_id",
                childEngineCode,
                "FOREIGN_KEY",
                "MANY_TO_ONE",
                "INNER",
                true,
                false,
                crossEngine,
                "GL balances map to ledger master rows",
                "Join on ledger identifier",
                "ACTIVE",
                projectedAt,
                "neo4j-projector"
        );
    }

    private static final class RecordingCypherExecutor implements RelationshipGraphCypherExecutor {

        private String cypher;
        private Map<String, Object> parameters = Map.of();
        private final Set<String> nodeKeys = new HashSet<>();
        private final Set<String> relationshipKeys = new HashSet<>();
        private final Map<String, OffsetDateTime> relationshipTimestamps = new HashMap<>();

        @Override
        public boolean run(String cypher, Map<String, Object> parameters) {
            this.cypher = cypher;
            this.parameters = Map.copyOf(parameters);
            nodeKeys.add((String) parameters.get("parent_node_key"));
            nodeKeys.add((String) parameters.get("child_node_key"));
            String relationshipCode = (String) parameters.get("relationship_cd");
            relationshipKeys.add(relationshipCode);
            relationshipTimestamps.put(relationshipCode, (OffsetDateTime) parameters.get("projected_ts"));
            return true;
        }
    }
}
