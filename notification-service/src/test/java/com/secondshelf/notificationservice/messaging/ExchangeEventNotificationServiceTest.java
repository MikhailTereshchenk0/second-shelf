package com.secondshelf.notificationservice.messaging;

import com.secondshelf.notificationservice.entity.Notification;
import com.secondshelf.notificationservice.entity.NotificationType;
import com.secondshelf.notificationservice.entity.ProcessedEvent;
import com.secondshelf.notificationservice.exception.NotificationBadRequestException;
import com.secondshelf.notificationservice.repository.NotificationRepository;
import com.secondshelf.notificationservice.repository.ProcessedEventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExchangeEventNotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private ProcessedEventRepository processedEventRepository;

    @InjectMocks
    private ExchangeEventNotificationService exchangeEventNotificationService;

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
        Notification notification = captureSavedNotifications().get(0);
        assertEquals(55L, notification.getUserId());
        assertEquals(NotificationType.EXCHANGE_REQUEST_CREATED, notification.getType());
        assertEquals("New exchange request", notification.getTitle());
        assertEquals("EXCHANGE_REQUEST", notification.getRelatedEntityType());
        assertEquals("101", notification.getRelatedEntityId());
        assertEquals(payload.getOccurredAt(), notification.getCreatedAt());
    }

    @Test
    void processShouldCreateRequesterNotificationForAcceptedEvent() {
        // arrange
        ExchangeEventPayload payload = sampleEvent("exchange.request.accepted");
        when(processedEventRepository.existsById(payload.getEventId())).thenReturn(false);
        when(processedEventRepository.saveAndFlush(any(ProcessedEvent.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // act
        exchangeEventNotificationService.process(payload);

        // assert
        Notification notification = captureSavedNotifications().get(0);
        assertEquals(42L, notification.getUserId());
        assertEquals(NotificationType.EXCHANGE_REQUEST_ACCEPTED, notification.getType());
        assertEquals("Exchange request accepted", notification.getTitle());
    }

    @Test
    void processShouldCreateRequesterNotificationForDeclinedEvent() {
        // arrange
        ExchangeEventPayload payload = sampleEvent("exchange.request.declined");
        when(processedEventRepository.existsById(payload.getEventId())).thenReturn(false);
        when(processedEventRepository.saveAndFlush(any(ProcessedEvent.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // act
        exchangeEventNotificationService.process(payload);

        // assert
        Notification notification = captureSavedNotifications().get(0);
        assertEquals(42L, notification.getUserId());
        assertEquals(NotificationType.EXCHANGE_REQUEST_DECLINED, notification.getType());
        assertEquals("Exchange request declined", notification.getTitle());
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
        Notification notification = captureSavedNotifications().get(0);
        assertEquals(55L, notification.getUserId());
        assertEquals(NotificationType.EXCHANGE_REQUEST_CANCELLED, notification.getType());
        assertEquals("Exchange request cancelled", notification.getTitle());
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
        List<Notification> notifications = captureSavedNotifications();
        assertEquals(2, notifications.size());
        assertEquals(List.of(42L, 55L), notifications.stream().map(Notification::getUserId).toList());
        assertEquals(List.of(
                NotificationType.EXCHANGE_REQUEST_COMPLETED,
                NotificationType.EXCHANGE_REQUEST_COMPLETED
        ), notifications.stream().map(Notification::getType).toList());
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
        verify(notificationRepository, never()).saveAll(any());
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
        verify(notificationRepository, never()).saveAll(any());
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
        verify(notificationRepository, never()).saveAll(any());
    }

    @SuppressWarnings("unchecked")
    private List<Notification> captureSavedNotifications() {
        ArgumentCaptor<List<Notification>> captor = ArgumentCaptor.forClass(List.class);
        verify(notificationRepository).saveAll(captor.capture());
        return captor.getValue();
    }

    private ExchangeEventPayload sampleEvent(String eventType) {
        return ExchangeEventPayload.builder()
                .eventId(UUID.randomUUID())
                .eventType(eventType)
                .occurredAt(LocalDateTime.of(2026, 5, 18, 22, 30))
                .exchangeRequestId(101L)
                .requesterId(42L)
                .ownerId(55L)
                .requestedBookId(1001L)
                .offeredBookId(2002L)
                .status("PENDING")
                .build();
    }
}
