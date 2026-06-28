package com.lextr.semanticlayer.api;

import com.lextr.semanticlayer.dto.ApiErrorResponseDto;
import com.lextr.semanticlayer.exception.OpaPolicyClientException;
import com.lextr.semanticlayer.exception.PolicyViolationException;
import com.lextr.semanticlayer.exception.RelationshipAlreadyExistsException;
import com.lextr.semanticlayer.exception.RegistryResourceNotFoundException;
import com.lextr.semanticlayer.exception.WorkflowApprovalServiceException;
import com.lextr.semanticlayer.exception.WorkflowTaskAlreadyApprovedException;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Method;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    void mapsPolicyViolationToUnprocessableEntityWithPolicyCode() {
        ResponseEntity<ApiErrorResponseDto> response =
                handler.handlePolicyViolation(new PolicyViolationException("POL-001", "policy rejected"));

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.getStatusCode());
        assertEquals(new ApiErrorResponseDto("POL-001", "policy rejected"), response.getBody());
    }

    @Test
    void mapsOpaPolicyFailuresToServiceUnavailable() {
        ResponseEntity<ApiErrorResponseDto> response =
                handler.handleOpaPolicyClientException(new OpaPolicyClientException("OPA evaluation failed for lextr.semantic.relationship"));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertEquals(new ApiErrorResponseDto("SERVICE_UNAVAILABLE", "OPA evaluation failed for lextr.semantic.relationship"), response.getBody());
    }

    @Test
    void mapsMethodArgumentValidationErrorsToBadRequest() throws Exception {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "request");
        bindingResult.addError(new FieldError("request", "client_id", "client_id is required"));
        Method method = ApiExceptionHandlerTest.class.getDeclaredMethod("workflowRequest", String.class);
        MethodArgumentNotValidException exception =
                new MethodArgumentNotValidException(new MethodParameter(method, 0), bindingResult);

        ResponseEntity<ApiErrorResponseDto> response = handler.handleMethodArgumentNotValid(exception);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(new ApiErrorResponseDto("VALIDATION_ERROR", "client_id: client_id is required"), response.getBody());
    }

    @Test
    void mapsMissingRequestParameterToBadRequest() {
        ResponseEntity<ApiErrorResponseDto> response =
                handler.handleMissingServletRequestParameter(new MissingServletRequestParameterException("client_id", "String"));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("BAD_REQUEST", response.getBody().code());
        assertEquals("Required request parameter 'client_id' for method parameter type String is not present", response.getBody().message());
    }

    @Test
    void mapsTypeMismatchToBadRequest() throws Exception {
        Method method = ApiExceptionHandlerTest.class.getDeclaredMethod("objectId", UUID.class);
        MethodArgumentTypeMismatchException exception =
                new MethodArgumentTypeMismatchException("not-a-uuid", UUID.class, "object_id", new MethodParameter(method, 0), null);

        ResponseEntity<ApiErrorResponseDto> response = handler.handleMethodArgumentTypeMismatch(exception);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(new ApiErrorResponseDto("BAD_REQUEST", "object_id: 'not-a-uuid' is not a valid UUID"), response.getBody());
    }

    @Test
    void mapsMalformedRequestBodyToBadRequest() {
        ResponseEntity<ApiErrorResponseDto> response =
                handler.handleHttpMessageNotReadable(new HttpMessageNotReadableException("Unexpected end-of-input"));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(new ApiErrorResponseDto("BAD_REQUEST", "Request body is malformed"), response.getBody());
    }

    @Test
    void mapsIllegalArgumentExceptionToBadRequest() {
        ResponseEntity<ApiErrorResponseDto> response =
                handler.handleIllegalArgumentException(new IllegalArgumentException("bad request"));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(new ApiErrorResponseDto("BAD_REQUEST", "bad request"), response.getBody());
    }

    @Test
    void mapsResponseStatusExceptionToDeclaredStatusCode() {
        ResponseEntity<ApiErrorResponseDto> response = handler.handleResponseStatusException(
                new ResponseStatusException(HttpStatus.NOT_FOUND, "resource missing"));

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals(new ApiErrorResponseDto("NOT_FOUND", "resource missing"), response.getBody());
    }

    @Test
    void mapsAnnotatedSemanticLayerExceptionsToResponseStatus() {
        ResponseEntity<ApiErrorResponseDto> notFound =
                handler.handleSemanticLayerException(new RegistryResourceNotFoundException("object", "GL_BALANCE"));
        ResponseEntity<ApiErrorResponseDto> unprocessable =
                handler.handleSemanticLayerException(new WorkflowTaskAlreadyApprovedException(301L));
        ResponseEntity<ApiErrorResponseDto> conflict =
                handler.handleSemanticLayerException(new RelationshipAlreadyExistsException("SELF_RELATIONSHIP"));
        ResponseEntity<ApiErrorResponseDto> internalError =
                handler.handleSemanticLayerException(new WorkflowApprovalServiceException("approval failed"));

        assertEquals(HttpStatus.NOT_FOUND, notFound.getStatusCode());
        assertEquals(new ApiErrorResponseDto("NOT_FOUND", "object not found: GL_BALANCE"), notFound.getBody());
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, unprocessable.getStatusCode());
        assertEquals(new ApiErrorResponseDto("UNPROCESSABLE_ENTITY",
                "Workflow task 301 is already approved and cannot be re-approved"), unprocessable.getBody());
        assertEquals(HttpStatus.CONFLICT, conflict.getStatusCode());
        assertEquals(new ApiErrorResponseDto("CONFLICT", "Relationship already exists: SELF_RELATIONSHIP"), conflict.getBody());
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, internalError.getStatusCode());
        assertEquals(new ApiErrorResponseDto("INTERNAL_SERVER_ERROR", "approval failed"), internalError.getBody());
    }

    @Test
    void mapsUnexpectedExceptionsToInternalServerError() {
        ResponseEntity<ApiErrorResponseDto> explicitMessage = handler.handleUnexpectedException(new RuntimeException("boom"));
        ResponseEntity<ApiErrorResponseDto> blankMessage = handler.handleUnexpectedException(new RuntimeException(" "));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, explicitMessage.getStatusCode());
        assertEquals(new ApiErrorResponseDto("INTERNAL_SERVER_ERROR", "boom"), explicitMessage.getBody());
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, blankMessage.getStatusCode());
        assertEquals(new ApiErrorResponseDto("INTERNAL_SERVER_ERROR", "An unexpected error occurred"), blankMessage.getBody());
    }

    private static void workflowRequest(String clientId) {
    }

    private static void objectId(UUID objectId) {
    }
}
