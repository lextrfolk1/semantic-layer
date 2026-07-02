package com.lextr.semanticlayer.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class WorkflowTaskNotPendingException extends SemanticLayerException {

    public WorkflowTaskNotPendingException(Long id, String status, String action) {
        super("Workflow task " + id + " is " + status + " and cannot be " + action);
    }
}
