package com.secondshelf.exchangeservice.exception;

public class ExchangeNotFoundException extends ExchangeException {

    public ExchangeNotFoundException(String code, String message) {
        super(code, message);
    }
}
