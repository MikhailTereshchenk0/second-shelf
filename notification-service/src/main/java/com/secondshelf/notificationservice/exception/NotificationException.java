package com.secondshelf.notificationservice.exception;

import lombok.Getter;

@Getter
public abstract class NotificationException extends RuntimeException {

    private final String code;

    protected NotificationException(String code, String message) {
        super(message);
        this.code = code;
    }
}
