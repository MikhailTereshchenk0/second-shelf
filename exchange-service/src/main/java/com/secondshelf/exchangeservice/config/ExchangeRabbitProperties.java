package com.secondshelf.exchangeservice.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "exchange.outbox.rabbitmq")
public class ExchangeRabbitProperties {

    private String exchange = "exchange.events";
    private String queue = "notification.exchange-events";
    private String routingKeyPattern = "exchange.request.*";
}
