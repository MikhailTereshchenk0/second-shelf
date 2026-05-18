package com.secondshelf.notificationservice.exception;

public class NotificationNotFoundException extends NotificationException {

    public NotificationNotFoundException(String code, String message) {
        super(code, message);
    }
}
