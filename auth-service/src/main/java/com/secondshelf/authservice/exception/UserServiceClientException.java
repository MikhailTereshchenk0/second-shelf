package com.secondshelf.authservice.exception;

import com.secondshelf.authservice.exception.advice.ErrorResponse;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class UserServiceClientException extends RuntimeException {
    private final HttpStatus status;
    private final ErrorResponse downstream;

    public UserServiceClientException(HttpStatus status, ErrorResponse downstream) {
        super(downstream != null ? downstream.getMessage() : "User-service error");
        this.status = status;
        this.downstream = downstream;
    }

    public UserServiceClientException(HttpStatus status, String message) {
        super(message);
        this.status = status;
        this.downstream = null;
    }
}
