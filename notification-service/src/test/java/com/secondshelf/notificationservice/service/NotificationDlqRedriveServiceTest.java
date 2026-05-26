package com.secondshelf.notificationservice.service;

import com.secondshelf.notificationservice.config.NotificationRabbitProperties;
import com.secondshelf.notificationservice.dto.DlqRedriveResponse;
import com.secondshelf.notificationservice.observability.CorrelationId;
import com.secondshelf.notificationservice.observability.NotificationAsyncMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationDlqRedriveServiceTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    private NotificationRabbitProperties properties;
    private SimpleMeterRegistry meterRegistry;
    private NotificationDlqRedriveService service;

    @BeforeEach
    void setUp() {
        properties = new NotificationRabbitProperties();
        properties.setExchange("exchange.events");
        properties.setQueue("notification.exchange-events");
        properties.setRoutingKeyPattern("exchange.request.*");
        meterRegistry = new SimpleMeterRegistry();
        service = new NotificationDlqRedriveService(
                rabbitTemplate,
                properties,
                new NotificationAsyncMetrics(meterRegistry)
        );
    }

    @Test
    void redriveShouldRepublishMessageWithOriginalRoutingKeyAndHeaders() {
        Message message = MessageBuilder.withBody("{\"eventType\":\"exchange.request.created\"}".getBytes(StandardCharsets.UTF_8))
                .setHeader("eventId", "event-123")
                .setHeader("eventType", "exchange.request.created")
                .setHeader(CorrelationId.HEADER_NAME, "corr-redrive-123")
                .setHeader("x-death", List.of(Map.of("routing-keys", List.of("exchange.request.created"))))
                .build();

        when(rabbitTemplate.receive(properties.resolveDeadLetterQueue()))
                .thenReturn(message)
                .thenReturn(null);

        DlqRedriveResponse response = service.redrive(10);

        assertEquals(10, response.getRequestedLimit());
        assertEquals(1, response.getRedrivenCount());
        assertEquals(0, response.getSkippedCount());
        assertEquals(0, response.getFailedCount());
        assertEquals(0, response.getErrors().size());

        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(rabbitTemplate).send(
                org.mockito.ArgumentMatchers.eq("exchange.events"),
                org.mockito.ArgumentMatchers.eq("exchange.request.created"),
                messageCaptor.capture()
        );

        MessageProperties sentProperties = messageCaptor.getValue().getMessageProperties();
        assertEquals("event-123", sentProperties.getHeaders().get("eventId"));
        assertEquals("exchange.request.created", sentProperties.getHeaders().get("eventType"));
        assertEquals("corr-redrive-123", sentProperties.getHeaders().get(CorrelationId.HEADER_NAME));
        assertEquals(1.0, meterRegistry.get("notification.dlq.redrive.attempted").counter().count());
        assertEquals(1.0, meterRegistry.get("notification.dlq.redrive.succeeded").counter().count());
    }

    @Test
    void redriveShouldReportMalformedMessageAndReturnItToDlqWhenRoutingKeyIsMissing() {
        Message malformedMessage = MessageBuilder.withBody("{not-valid-json".getBytes(StandardCharsets.UTF_8))
                .setHeader("eventId", "event-bad")
                .setHeader("eventType", "exchange.request.created")
                .build();

        when(rabbitTemplate.receive(properties.resolveDeadLetterQueue()))
                .thenReturn(malformedMessage)
                .thenReturn(null);

        DlqRedriveResponse response = service.redrive(3);

        assertEquals(3, response.getRequestedLimit());
        assertEquals(0, response.getRedrivenCount());
        assertEquals(1, response.getSkippedCount());
        assertEquals(0, response.getFailedCount());
        assertEquals("event-bad", response.getErrors().get(0).getEventId());
        assertEquals("exchange.request.created", response.getErrors().get(0).getEventType());

        verify(rabbitTemplate).send(
                properties.resolveDeadLetterExchange(),
                properties.resolveDeadLetterRoutingKey(),
                malformedMessage
        );
        verify(rabbitTemplate, never()).send(
                org.mockito.ArgumentMatchers.eq("exchange.events"),
                org.mockito.ArgumentMatchers.eq("exchange.request.created"),
                org.mockito.ArgumentMatchers.any(Message.class)
        );
        assertEquals(1.0, meterRegistry.get("notification.dlq.redrive.attempted").counter().count());
        assertEquals(1.0, meterRegistry.get("notification.dlq.redrive.failed").counter().count());
    }
}
