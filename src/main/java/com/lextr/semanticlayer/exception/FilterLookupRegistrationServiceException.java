package com.lextr.semanticlayer.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
public class FilterLookupRegistrationServiceException extends SemanticLayerException {

    public FilterLookupRegistrationServiceException(String message, Throwable cause) {
        super(message, cause);
    }

    public FilterLookupRegistrationServiceException(String message) {
        super(message);
    }
}
