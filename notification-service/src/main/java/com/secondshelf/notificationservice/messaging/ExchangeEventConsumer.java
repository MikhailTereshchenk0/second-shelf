package com.secondshelf.notificationservice.messaging;

import com.secondshelf.notificationservice.exception.NotificationBadRequestException;
import com.secondshelf.notificationservice.observability.CorrelationId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ExchangeEventConsumer {

    private final ExchangeEventNotificationService exchangeEventNotificationService;

    @RabbitListener(queues = "${notification.rabbitmq.queue}")
    public void consumeExchangeEvent(ExchangeEventPayload eventPayload,
                                     @Header(name = CorrelationId.HEADER_NAME, required = false) String correlationIdHeader) {
        String correlationId = correlationIdHeader != null
                ? correlationIdHeader
                : eventPayload != null ? eventPayload.getCorrelationId() : null;

        try (CorrelationId.Scope ignored = CorrelationId.openScope(correlationId)) {
            log.info(
                    "Received exchange event eventId={}, eventType={}",
                    eventPayload != null ? eventPayload.getEventId() : null,
                    eventPayload != null ? eventPayload.getEventType() : null
            );
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
