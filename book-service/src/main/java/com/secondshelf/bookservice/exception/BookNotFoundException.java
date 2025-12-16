package com.secondshelf.bookservice.exception;

public class BookNotFoundException extends RuntimeException {
    public BookNotFoundException(Long id) {
        super("Book with id = " + id + " not found.");
    }
}
