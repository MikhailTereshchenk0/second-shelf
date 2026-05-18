package com.secondshelf.exchangeservice.outbox;

import com.secondshelf.exchangeservice.config.ExchangeRabbitProperties;
import com.secondshelf.exchangeservice.entity.OutboxEvent;
import com.secondshelf.exchangeservice.entity.OutboxEventStatus;
import com.secondshelf.exchangeservice.repository.OutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

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

    @InjectMocks
    private ExchangeOutboxPublisher exchangeOutboxPublisher;

    @BeforeEach
    void setUp() {
        when(exchangeRabbitProperties.getExchange()).thenReturn("exchange.events");
        when(transactionOperations.execute(any(TransactionCallback.class))).thenAnswer(invocation -> {
            TransactionCallback<Object> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });
    }

    @Test
    void publishPendingEventsShouldSendEventAndMarkItPublished() {
        // arrange
        OutboxEvent event = OutboxEvent.builder()
                .id(1L)
                .eventId(UUID.randomUUID())
                .eventType("exchange.request.created")
                .payload("{\"eventType\":\"exchange.request.created\"}")
                .status(OutboxEventStatus.PENDING)
                .attemptsCount(0)
                .createdAt(LocalDateTime.now())
                .build();

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
        assertEquals("{\"eventType\":\"exchange.request.created\"}", new String(message.getBody(), StandardCharsets.UTF_8));
        assertEquals("application/json", message.getMessageProperties().getContentType());
        assertEquals(event.getEventId().toString(), message.getMessageProperties().getMessageId());
        assertEquals(event.getEventId().toString(), message.getMessageProperties().getHeaders().get("eventId"));
        assertEquals("exchange.request.created", message.getMessageProperties().getHeaders().get("eventType"));
        assertEquals(OutboxEventStatus.PUBLISHED, event.getStatus());
        assertNotNull(event.getPublishedAt());
    }

    @Test
    void publishPendingEventsShouldIncrementAttemptsWhenPublishFails() {
        // arrange
        OutboxEvent event = OutboxEvent.builder()
                .id(1L)
                .eventId(UUID.randomUUID())
                .eventType("exchange.request.accepted")
                .payload("{\"eventType\":\"exchange.request.accepted\"}")
                .status(OutboxEventStatus.PENDING)
                .attemptsCount(0)
                .createdAt(LocalDateTime.now())
                .build();

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
    }
}
