package com.secondshelf.exchangeservice.outbox;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.secondshelf.exchangeservice.config.ExchangeRabbitProperties;
import com.secondshelf.exchangeservice.entity.OutboxEvent;
import com.secondshelf.exchangeservice.entity.OutboxEventStatus;
import com.secondshelf.exchangeservice.observability.CorrelationId;
import com.secondshelf.exchangeservice.observability.ExchangeAsyncMetrics;
import com.secondshelf.exchangeservice.repository.OutboxEventRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExchangeOutboxPublisherTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private RabbitTemplate rabbitTemplate;

    @Mock
    private ExchangeRabbitProperties exchangeRabbitProperties;

    @Mock
    private TransactionOperations transactionOperations;

    private ExchangeOutboxPublisher exchangeOutboxPublisher;
    private JsonMapper objectMapper;
    private SimpleMeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        objectMapper = JsonMapper.builder().findAndAddModules().build();
        meterRegistry = new SimpleMeterRegistry();
        exchangeOutboxPublisher = new ExchangeOutboxPublisher(
                outboxEventRepository,
                rabbitTemplate,
                exchangeRabbitProperties,
                transactionOperations,
                objectMapper,
                new ExchangeAsyncMetrics(meterRegistry)
        );

        when(exchangeRabbitProperties.getExchange()).thenReturn("exchange.events");
        when(transactionOperations.execute(any(TransactionCallback.class))).thenAnswer(invocation -> {
            TransactionCallback<Object> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });
    }

    @Test
    void publishPendingEventsShouldSendEventAndMarkItPublished() {
        // arrange
        OutboxEvent event = buildEvent("exchange.request.created", "corr-publish-123");

        when(outboxEventRepository.findTop100ByStatusOrderByCreatedAtAsc(OutboxEventStatus.PENDING))
                .thenReturn(List.of(event));
        when(outboxEventRepository.findById(1L)).thenReturn(Optional.of(event));

        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);

        // act
        exchangeOutboxPublisher.publishPendingEvents();

        // assert
        verify(rabbitTemplate).send(eq("exchange.events"), eq("exchange.request.created"), messageCaptor.capture());
        verify(outboxEventRepository).save(event);

        Message message = messageCaptor.getValue();
        assertEquals(event.getPayload(), new String(message.getBody(), StandardCharsets.UTF_8));
        assertEquals("application/json", message.getMessageProperties().getContentType());
        assertEquals(event.getEventId().toString(), message.getMessageProperties().getMessageId());
        assertEquals(event.getEventId().toString(), message.getMessageProperties().getHeaders().get("eventId"));
        assertEquals("exchange.request.created", message.getMessageProperties().getHeaders().get("eventType"));
        assertEquals("corr-publish-123", message.getMessageProperties().getHeaders().get(CorrelationId.HEADER_NAME));
        assertEquals(OutboxEventStatus.PUBLISHED, event.getStatus());
        assertNotNull(event.getPublishedAt());
        assertEquals(
                1.0,
                meterRegistry.get("exchange.outbox.events.published")
                        .tag("event_type", "exchange.request.created")
                        .counter()
                        .count()
        );
    }

    @Test
    void publishPendingEventsShouldIncrementAttemptsWhenPublishFails() {
        // arrange
        OutboxEvent event = buildEvent("exchange.request.accepted", "corr-fail-123");

        when(outboxEventRepository.findTop100ByStatusOrderByCreatedAtAsc(OutboxEventStatus.PENDING))
                .thenReturn(List.of(event));
        when(outboxEventRepository.findById(1L)).thenReturn(Optional.of(event));
        doThrow(new AmqpException("RabbitMQ is unavailable") {
        }).when(rabbitTemplate).send(anyString(), anyString(), any(Message.class));

        // act
        exchangeOutboxPublisher.publishPendingEvents();

        // assert
        verify(outboxEventRepository).save(event);
        assertEquals(1, event.getAttemptsCount());
        assertEquals(OutboxEventStatus.PENDING, event.getStatus());
        assertNull(event.getPublishedAt());
        assertEquals(
                1.0,
                meterRegistry.get("exchange.outbox.publish.errors")
                        .tag("event_type", "exchange.request.accepted")
                        .counter()
                        .count()
        );
    }

    private OutboxEvent buildEvent(String eventType, String correlationId) {
        UUID eventId = UUID.randomUUID();
        String payload = """
                {"eventId":"%s","correlationId":"%s","eventType":"%s"}
                """.formatted(eventId, correlationId, eventType);

        return OutboxEvent.builder()
                .id(1L)
                .eventId(eventId)
                .eventType(eventType)
                .payload(payload)
                .status(OutboxEventStatus.PENDING)
                .attemptsCount(0)
                .createdAt(LocalDateTime.now())
                .build();
    }
}
