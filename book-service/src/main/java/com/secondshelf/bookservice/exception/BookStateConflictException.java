package com.secondshelf.bookservice.exception;

public class BookStateConflictException extends RuntimeException {

    private final String code;

    public BookStateConflictException(String message) {
        this("CONFLICT", message);
    }

    public BookStateConflictException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
