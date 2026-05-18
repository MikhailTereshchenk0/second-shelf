package com.secondshelf.notificationservice.exception;

public class NotificationBadRequestException extends NotificationException {

    public NotificationBadRequestException(String code, String message) {
        super(code, message);
    }
}
