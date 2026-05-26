package com.secondshelf.exchangeservice.outbox;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ExchangeEventType {
    EXCHANGE_REQUEST_CREATED("exchange.request.created"),
    EXCHANGE_REQUEST_ACCEPTED("exchange.request.accepted"),
    EXCHANGE_REQUEST_DECLINED("exchange.request.declined"),
    EXCHANGE_REQUEST_CANCELLED("exchange.request.cancelled"),
    EXCHANGE_REQUEST_COMPLETION_CONFIRMED("exchange.request.completion_confirmed"),
    EXCHANGE_REQUEST_REPAIR_REQUIRED("exchange.request.repair_required"),
    EXCHANGE_REQUEST_COMPLETED("exchange.request.completed");

    private final String value;
}
