package com.lextr.semanticlayer.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class RelationshipAlreadyExistsException extends SemanticLayerException {

    public RelationshipAlreadyExistsException(String relationshipCd) {
        super("Relationship already exists: " + relationshipCd);
    }
}
