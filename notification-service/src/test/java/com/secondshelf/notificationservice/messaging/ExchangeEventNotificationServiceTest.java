package com.secondshelf.notificationservice.messaging;

import com.secondshelf.notificationservice.entity.NotificationChannel;
import com.secondshelf.notificationservice.entity.NotificationType;
import com.secondshelf.notificationservice.entity.ProcessedEvent;
import com.secondshelf.notificationservice.exception.NotificationBadRequestException;
import com.secondshelf.notificationservice.observability.NotificationAsyncMetrics;
import com.secondshelf.notificationservice.repository.ProcessedEventRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExchangeEventNotificationServiceTest {

    @Mock
    private ProcessedEventRepository processedEventRepository;

    @Mock
    private NotificationChannelDispatcher notificationChannelDispatcher;

    private ExchangeEventNotificationService exchangeEventNotificationService;
    private SimpleMeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        exchangeEventNotificationService = new ExchangeEventNotificationService(
                processedEventRepository,
                notificationChannelDispatcher,
                new NotificationAsyncMetrics(meterRegistry)
        );
    }

    @Test
    void processShouldCreateOwnerNotificationForCreatedEvent() {
        // arrange
        ExchangeEventPayload payload = sampleEvent("exchange.request.created");
        when(processedEventRepository.existsById(payload.getEventId())).thenReturn(false);
        when(processedEventRepository.saveAndFlush(any(ProcessedEvent.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // act
        exchangeEventNotificationService.process(payload);

        // assert
        NotificationDeliveryCommand notification = captureDeliveryCommands().get(0);
        assertEquals(NotificationChannel.IN_APP, notification.getChannel());
        assertEquals(55L, notification.getUserId());
        assertEquals(NotificationType.EXCHANGE_REQUEST_CREATED, notification.getType());
        assertEquals("New exchange request", notification.getTitle());
        assertEquals(
                "alice wants to exchange \"Dune\" by Frank Herbert for your \"The Left Hand of Darkness\" by Ursula K. Le Guin. Message from requester: Can meet this weekend.",
                notification.getMessage()
        );
        assertEquals("EXCHANGE_REQUEST", notification.getRelatedEntityType());
        assertEquals("101", notification.getRelatedEntityId());
        assertEquals(payload.getOccurredAt(), notification.getOccurredAt());
        assertEquals(
                1.0,
                meterRegistry.get("notification.exchange.events.processed")
                        .tag("event_type", "exchange.request.created")
                        .counter()
                        .count()
        );
    }

    @Test
    void processShouldCreateRequesterNotificationForAcceptedEvent() {
        // arrange
        ExchangeEventPayload payload = sampleEvent("exchange.request.accepted");
        payload.setInitiatorUserId(55L);
        payload.setInitiatorUsername("owner");
        when(processedEventRepository.existsById(payload.getEventId())).thenReturn(false);
        when(processedEventRepository.saveAndFlush(any(ProcessedEvent.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // act
        exchangeEventNotificationService.process(payload);

        // assert
        NotificationDeliveryCommand notification = captureDeliveryCommands().get(0);
        assertEquals(42L, notification.getUserId());
        assertEquals(NotificationType.EXCHANGE_REQUEST_ACCEPTED, notification.getType());
        assertEquals("Your exchange request was accepted", notification.getTitle());
        assertEquals(
                "owner accepted your request to exchange \"Dune\" by Frank Herbert for \"The Left Hand of Darkness\" by Ursula K. Le Guin.",
                notification.getMessage()
        );
    }

    @Test
    void processShouldCreateRequesterNotificationForDeclinedEvent() {
        // arrange
        ExchangeEventPayload payload = sampleEvent("exchange.request.declined");
        payload.setInitiatorUserId(55L);
        payload.setInitiatorUsername("owner");
        when(processedEventRepository.existsById(payload.getEventId())).thenReturn(false);
        when(processedEventRepository.saveAndFlush(any(ProcessedEvent.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // act
        exchangeEventNotificationService.process(payload);

        // assert
        NotificationDeliveryCommand notification = captureDeliveryCommands().get(0);
        assertEquals(42L, notification.getUserId());
        assertEquals(NotificationType.EXCHANGE_REQUEST_DECLINED, notification.getType());
        assertEquals("Your exchange request was declined", notification.getTitle());
        assertEquals(
                "owner declined your request to exchange \"Dune\" by Frank Herbert for \"The Left Hand of Darkness\" by Ursula K. Le Guin.",
                notification.getMessage()
        );
    }

    @Test
    void processShouldCreateOwnerNotificationForCancelledEvent() {
        // arrange
        ExchangeEventPayload payload = sampleEvent("exchange.request.cancelled");
        when(processedEventRepository.existsById(payload.getEventId())).thenReturn(false);
        when(processedEventRepository.saveAndFlush(any(ProcessedEvent.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // act
        exchangeEventNotificationService.process(payload);

        // assert
        NotificationDeliveryCommand notification = captureDeliveryCommands().get(0);
        assertEquals(55L, notification.getUserId());
        assertEquals(NotificationType.EXCHANGE_REQUEST_CANCELLED, notification.getType());
        assertEquals("Exchange request cancelled", notification.getTitle());
        assertEquals(
                "alice cancelled the request to exchange \"Dune\" by Frank Herbert for your \"The Left Hand of Darkness\" by Ursula K. Le Guin.",
                notification.getMessage()
        );
    }

    @Test
    void processShouldCreateCounterpartyNotificationForCompletionConfirmedEvent() {
        // arrange
        ExchangeEventPayload payload = sampleEvent("exchange.request.completion_confirmed");
        payload.setCompletedByUserId(42L);
        payload.setStatus("COMPLETION_PENDING");
        when(processedEventRepository.existsById(payload.getEventId())).thenReturn(false);
        when(processedEventRepository.saveAndFlush(any(ProcessedEvent.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // act
        exchangeEventNotificationService.process(payload);

        // assert
        NotificationDeliveryCommand notification = captureDeliveryCommands().get(0);
        assertEquals(55L, notification.getUserId());
        assertEquals(NotificationType.EXCHANGE_REQUEST_COMPLETION_CONFIRMED, notification.getType());
        assertEquals("Your confirmation is needed", notification.getTitle());
        assertEquals(
                "alice confirmed the exchange of \"Dune\" by Frank Herbert and \"The Left Hand of Darkness\" by Ursula K. Le Guin. Confirm it from your side to complete the exchange.",
                notification.getMessage()
        );
    }

    @Test
    void processShouldCreateNotificationsForBothUsersWhenCompleted() {
        // arrange
        ExchangeEventPayload payload = sampleEvent("exchange.request.completed");
        when(processedEventRepository.existsById(payload.getEventId())).thenReturn(false);
        when(processedEventRepository.saveAndFlush(any(ProcessedEvent.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // act
        exchangeEventNotificationService.process(payload);

        // assert
        List<NotificationDeliveryCommand> notifications = captureDeliveryCommands();
        assertEquals(2, notifications.size());
        assertEquals(List.of(42L, 55L), notifications.stream().map(NotificationDeliveryCommand::getUserId).toList());
        assertEquals(List.of(
                NotificationType.EXCHANGE_REQUEST_COMPLETED,
                NotificationType.EXCHANGE_REQUEST_COMPLETED
        ), notifications.stream().map(NotificationDeliveryCommand::getType).toList());
        assertEquals(
                List.of(
                        "Your exchange of \"Dune\" by Frank Herbert and \"The Left Hand of Darkness\" by Ursula K. Le Guin is complete.",
                        "Your exchange of \"Dune\" by Frank Herbert and \"The Left Hand of Darkness\" by Ursula K. Le Guin is complete."
                ),
                notifications.stream().map(NotificationDeliveryCommand::getMessage).toList()
        );
        assertEquals(
                2.0,
                meterRegistry.get("notification.exchange.notifications.created")
                        .tag("event_type", "exchange.request.completed")
                        .counter()
                        .count()
        );
        assertEquals(
                1.0,
                meterRegistry.get("notification.exchange.events.processed")
                        .tag("event_type", "exchange.request.completed")
                        .counter()
                        .count()
        );
    }

    @Test
    void processShouldFallbackToGenericTextForLegacyPayloadWithoutSnapshots() {
        // arrange
        ExchangeEventPayload payload = ExchangeEventPayload.builder()
                .eventId(UUID.randomUUID())
                .correlationId("corr-notification-legacy-123")
                .eventType("exchange.request.created")
                .occurredAt(LocalDateTime.of(2026, 5, 18, 22, 30))
                .exchangeRequestId(101L)
                .requesterId(42L)
                .ownerId(55L)
                .requestedBookId(1001L)
                .offeredBookId(2002L)
                .status("PENDING")
                .build();
        when(processedEventRepository.existsById(payload.getEventId())).thenReturn(false);
        when(processedEventRepository.saveAndFlush(any(ProcessedEvent.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // act
        exchangeEventNotificationService.process(payload);

        // assert
        NotificationDeliveryCommand notification = captureDeliveryCommands().get(0);
        assertEquals(
                "Another reader wants to exchange book #2002 for your book #1001.",
                notification.getMessage()
        );
    }

    @Test
    void processShouldSkipAlreadyProcessedEvent() {
        // arrange
        ExchangeEventPayload payload = sampleEvent("exchange.request.created");
        when(processedEventRepository.existsById(payload.getEventId())).thenReturn(true);

        // act
        exchangeEventNotificationService.process(payload);

        // assert
        verify(processedEventRepository, never()).saveAndFlush(any(ProcessedEvent.class));
        verify(notificationChannelDispatcher, never()).deliver(any());
        assertEquals(
                1.0,
                meterRegistry.get("notification.exchange.events.ignored")
                        .tag("event_type", "exchange.request.created")
                        .tag("reason", "duplicate")
                        .counter()
                        .count()
        );
    }

    @Test
    void processShouldSkipDuplicateEventOnProcessedEventConflict() {
        // arrange
        ExchangeEventPayload payload = sampleEvent("exchange.request.created");
        when(processedEventRepository.existsById(payload.getEventId())).thenReturn(false);
        when(processedEventRepository.saveAndFlush(any(ProcessedEvent.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        // act
        exchangeEventNotificationService.process(payload);

        // assert
        verify(notificationChannelDispatcher, never()).deliver(any());
        assertEquals(
                1.0,
                meterRegistry.get("notification.exchange.events.ignored")
                        .tag("event_type", "exchange.request.created")
                        .tag("reason", "duplicate")
                        .counter()
                        .count()
        );
    }

    @Test
    void processShouldRejectUnsupportedEventType() {
        // arrange
        ExchangeEventPayload payload = sampleEvent("exchange.request.unknown");

        // act
        NotificationBadRequestException exception = assertThrows(
                NotificationBadRequestException.class,
                () -> exchangeEventNotificationService.process(payload)
        );

        // assert
        assertEquals("UNSUPPORTED_EXCHANGE_EVENT_TYPE", exception.getCode());
        verify(processedEventRepository, never()).existsById(any());
        verify(processedEventRepository, never()).saveAndFlush(any(ProcessedEvent.class));
        verify(notificationChannelDispatcher, never()).deliver(any());
        assertEquals(
                1.0,
                meterRegistry.get("notification.exchange.events.ignored")
                        .tag("event_type", "exchange.request.unknown")
                        .tag("reason", "invalid")
                        .counter()
                        .count()
        );
    }

    @SuppressWarnings("unchecked")
    private List<NotificationDeliveryCommand> captureDeliveryCommands() {
        ArgumentCaptor<NotificationDeliveryCommand> captor = ArgumentCaptor.forClass(NotificationDeliveryCommand.class);
        verify(notificationChannelDispatcher, org.mockito.Mockito.atLeastOnce()).deliver(captor.capture());
        return captor.getAllValues();
    }

    private ExchangeEventPayload sampleEvent(String eventType) {
        return ExchangeEventPayload.builder()
                .schemaVersion(2)
                .eventId(UUID.randomUUID())
                .correlationId("corr-notification-123")
                .eventType(eventType)
                .occurredAt(LocalDateTime.of(2026, 5, 18, 22, 30))
                .exchangeRequestId(101L)
                .initiatorUserId(42L)
                .initiatorUsername("alice")
                .requesterId(42L)
                .ownerId(55L)
                .requestedBookId(1001L)
                .requestedBookTitle("The Left Hand of Darkness")
                .requestedBookAuthor("Ursula K. Le Guin")
                .offeredBookId(2002L)
                .offeredBookTitle("Dune")
                .offeredBookAuthor("Frank Herbert")
                .requestMessage("Can meet this weekend.")
                .status("PENDING")
                .build();
    }
}
