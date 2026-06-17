package com.lextr.semanticlayer.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
public class ObjectRegistrationServiceException extends SemanticLayerException {

    public ObjectRegistrationServiceException(String message, Throwable cause) {
        super(message, cause);
    }

    public ObjectRegistrationServiceException(String message) {
        super(message);
    }
}
