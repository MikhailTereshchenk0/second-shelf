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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
                new ExchangeAsyncMetrics(meterRegistry),
                100L,
                3
        );

        lenient().when(exchangeRabbitProperties.getExchange()).thenReturn("exchange.events");
        lenient().when(transactionOperations.execute(any(TransactionCallback.class))).thenAnswer(invocation -> {
            TransactionCallback<Object> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });
    }

    @Test
    void publishPendingEventsShouldMarkEventPublishedOnlyAfterBrokerAck() {
        OutboxEvent event = buildEvent("exchange.request.created", "corr-publish-123");

        when(outboxEventRepository.findTop100ByStatusOrderByCreatedAtAsc(OutboxEventStatus.PENDING))
                .thenReturn(List.of(event));
        when(outboxEventRepository.findById(1L)).thenReturn(Optional.of(event));

        doAnswer(invocation -> {
            Message message = invocation.getArgument(2);
            CorrelationData correlationData = invocation.getArgument(3);

            assertEquals(event.getPayload(), new String(message.getBody(), StandardCharsets.UTF_8));
            assertEquals("application/json", message.getMessageProperties().getContentType());
            assertEquals(event.getEventId().toString(), message.getMessageProperties().getMessageId());
            assertEquals(event.getEventId().toString(), message.getMessageProperties().getHeaders().get("eventId"));
            assertEquals("exchange.request.created", message.getMessageProperties().getHeaders().get("eventType"));
            assertEquals("corr-publish-123", message.getMessageProperties().getHeaders().get(CorrelationId.HEADER_NAME));

            assertEquals(OutboxEventStatus.PENDING, event.getStatus());
            assertNull(event.getPublishedAt());

            correlationData.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbitTemplate).send(eq("exchange.events"), eq("exchange.request.created"), any(Message.class), any(CorrelationData.class));

        exchangeOutboxPublisher.publishPendingEvents();

        verify(outboxEventRepository).save(event);
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
    void publishPendingEventsShouldIncrementAttemptsWhenBrokerNacksPublish() {
        OutboxEvent event = buildEvent("exchange.request.accepted", "corr-nack-123");

        when(outboxEventRepository.findTop100ByStatusOrderByCreatedAtAsc(OutboxEventStatus.PENDING))
                .thenReturn(List.of(event));
        when(outboxEventRepository.findById(1L)).thenReturn(Optional.of(event));

        doAnswer(invocation -> {
            CorrelationData correlationData = invocation.getArgument(3);
            correlationData.getFuture().complete(new CorrelationData.Confirm(false, "broker-nack"));
            return null;
        }).when(rabbitTemplate).send(eq("exchange.events"), eq("exchange.request.accepted"), any(Message.class), any(CorrelationData.class));

        exchangeOutboxPublisher.publishPendingEvents();

        verify(outboxEventRepository).save(event);
        assertEquals(1, event.getAttemptsCount());
        assertEquals(OutboxEventStatus.PENDING, event.getStatus());
        assertNull(event.getPublishedAt());
        assertNotNull(event.getLastError());
        assertEquals(
                1.0,
                meterRegistry.get("exchange.outbox.publish.errors")
                        .tag("event_type", "exchange.request.accepted")
                        .counter()
                        .count()
        );
        assertEquals(
                1.0,
                meterRegistry.get("exchange.outbox.publish.retries")
                        .tag("event_type", "exchange.request.accepted")
                        .counter()
                        .count()
        );
    }

    @Test
    void publishPendingEventsShouldIncrementAttemptsWhenMessageIsReturnedAsUnroutable() {
        OutboxEvent event = buildEvent("exchange.request.declined", "corr-return-123");

        when(outboxEventRepository.findTop100ByStatusOrderByCreatedAtAsc(OutboxEventStatus.PENDING))
                .thenReturn(List.of(event));
        when(outboxEventRepository.findById(1L)).thenReturn(Optional.of(event));

        doAnswer(invocation -> {
            Message message = invocation.getArgument(2);
            CorrelationData correlationData = invocation.getArgument(3);
            correlationData.setReturned(new ReturnedMessage(
                    message,
                    312,
                    "NO_ROUTE",
                    "exchange.events",
                    "exchange.request.declined"
            ));
            correlationData.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbitTemplate).send(eq("exchange.events"), eq("exchange.request.declined"), any(Message.class), any(CorrelationData.class));

        exchangeOutboxPublisher.publishPendingEvents();

        verify(outboxEventRepository).save(event);
        assertEquals(1, event.getAttemptsCount());
        assertEquals(OutboxEventStatus.PENDING, event.getStatus());
        assertNull(event.getPublishedAt());
        assertNotNull(event.getLastError());
        assertEquals(
                1.0,
                meterRegistry.get("exchange.outbox.publish.errors")
                        .tag("event_type", "exchange.request.declined")
                        .counter()
                        .count()
        );
    }

    @Test
    void publishPendingEventsShouldIncrementAttemptsWhenPublishFailsImmediately() {
        OutboxEvent event = buildEvent("exchange.request.accepted", "corr-fail-123");

        when(outboxEventRepository.findTop100ByStatusOrderByCreatedAtAsc(OutboxEventStatus.PENDING))
                .thenReturn(List.of(event));
        when(outboxEventRepository.findById(1L)).thenReturn(Optional.of(event));
        doThrow(new AmqpException("RabbitMQ is unavailable") {
        }).when(rabbitTemplate).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));

        exchangeOutboxPublisher.publishPendingEvents();

        verify(outboxEventRepository).save(event);
        assertEquals(1, event.getAttemptsCount());
        assertEquals(OutboxEventStatus.PENDING, event.getStatus());
        assertNull(event.getPublishedAt());
        assertNotNull(event.getLastError());
        assertEquals(
                1.0,
                meterRegistry.get("exchange.outbox.publish.errors")
                        .tag("event_type", "exchange.request.accepted")
                        .counter()
                        .count()
        );
    }

    @Test
    void publishPendingEventsShouldTreatMalformedPayloadAsPublishFailure() {
        OutboxEvent event = OutboxEvent.builder()
                .id(1L)
                .eventId(UUID.randomUUID())
                .eventType("exchange.request.completed")
                .payload("{not-valid-json")
                .status(OutboxEventStatus.PENDING)
                .attemptsCount(0)
                .createdAt(LocalDateTime.now())
                .build();

        when(outboxEventRepository.findTop100ByStatusOrderByCreatedAtAsc(OutboxEventStatus.PENDING))
                .thenReturn(List.of(event));
        when(outboxEventRepository.findById(1L)).thenReturn(Optional.of(event));

        exchangeOutboxPublisher.publishPendingEvents();

        verifyNoInteractions(rabbitTemplate);
        verify(outboxEventRepository).save(event);
        assertEquals(1, event.getAttemptsCount());
        assertEquals(OutboxEventStatus.PENDING, event.getStatus());
        assertNotNull(event.getLastError());
        assertEquals(
                1.0,
                meterRegistry.get("exchange.outbox.publish.errors")
                        .tag("event_type", "exchange.request.completed")
                        .counter()
                        .count()
        );
    }

    @Test
    void publishPendingEventsShouldMarkEventTerminallyFailedWhenAttemptsAreExhausted() {
        OutboxEvent event = buildEvent("exchange.request.completed", "corr-terminal-123");
        event.setAttemptsCount(2);

        when(outboxEventRepository.findTop100ByStatusOrderByCreatedAtAsc(OutboxEventStatus.PENDING))
                .thenReturn(List.of(event));
        when(outboxEventRepository.findById(1L)).thenReturn(Optional.of(event));
        doThrow(new AmqpException("RabbitMQ is unavailable") {
        }).when(rabbitTemplate).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));

        exchangeOutboxPublisher.publishPendingEvents();

        verify(outboxEventRepository).save(event);
        assertEquals(3, event.getAttemptsCount());
        assertEquals(OutboxEventStatus.TERMINAL_FAILED, event.getStatus());
        assertNotNull(event.getFailedAt());
        assertNotNull(event.getLastError());
        assertEquals(
                1.0,
                meterRegistry.get("exchange.outbox.events.terminal_failed")
                        .tag("event_type", "exchange.request.completed")
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
