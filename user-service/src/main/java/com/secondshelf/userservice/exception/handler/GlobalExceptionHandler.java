package com.secondshelf.userservice.exception.handler;

import com.secondshelf.userservice.exception.EmailAlreadyExistsException;
import com.secondshelf.userservice.exception.UserNotFoundException;
import com.secondshelf.userservice.exception.UserProfileAlreadyExistsException;
import com.secondshelf.userservice.exception.UsernameAlreadyExistsException;
import com.secondshelf.userservice.exception.advice.ErrorResponse;
import lombok.extern.log4j.Log4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Log4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(UserNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleUserNotFound(UserNotFoundException exception) {
        return ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .code("USER_NOT_FOUND")
                .message(exception.getMessage())
                .build();
    }

    @ExceptionHandler(IllegalAccessError.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleIllegalArgument(IllegalArgumentException exception) {
        return ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .code("BAD_REQUEST")
                .message(exception.getMessage())
                .build();
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleValidation(MethodArgumentNotValidException exception) {

        Map<String, String> fieldErrors = new HashMap<>();
        exception.getBindingResult().getFieldErrors().forEach(error ->
                fieldErrors.put(error.getField(), error.getDefaultMessage())
        );

        return ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .code("VALIDATION_ERROR")
                .message("Request validation failed")
                .details(fieldErrors)
                .build();
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleNotReadable(HttpMessageNotReadableException exception) {
        return ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .code("MALFORMED_JSON_REQUEST")
                .message(exception.getMessage())
                .build();
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorResponse handleGeneric(Exception exception) {
        // log exception
        return ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .code("INTERNAL_SERVER_ERROR")
                .message("Unexpected error occurred")
                .build();
    }

    @ExceptionHandler(UserProfileAlreadyExistsException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleProfileAlreadyExists(UserProfileAlreadyExistsException exception) {
        return ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .code("PROFILE_ALREADY_EXISTS")
                .message(exception.getMessage())
                .build();
    }

    @ExceptionHandler(UsernameAlreadyExistsException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleUsernameAlreadyExists(UsernameAlreadyExistsException exception) {
        return ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .code("USERNAME_ALREADY_EXISTS")
                .message(exception.getMessage())
                .build();
    }

    @ExceptionHandler(EmailAlreadyExistsException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleEmailAlreadyExists(EmailAlreadyExistsException exception) {
        return ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .code("EMAIL_ALREADY_EXISTS")
                .message(exception.getMessage())
                .build();
    }
}
