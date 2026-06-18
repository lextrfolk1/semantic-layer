package com.lextr.semanticlayer.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
public class AttributePairingRegistrationServiceException extends SemanticLayerException {

    public AttributePairingRegistrationServiceException(String message) {
        super(message);
    }

    public AttributePairingRegistrationServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
