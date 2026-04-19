package com.secondshelf.exchangeservice.exception;

public class ExchangeForbiddenException extends ExchangeException {

    public ExchangeForbiddenException(String code, String message) {
        super(code, message);
    }
}
