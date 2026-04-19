package com.secondshelf.exchangeservice.exception;

public class ExchangeBadRequestException extends ExchangeException {

    public ExchangeBadRequestException(String code, String message) {
        super(code, message);
    }
}
