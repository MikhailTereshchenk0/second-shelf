package com.secondshelf.exchangeservice.outbox;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ExchangeEventType {
    EXCHANGE_STATUS_CHANGED("exchange.status.changed");

    private final String routingKey;
}
