package com.secondshelf.exchangeservice.exception;

import lombok.Getter;

@Getter
public abstract class ExchangeException extends RuntimeException {

    private final String code;

    protected ExchangeException(String code, String message) {
        super(message);
        this.code = code;
    }
}
