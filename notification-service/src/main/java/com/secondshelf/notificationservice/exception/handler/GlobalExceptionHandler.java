package com.secondshelf.notificationservice.exception.handler;

import com.secondshelf.notificationservice.exception.NotificationBadRequestException;
import com.secondshelf.notificationservice.exception.NotificationException;
import com.secondshelf.notificationservice.exception.NotificationForbiddenException;
import com.secondshelf.notificationservice.exception.NotificationNotFoundException;
import com.secondshelf.notificationservice.exception.advice.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(NotificationBadRequestException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleBadRequest(NotificationBadRequestException ex) {
        return buildErrorResponse(ex);
    }

    @ExceptionHandler(NotificationForbiddenException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ErrorResponse handleForbidden(NotificationForbiddenException ex) {
        return buildErrorResponse(ex);
    }

    @ExceptionHandler(NotificationNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleNotFound(NotificationNotFoundException ex) {
        return buildErrorResponse(ex);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new HashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(err -> fieldErrors.put(err.getField(), err.getDefaultMessage()));

        return ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .code("VALIDATION_ERROR")
                .message("Request validation failed")
                .details(fieldErrors)
                .build();
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .code("INVALID_REQUEST_PARAMETER")
                .message("Invalid request parameter: " + ex.getName())
                .build();
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleNotReadable(HttpMessageNotReadableException ex) {
        return ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .code("MALFORMED_JSON_REQUEST")
                .message("Malformed JSON request")
                .build();
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorResponse handleGeneric(Exception ex) {
        return ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .code("INTERNAL_SERVER_ERROR")
                .message("Unexpected error occurred")
                .build();
    }

    private ErrorResponse buildErrorResponse(NotificationException ex) {
        return ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .code(ex.getCode())
                .message(ex.getMessage())
                .build();
    }
}
