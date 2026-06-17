package com.lextr.semanticlayer.exception;

public class PolicyViolationException extends SemanticLayerException {

    private final String code;

    public PolicyViolationException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
