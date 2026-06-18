package com.lextr.semanticlayer.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
public class FilterLookupCertificationServiceException extends SemanticLayerException {

    public FilterLookupCertificationServiceException(String message, Throwable cause) {
        super(message, cause);
    }

    public FilterLookupCertificationServiceException(String message) {
        super(message);
    }
}
