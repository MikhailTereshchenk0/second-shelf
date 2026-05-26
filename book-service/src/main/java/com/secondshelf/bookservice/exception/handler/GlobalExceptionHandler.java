package com.secondshelf.bookservice.exception.handler;

import com.secondshelf.bookservice.exception.*;
import com.secondshelf.bookservice.exception.advice.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BookNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleNotFound(BookNotFoundException ex, HttpServletRequest request) {
        return error(request, HttpStatus.NOT_FOUND, ex.getCode(), ex.getMessage());
    }

    @ExceptionHandler(BookAccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ErrorResponse handleDenied(BookAccessDeniedException ex, HttpServletRequest request) {
        return error(request, HttpStatus.FORBIDDEN, "ACCESS_DENIED", ex.getMessage());
    }

    @ExceptionHandler(BookStateConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleConflict(BookStateConflictException ex, HttpServletRequest request) {
        return error(request, HttpStatus.CONFLICT, ex.getCode(), ex.getMessage());
    }

    @ExceptionHandler(ForbiddenOperationException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ErrorResponse handleForbidden(ForbiddenOperationException ex, HttpServletRequest request) {
        return error(request, HttpStatus.FORBIDDEN, ex.getCode(), ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        return error(request, HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Request validation failed", validationDetails(ex));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleNotReadable(HttpMessageNotReadableException ex, HttpServletRequest request) {
        return error(request, HttpStatus.BAD_REQUEST, "MALFORMED_JSON_REQUEST", "Malformed JSON request");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
        return error(request, HttpStatus.BAD_REQUEST, "BAD_REQUEST", ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorResponse handleGeneric(Exception ex, HttpServletRequest request) {
        return error(request, HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR", "Unexpected error occurred");
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
