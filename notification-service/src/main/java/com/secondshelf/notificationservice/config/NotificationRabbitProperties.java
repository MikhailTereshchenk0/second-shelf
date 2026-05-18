package com.secondshelf.notificationservice.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "notification.rabbitmq")
public class NotificationRabbitProperties {

    private String exchange = "exchange.events";
    private String queue = "notification.exchange-events";
    private String routingKeyPattern = "exchange.request.*";
}
