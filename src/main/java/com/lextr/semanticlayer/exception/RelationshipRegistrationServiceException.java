package com.lextr.semanticlayer.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
public class RelationshipRegistrationServiceException extends SemanticLayerException {

    public RelationshipRegistrationServiceException(String message, Throwable cause) {
        super(message, cause);
    }

    public RelationshipRegistrationServiceException(String message) {
        super(message);
    }
}
