package com.lextr.semanticlayer.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
public class WorkflowTaskAlreadyApprovedException extends SemanticLayerException {

    public WorkflowTaskAlreadyApprovedException(Long id) {
        super("Workflow task " + id + " is already approved and cannot be re-approved");
    }
}
