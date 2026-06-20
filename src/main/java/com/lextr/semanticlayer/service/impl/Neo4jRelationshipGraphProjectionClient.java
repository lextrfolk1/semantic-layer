package com.lextr.semanticlayer.service.impl;

import com.lextr.semanticlayer.model.RelationshipGraphProjectionRequest;
import com.lextr.semanticlayer.service.RelationshipGraphProjectionClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class Neo4jRelationshipGraphProjectionClient implements RelationshipGraphProjectionClient {

    static final String UPSERT_RELATIONSHIP_GRAPH = """
            MERGE (parent:Object {node_key: $parent_node_key})
            SET parent.schema_cd = $parent_schema_cd,
                parent.object_cd = $parent_object_cd,
                parent.engine_cd = $parent_engine_cd,
                parent.updated_ts = $projected_ts,
                parent.updated_by = $projected_by
            MERGE (child:Object {node_key: $child_node_key})
            SET child.schema_cd = $child_schema_cd,
                child.object_cd = $child_object_cd,
                child.engine_cd = $child_engine_cd,
                child.updated_ts = $projected_ts,
                child.updated_by = $projected_by
            MERGE (parent)-[relationship:SEMANTIC_RELATIONSHIP {relationship_cd: $relationship_cd}]->(child)
            SET relationship.parent_attribute_cd = $parent_attribute_cd,
                relationship.child_attribute_cd = $child_attribute_cd,
                relationship.relationship_type_cd = $relationship_type_cd,
                relationship.cardinality_cd = $cardinality_cd,
                relationship.join_type_cd = $join_type_cd,
                relationship.is_enforced_flg = $is_enforced_flg,
                relationship.is_nullable_flg = $is_nullable_flg,
                relationship.is_cross_engine_flg = $is_cross_engine_flg,
                relationship.relationship_desc = $relationship_desc,
                relationship.ai_join_guidance_txt = $ai_join_guidance_txt,
                relationship.lifecycle_status_cd = $lifecycle_status_cd,
                relationship.updated_ts = $projected_ts,
                relationship.updated_by = $projected_by
            """;

    private final RelationshipGraphCypherExecutor cypherExecutor;

    @Autowired
    public Neo4jRelationshipGraphProjectionClient(ObjectProvider<Neo4jClient> neo4jClientProvider) {
        this(new Neo4jClientRelationshipGraphCypherExecutor(neo4jClientProvider));
    }

    Neo4jRelationshipGraphProjectionClient(RelationshipGraphCypherExecutor cypherExecutor) {
        this.cypherExecutor = cypherExecutor;
    }

    @Override
    public boolean projectRelationship(RelationshipGraphProjectionRequest request) {
        return cypherExecutor.run(UPSERT_RELATIONSHIP_GRAPH, parameters(request));
    }

    private Map<String, Object> parameters(RelationshipGraphProjectionRequest request) {
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("relationship_cd", request.relationship_cd());
        parameters.put("parent_node_key", nodeKey(request.parent_schema_cd(), request.parent_object_cd()));
        parameters.put("parent_schema_cd", request.parent_schema_cd());
        parameters.put("parent_object_cd", request.parent_object_cd());
        parameters.put("parent_attribute_cd", request.parent_attribute_cd());
        parameters.put("parent_engine_cd", request.parent_engine_cd());
        parameters.put("child_node_key", nodeKey(request.child_schema_cd(), request.child_object_cd()));
        parameters.put("child_schema_cd", request.child_schema_cd());
        parameters.put("child_object_cd", request.child_object_cd());
        parameters.put("child_attribute_cd", request.child_attribute_cd());
        parameters.put("child_engine_cd", request.child_engine_cd());
        parameters.put("relationship_type_cd", request.relationship_type_cd());
        parameters.put("cardinality_cd", request.cardinality_cd());
        parameters.put("join_type_cd", request.join_type_cd());
        parameters.put("is_enforced_flg", request.is_enforced_flg());
        parameters.put("is_nullable_flg", request.is_nullable_flg());
        parameters.put("is_cross_engine_flg", request.is_cross_engine_flg());
        parameters.put("relationship_desc", request.relationship_desc());
        parameters.put("ai_join_guidance_txt", request.ai_join_guidance_txt());
        parameters.put("lifecycle_status_cd", request.lifecycle_status_cd());
        parameters.put("projected_ts", request.projected_ts());
        parameters.put("projected_by", request.projected_by());
        return parameters;
    }

    private String nodeKey(String schemaCode, String objectCode) {
        return schemaCode + "." + objectCode;
    }
}

interface RelationshipGraphCypherExecutor {

    boolean run(String cypher, Map<String, Object> parameters);
}

final class Neo4jClientRelationshipGraphCypherExecutor implements RelationshipGraphCypherExecutor {

    private static final Logger logger = LoggerFactory.getLogger(Neo4jClientRelationshipGraphCypherExecutor.class);

    private final ObjectProvider<Neo4jClient> neo4jClientProvider;

    Neo4jClientRelationshipGraphCypherExecutor(ObjectProvider<Neo4jClient> neo4jClientProvider) {
        this.neo4jClientProvider = neo4jClientProvider;
    }

    @Override
    public boolean run(String cypher, Map<String, Object> parameters) {
        Neo4jClient neo4jClient = neo4jClientProvider.getIfAvailable();
        if (neo4jClient == null) {
            logger.warn("Skipping relationship graph projection because Neo4jClient is not configured");
            return false;
        }
        neo4jClient.query(cypher).bindAll(parameters).run();
        return true;
    }
}
