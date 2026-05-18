package com.secondshelf.notificationservice.exception;

public class NotificationForbiddenException extends NotificationException {

    public NotificationForbiddenException(String code, String message) {
        super(code, message);
    }
}
