package com.lextr.semanticlayer.dao;

import com.lextr.semanticlayer.model.SemanticRelationshipCatalogRecord;
import com.lextr.semanticlayer.model.SemanticRelationshipCatalogWriteRequest;

public interface RelationshipRegistrationWriteDao {

    SemanticRelationshipCatalogRecord insertRelationship(SemanticRelationshipCatalogWriteRequest request);
}
