package com.lextr.semanticlayer.exception;

public class SemanticLayerException extends RuntimeException {

    public SemanticLayerException(String message) {
        super(message);
    }

    public SemanticLayerException(String message, Throwable cause) {
        super(message, cause);
    }
}
