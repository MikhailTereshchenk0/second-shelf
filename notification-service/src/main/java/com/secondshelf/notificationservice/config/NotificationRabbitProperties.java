package com.secondshelf.notificationservice.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

@Getter
@Setter
@ConfigurationProperties(prefix = "notification.rabbitmq")
public class NotificationRabbitProperties {

    private String exchange = "exchange.events";
    private String queue = "notification.exchange-events";
    private String routingKeyPattern = "exchange.request.*";
    private String deadLetterExchange;
    private String deadLetterQueue;
    private String deadLetterRoutingKey;

    public String resolveDeadLetterExchange() {
        return StringUtils.hasText(deadLetterExchange) ? deadLetterExchange : queue + ".dlx";
    }

    public String resolveDeadLetterQueue() {
        return StringUtils.hasText(deadLetterQueue) ? deadLetterQueue : queue + ".dlq";
    }

    public String resolveDeadLetterRoutingKey() {
        return StringUtils.hasText(deadLetterRoutingKey) ? deadLetterRoutingKey : resolveDeadLetterQueue();
    }
}
