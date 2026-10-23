package com.papaya.claimworkflow.api;

import jakarta.validation.ConstraintViolationException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import tools.jackson.databind.exc.InvalidFormatException;

@Slf4j
@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ApiError> missingEndpoint(NoResourceFoundException exception) {
        String path = exception.getResourcePath();
        boolean mockEndpoint = path.startsWith("api/v1/mock/") || path.startsWith("/api/v1/mock/");
        String explanation = mockEndpoint
                ? "Mock endpoint is unavailable. Start the backend with the local profile and check the endpoint path."
                : "Requested endpoint or resource does not exist";
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiError("ENDPOINT_NOT_FOUND", explanation, Map.of("path", path)));
    }

    @ExceptionHandler(WorkflowException.class)
    ResponseEntity<ApiError> workflow(WorkflowException exception) {
        return ResponseEntity.status(exception.status())
                .body(new ApiError(exception.code(), exception.getMessage(), exception.details()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> invalidBody(MethodArgumentNotValidException exception) {
        List<Map<String, String>> violations = exception.getBindingResult().getAllErrors().stream().map(error -> {
            String field = error instanceof FieldError fieldError ? fieldError.getField() : error.getObjectName();
            return Map.of("field", field, "message", message(error.getDefaultMessage()));
        }).toList();
        return badRequest("Request validation failed", Map.of("violations", violations));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ApiError> constraintViolation(ConstraintViolationException exception) {
        List<Map<String, String>> violations = exception.getConstraintViolations().stream()
                .map(violation -> Map.of("field", violation.getPropertyPath().toString(),
                        "message", violation.getMessage())).toList();
        return badRequest("Request validation failed", Map.of("violations", violations));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiError> unreadable(HttpMessageNotReadableException exception) {
        Throwable cause = exception.getMostSpecificCause();
        if (cause instanceof InvalidFormatException invalid && invalid.getTargetType().isEnum()) {
            String field = invalid.getPath().stream().map(reference -> reference.getPropertyName() == null
                            ? "[" + reference.getIndex() + "]" : reference.getPropertyName())
                    .reduce((left, right) -> left + "." + right).orElse("requestBody");
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("field", field);
            details.put("rejectedValue", String.valueOf(invalid.getValue()));
            details.put("allowedValues", Arrays.stream(invalid.getTargetType().getEnumConstants())
                    .map(String::valueOf).toList());
            return badRequest("Invalid value for " + field, details);
        }
        return badRequest("Malformed JSON request body", Map.of());
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    ResponseEntity<ApiError> missingHeader(MissingRequestHeaderException exception) {
        return badRequest("Required request header is missing", Map.of("header", exception.getHeaderName()));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    ResponseEntity<ApiError> missingParameter(MissingServletRequestParameterException exception) {
        return badRequest("Required request parameter is missing", Map.of("parameter", exception.getParameterName()));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ApiError> typeMismatch(MethodArgumentTypeMismatchException exception) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("parameter", exception.getName());
        details.put("rejectedValue", String.valueOf(exception.getValue()));
        if (exception.getRequiredType() != null) details.put("expectedType", exception.getRequiredType().getSimpleName());
        return badRequest("Invalid request parameter", details);
    }

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ApiError> unauthenticated(AuthenticationException exception) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiError.of("UNAUTHENTICATED", "A valid bearer token is required"));
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiError> forbidden(AccessDeniedException exception) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiError.of("FORBIDDEN", "Access is denied"));
    }

    @ExceptionHandler(ConcurrencyFailureException.class)
    ResponseEntity<ApiError> concurrency(ConcurrencyFailureException exception) {
        return workflow(WorkflowException.conflict());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> unexpected(Exception exception) {
        log.error("Unhandled API failure", exception);
        return ResponseEntity.internalServerError()
                .body(ApiError.of("INTERNAL_ERROR", "Request failed; no partial transition was committed"));
    }

    private ResponseEntity<ApiError> badRequest(String message, Map<String, Object> details) {
        return ResponseEntity.badRequest().body(new ApiError("INVALID_REQUEST", message, details));
    }

    private String message(String value) {
        return value == null ? "Invalid value" : value;
    }
}
