package com.secondshelf.notificationservice.messaging;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;

@Getter
@RequiredArgsConstructor
public enum ExchangeEventType {
    EXCHANGE_REQUEST_CREATED("exchange.request.created"),
    EXCHANGE_REQUEST_ACCEPTED("exchange.request.accepted"),
    EXCHANGE_REQUEST_DECLINED("exchange.request.declined"),
    EXCHANGE_REQUEST_CANCELLED("exchange.request.cancelled"),
    EXCHANGE_REQUEST_COMPLETED("exchange.request.completed");

    private final String value;

    public static ExchangeEventType fromValue(String value) {
        return Arrays.stream(values())
                .filter(eventType -> eventType.value.equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported exchange event type: " + value));
    }
}
