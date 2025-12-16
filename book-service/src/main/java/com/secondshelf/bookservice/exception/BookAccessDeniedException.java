package com.secondshelf.bookservice.exception;

public class BookAccessDeniedException extends RuntimeException {
    public BookAccessDeniedException(Long bookId) {
        super("Access denied for book id = " + bookId);
    }
}
