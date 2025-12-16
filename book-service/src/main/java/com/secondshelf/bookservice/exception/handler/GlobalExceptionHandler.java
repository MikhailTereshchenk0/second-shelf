package com.secondshelf.bookservice.exception.handler;

import com.secondshelf.bookservice.exception.*;
import com.secondshelf.bookservice.exception.advice.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BookNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleNotFound(BookNotFoundException ex) {
        return ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .code("BOOK_NOT_FOUND")
                .message(ex.getMessage())
                .build();
    }

    @ExceptionHandler(BookAccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ErrorResponse handleDenied(BookAccessDeniedException ex) {
        return ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .code("ACCESS_DENIED")
                .message(ex.getMessage())
                .build();
    }

    @ExceptionHandler(BookStateConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleConflict(BookStateConflictException ex) {
        return ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .code("CONFLICT")
                .message(ex.getMessage())
                .build();
    }

    @ExceptionHandler(ForbiddenOperationException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ErrorResponse handleForbidden(ForbiddenOperationException ex) {
        return ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .code("FORBIDDEN")
                .message(ex.getMessage())
                .build();
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

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleNotReadable(HttpMessageNotReadableException ex) {
        return ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .code("MALFORMED_JSON_REQUEST")
                .message("Malformed JSON request")
                .build();
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleIllegalArgument(IllegalArgumentException ex) {
        return ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .code("BAD_REQUEST")
                .message(ex.getMessage())
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
}
