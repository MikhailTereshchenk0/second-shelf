package com.secondshelf.bookservice.exception;

public class BookNotFoundException extends RuntimeException {

    private final String code;

    public BookNotFoundException(Long id) {
        this("BOOK_NOT_FOUND", "Book with id = " + id + " not found.");
    }

    public BookNotFoundException(String code, String message) {
        super(message);
        this.code = code;
    }

    public static BookNotFoundException notAvailableForPublicView(Long id) {
        return new BookNotFoundException(
                "BOOK_NOT_AVAILABLE_FOR_PUBLIC_VIEW",
                "Book with id = " + id + " not found."
        );
    }

    public String getCode() {
        return code;
    }
}
