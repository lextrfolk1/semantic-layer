package com.lextr.semanticlayer.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
public class WorkflowApprovalServiceException extends SemanticLayerException {

    public WorkflowApprovalServiceException(String message) {
        super(message);
    }

    public WorkflowApprovalServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
