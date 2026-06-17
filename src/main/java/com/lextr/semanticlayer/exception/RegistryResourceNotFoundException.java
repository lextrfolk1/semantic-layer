package com.lextr.semanticlayer.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class RegistryResourceNotFoundException extends SemanticLayerException {

    public RegistryResourceNotFoundException(String resourceType, String resourceId) {
        super(resourceType + " not found: " + resourceId);
    }
}
