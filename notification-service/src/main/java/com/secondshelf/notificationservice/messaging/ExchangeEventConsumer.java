package com.secondshelf.notificationservice.messaging;

import com.secondshelf.notificationservice.exception.NotificationBadRequestException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ExchangeEventConsumer {

    private final ExchangeEventNotificationService exchangeEventNotificationService;

    @RabbitListener(queues = "${notification.rabbitmq.queue}")
    public void consumeExchangeEvent(ExchangeEventPayload eventPayload) {
        try {
            exchangeEventNotificationService.process(eventPayload);
        } catch (NotificationBadRequestException ex) {
            log.error(
                    "Rejecting non-retryable exchange event eventId={}, eventType={}",
                    eventPayload != null ? eventPayload.getEventId() : null,
                    eventPayload != null ? eventPayload.getEventType() : null,
                    ex
            );
            throw new AmqpRejectAndDontRequeueException(ex.getMessage(), ex);
        }
    }
}
