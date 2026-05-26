package com.secondshelf.userservice.exception.handler;

import com.secondshelf.userservice.exception.EmailAlreadyExistsException;
import com.secondshelf.userservice.exception.UserNotFoundException;
import com.secondshelf.userservice.exception.UserProfileAlreadyExistsException;
import com.secondshelf.userservice.exception.UsernameAlreadyExistsException;
import com.secondshelf.userservice.exception.advice.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(UserNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleUserNotFound(UserNotFoundException exception, HttpServletRequest request) {
        return error(request, HttpStatus.NOT_FOUND, "USER_NOT_FOUND", exception.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleIllegalArgument(IllegalArgumentException exception, HttpServletRequest request) {
        return error(request, HttpStatus.BAD_REQUEST, "BAD_REQUEST", exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleValidation(MethodArgumentNotValidException exception, HttpServletRequest request) {
        return error(request, HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Request validation failed", validationDetails(exception));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleNotReadable(HttpMessageNotReadableException exception, HttpServletRequest request) {
        return error(request, HttpStatus.BAD_REQUEST, "MALFORMED_JSON_REQUEST", "Malformed JSON request");
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatus(ResponseStatusException ex, HttpServletRequest request) {
        HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());
        ErrorResponse body = error(
                request,
                status,
                ex.getStatusCode().toString(),
                ex.getReason() != null ? ex.getReason() : status.getReasonPhrase()
        );
        return ResponseEntity.status(ex.getStatusCode()).body(body);
    }

    @ExceptionHandler(UserProfileAlreadyExistsException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleProfileAlreadyExists(UserProfileAlreadyExistsException exception, HttpServletRequest request) {
        return error(request, HttpStatus.BAD_REQUEST, "PROFILE_ALREADY_EXISTS", exception.getMessage());
    }

    @ExceptionHandler(UsernameAlreadyExistsException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleUsernameAlreadyExists(UsernameAlreadyExistsException exception, HttpServletRequest request) {
        return error(request, HttpStatus.BAD_REQUEST, "USERNAME_ALREADY_EXISTS", exception.getMessage());
    }

    @ExceptionHandler(EmailAlreadyExistsException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleEmailAlreadyExists(EmailAlreadyExistsException exception, HttpServletRequest request) {
        return error(request, HttpStatus.BAD_REQUEST, "EMAIL_ALREADY_EXISTS", exception.getMessage());
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
