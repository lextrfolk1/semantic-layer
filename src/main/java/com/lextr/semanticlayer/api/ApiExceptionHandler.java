package com.lextr.semanticlayer.api;

import com.lextr.semanticlayer.dto.ApiErrorResponseDto;
import com.lextr.semanticlayer.exception.PolicyViolationException;
import com.lextr.semanticlayer.exception.SemanticLayerException;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(ApiExceptionHandler.class);
    private static final String BAD_REQUEST_CODE = "BAD_REQUEST";
    private static final String VALIDATION_ERROR_CODE = "VALIDATION_ERROR";
    private static final String NOT_FOUND_CODE = "NOT_FOUND";
    private static final String UNPROCESSABLE_ENTITY_CODE = "UNPROCESSABLE_ENTITY";
    private static final String INTERNAL_SERVER_ERROR_CODE = "INTERNAL_SERVER_ERROR";

    @ExceptionHandler(PolicyViolationException.class)
    public ResponseEntity<ApiErrorResponseDto> handlePolicyViolation(PolicyViolationException exception) {
        return buildResponse(HttpStatus.UNPROCESSABLE_ENTITY, exception.code(), exception.getMessage(), exception);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponseDto> handleMethodArgumentNotValid(MethodArgumentNotValidException exception) {
        return buildResponse(HttpStatus.BAD_REQUEST, VALIDATION_ERROR_CODE, validationMessage(exception), exception);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiErrorResponseDto> handleMissingServletRequestParameter(MissingServletRequestParameterException exception) {
        return buildResponse(HttpStatus.BAD_REQUEST, BAD_REQUEST_CODE, exception.getMessage(), exception);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponseDto> handleMethodArgumentTypeMismatch(MethodArgumentTypeMismatchException exception) {
        return buildResponse(HttpStatus.BAD_REQUEST, BAD_REQUEST_CODE, invalidParameterMessage(exception), exception);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponseDto> handleHttpMessageNotReadable(HttpMessageNotReadableException exception) {
        return buildResponse(HttpStatus.BAD_REQUEST, BAD_REQUEST_CODE, "Request body is malformed", exception);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponseDto> handleIllegalArgumentException(IllegalArgumentException exception) {
        return buildResponse(HttpStatus.BAD_REQUEST, BAD_REQUEST_CODE, exception.getMessage(), exception);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiErrorResponseDto> handleResponseStatusException(ResponseStatusException exception) {
        return buildResponse(
                HttpStatus.valueOf(exception.getStatusCode().value()),
                statusCodeFor(HttpStatus.valueOf(exception.getStatusCode().value())),
                exception.getReason() != null ? exception.getReason() : exception.getMessage(),
                exception
        );
    }

    @ExceptionHandler(SemanticLayerException.class)
    public ResponseEntity<ApiErrorResponseDto> handleSemanticLayerException(SemanticLayerException exception) {
        HttpStatus status = resolveStatus(exception);
        return buildResponse(status, statusCodeFor(status), exception.getMessage(), exception);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponseDto> handleUnexpectedException(Exception exception) {
        return buildResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                INTERNAL_SERVER_ERROR_CODE,
                Optional.ofNullable(exception.getMessage()).filter(message -> !message.isBlank()).orElse("An unexpected error occurred"),
                exception
        );
    }

    private static String validationMessage(MethodArgumentNotValidException exception) {
        return exception.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .or(() -> exception.getBindingResult().getGlobalErrors().stream()
                        .findFirst()
                        .map(error -> error.getObjectName() + ": " + error.getDefaultMessage()))
                .orElse("Validation failed");
    }

    private static String invalidParameterMessage(MethodArgumentTypeMismatchException exception) {
        String name = exception.getName();
        Object value = exception.getValue();
        Class<?> requiredType = exception.getRequiredType();
        if (requiredType == null) {
            return name + ": value is invalid";
        }
        return name + ": '" + value + "' is not a valid " + requiredType.getSimpleName();
    }

    private static HttpStatus resolveStatus(SemanticLayerException exception) {
        ResponseStatus responseStatus = AnnotatedElementUtils.findMergedAnnotation(exception.getClass(), ResponseStatus.class);
        return responseStatus != null ? responseStatus.code() : HttpStatus.INTERNAL_SERVER_ERROR;
    }

    private static String statusCodeFor(HttpStatus status) {
        return switch (status) {
            case BAD_REQUEST -> BAD_REQUEST_CODE;
            case NOT_FOUND -> NOT_FOUND_CODE;
            case UNPROCESSABLE_ENTITY -> UNPROCESSABLE_ENTITY_CODE;
            default -> INTERNAL_SERVER_ERROR_CODE;
        };
    }

    private static ResponseEntity<ApiErrorResponseDto> buildResponse(HttpStatus status, String code, String message, Exception exception) {
        logException(status, code, message, exception);
        return ResponseEntity.status(status).body(new ApiErrorResponseDto(code, message));
    }

    private static void logException(HttpStatus status, String code, String message, Exception exception) {
        String requestSummary = currentRequestSummary();
        String exceptionName = exception == null ? "n/a" : exception.getClass().getSimpleName();
        if (status.is5xxServerError()) {
            if (exception == null) {
                logger.error(
                        "Request {} failed with status={} code={} exception={} message={}",
                        requestSummary,
                        status.value(),
                        code,
                        exceptionName,
                        message
                );
            } else {
                logger.error(
                        "Request {} failed with status={} code={} exception={} message={}",
                        requestSummary,
                        status.value(),
                        code,
                        exceptionName,
                        message,
                        exception
                );
            }
            return;
        }
        logger.warn(
                "Request {} failed with status={} code={} exception={} message={}",
                requestSummary,
                status.value(),
                code,
                exceptionName,
                message
        );
    }

    private static String currentRequestSummary() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof ServletRequestAttributes servletRequestAttributes)) {
            return "n/a";
        }
        HttpServletRequest request = servletRequestAttributes.getRequest();
        return request.getMethod() + " " + request.getRequestURI();
    }
}
