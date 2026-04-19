package com.secondshelf.exchangeservice.exception;

public class ExchangeConflictException extends ExchangeException {

    public ExchangeConflictException(String code, String message) {
        super(code, message);
    }
}
