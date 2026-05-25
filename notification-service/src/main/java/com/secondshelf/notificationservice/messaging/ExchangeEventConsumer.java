package com.secondshelf.notificationservice.messaging;

import com.secondshelf.notificationservice.exception.NotificationBadRequestException;
import com.secondshelf.notificationservice.observability.CorrelationId;
import com.secondshelf.notificationservice.observability.NotificationAsyncMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.ImmediateRequeueAmqpException;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ExchangeEventConsumer {

    private final ExchangeEventNotificationService exchangeEventNotificationService;
    private final NotificationAsyncMetrics notificationAsyncMetrics;

    @RabbitListener(queues = "${notification.rabbitmq.queue}")
    public void consumeExchangeEvent(ExchangeEventPayload eventPayload,
                                     @Header(name = CorrelationId.HEADER_NAME, required = false) String correlationIdHeader,
                                     @Header(name = AmqpHeaders.REDELIVERED, required = false) Boolean redelivered) {
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
            notificationAsyncMetrics.incrementDeadLettered(resolveEventType(eventPayload), "invalid");
            log.error(
                    "Rejecting non-retryable exchange event to DLQ eventId={}, eventType={}",
                    eventPayload != null ? eventPayload.getEventId() : null,
                    eventPayload != null ? eventPayload.getEventType() : null,
                    ex
            );
            throw new AmqpRejectAndDontRequeueException(ex.getMessage(), ex);
        } catch (RuntimeException ex) {
            if (Boolean.TRUE.equals(redelivered)) {
                notificationAsyncMetrics.incrementDeadLettered(resolveEventType(eventPayload), "retries_exhausted");
                log.error(
                        "Dead-lettering exchange event after retry exhaustion eventId={}, eventType={}",
                        eventPayload != null ? eventPayload.getEventId() : null,
                        eventPayload != null ? eventPayload.getEventType() : null,
                        ex
                );
                throw new AmqpRejectAndDontRequeueException("Retries exhausted for exchange event.", ex);
            }

            notificationAsyncMetrics.incrementRetried(resolveEventType(eventPayload));
            log.warn(
                    "Requeueing exchange event after transient processing failure eventId={}, eventType={}",
                    eventPayload != null ? eventPayload.getEventId() : null,
                    eventPayload != null ? eventPayload.getEventType() : null,
                    ex
            );
            throw new ImmediateRequeueAmqpException("Retrying exchange event after transient processing failure.", ex);
        }
    }

    private String resolveEventType(ExchangeEventPayload eventPayload) {
        return eventPayload != null ? eventPayload.getEventType() : "unknown";
    }
}
