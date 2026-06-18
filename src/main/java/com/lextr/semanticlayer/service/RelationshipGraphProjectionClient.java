package com.lextr.semanticlayer.service;

import com.lextr.semanticlayer.model.RelationshipGraphProjectionRequest;

public interface RelationshipGraphProjectionClient {

    boolean projectRelationship(RelationshipGraphProjectionRequest request);
}
