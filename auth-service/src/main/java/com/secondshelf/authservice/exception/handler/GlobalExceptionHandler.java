package com.secondshelf.authservice.exception.handler;

import com.secondshelf.authservice.exception.UserServiceClientException;
import com.secondshelf.authservice.exception.advice.ErrorResponse;
import com.secondshelf.authservice.ratelimit.AuthRateLimitExceededException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String AUTH_RATE_LIMIT_EXCEEDED = "AUTH_RATE_LIMIT_EXCEEDED";

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex,
                                                          HttpServletRequest request) {
        return ResponseEntity.badRequest().body(error(
                request,
                HttpStatus.BAD_REQUEST,
                "VALIDATION_ERROR",
                "Request validation failed",
                validationDetails(ex)
        ));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleNotReadable(HttpMessageNotReadableException ex,
                                                           HttpServletRequest request) {
        return ResponseEntity.badRequest().body(error(
                request,
                HttpStatus.BAD_REQUEST,
                "MALFORMED_JSON_REQUEST",
                "Malformed JSON request"
        ));
    }

    @ExceptionHandler(UserServiceClientException.class)
    public ResponseEntity<ErrorResponse> handleUserService(UserServiceClientException ex,
                                                           HttpServletRequest request) {
        if (ex.getDownstream() != null) {
            return ResponseEntity.status(ex.getStatus()).body(ex.getDownstream());
        }

        return ResponseEntity.status(ex.getStatus()).body(error(
                request,
                ex.getStatus(),
                "USER_SERVICE_ERROR",
                "User service request failed"
        ));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatus(ResponseStatusException ex,
                                                              HttpServletRequest request) {
        HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());
        return ResponseEntity.status(ex.getStatusCode()).body(error(
                request,
                status,
                ex.getStatusCode().toString(),
                ex.getReason() != null ? ex.getReason() : status.getReasonPhrase()
        ));
    }

    @ExceptionHandler(AuthRateLimitExceededException.class)
    public ResponseEntity<ErrorResponse> handleAuthRateLimitExceeded(AuthRateLimitExceededException ex,
                                                                     HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.getRetryAfterSeconds()))
                .body(error(request, HttpStatus.TOO_MANY_REQUESTS, AUTH_RATE_LIMIT_EXCEEDED, ex.getMessage()));
    }

    private ErrorResponse error(HttpServletRequest request,
                                HttpStatus status,
                                String errorCode,
                                String message) {
        return error(request, status, errorCode, message, null);
    }

    private ErrorResponse error(HttpServletRequest request,
                                HttpStatus status,
                                String errorCode,
                                String message,
                                Map<String, Object> details) {
        return ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .path(request.getRequestURI())
                .status(status.value())
                .errorCode(errorCode)
                .message(message)
                .details(details)
                .build();
    }

    private Map<String, Object> validationDetails(MethodArgumentNotValidException ex) {
        Map<String, Object> details = new LinkedHashMap<>();
        List<Map<String, String>> fieldErrors = new ArrayList<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            String reason = error.getDefaultMessage();
            details.put(error.getField(), reason);
            fieldErrors.add(Map.of(
                    "field", error.getField(),
                    "reason", reason != null ? reason : "Invalid value",
                    "code", error.getCode() != null ? error.getCode() : "Invalid"
            ));
        }
        details.put("fieldErrors", fieldErrors);
        return details;
    }
}
