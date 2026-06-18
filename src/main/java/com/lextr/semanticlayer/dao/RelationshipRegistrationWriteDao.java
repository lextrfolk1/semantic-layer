package com.lextr.semanticlayer.dao;

import com.lextr.semanticlayer.model.SemanticRelationshipCatalogRecord;
import com.lextr.semanticlayer.model.SemanticRelationshipProjectionSyncWriteRequest;
import com.lextr.semanticlayer.model.SemanticRelationshipCatalogWriteRequest;

public interface RelationshipRegistrationWriteDao {

    SemanticRelationshipCatalogRecord insertRelationship(SemanticRelationshipCatalogWriteRequest request);

    SemanticRelationshipCatalogRecord updateNeo4jProjectionSync(SemanticRelationshipProjectionSyncWriteRequest request);
}
