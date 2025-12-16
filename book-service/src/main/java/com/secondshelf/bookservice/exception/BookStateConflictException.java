package com.secondshelf.bookservice.exception;

public class BookStateConflictException extends RuntimeException {
    public BookStateConflictException(String message) {
        super(message);
    }
}
