package com.lextr.semanticlayer.api;

import com.lextr.semanticlayer.dto.ApiErrorResponseDto;
import com.lextr.semanticlayer.exception.PolicyViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(PolicyViolationException.class)
    public ResponseEntity<ApiErrorResponseDto> handlePolicyViolation(PolicyViolationException exception) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(new ApiErrorResponseDto(exception.code(), exception.getMessage()));
    }
}
