package com.lextr.semanticlayer.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
public class DqRuleServiceException extends SemanticLayerException {

    public DqRuleServiceException(String message) {
        super(message);
    }

    public DqRuleServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
