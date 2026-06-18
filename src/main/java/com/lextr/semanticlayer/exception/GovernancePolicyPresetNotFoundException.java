package com.lextr.semanticlayer.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class GovernancePolicyPresetNotFoundException extends SemanticLayerException {

    public GovernancePolicyPresetNotFoundException(String policyCode) {
        super("Governance policy preset not found: " + policyCode);
    }
}
