package com.secondshelf.notificationservice.messaging;

import com.secondshelf.notificationservice.entity.Notification;
import com.secondshelf.notificationservice.entity.NotificationStatus;
import com.secondshelf.notificationservice.entity.NotificationType;
import com.secondshelf.notificationservice.entity.ProcessedEvent;
import com.secondshelf.notificationservice.observability.NotificationAsyncMetrics;
import com.secondshelf.notificationservice.repository.NotificationRepository;
import com.secondshelf.notificationservice.repository.ProcessedEventRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;

import java.util.ArrayList;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.liquibase.enabled=false"
})
@Import(ExchangeEventNotificationServiceJpaTest.TestConfig.class)
class ExchangeEventNotificationServiceJpaTest {

    @Autowired
    private ExchangeEventNotificationService exchangeEventNotificationService;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private ProcessedEventRepository processedEventRepository;

    @Test
    void processShouldPersistNotificationAndProcessedEvent() {
        // arrange
        ExchangeEventPayload payload = sampleEvent(UUID.randomUUID(), "exchange.request.created");

        // act
        exchangeEventNotificationService.process(payload);

        // assert
        List<Notification> notifications = notificationRepository.findAll();
        Notification notification = notifications.get(0);
        ProcessedEvent processedEvent = processedEventRepository.findById(payload.getEventId()).orElseThrow();

        assertEquals(1, notifications.size());
        assertEquals(55L, notification.getUserId());
        assertEquals(NotificationType.EXCHANGE_REQUEST_CREATED, notification.getType());
        assertEquals("New exchange request", notification.getTitle());
        assertEquals(
                "alice wants to exchange \"Dune\" by Frank Herbert for your \"The Left Hand of Darkness\" by Ursula K. Le Guin. Message from requester: Can meet this weekend.",
                notification.getMessage()
        );
        assertEquals(NotificationStatus.UNREAD, notification.getStatus());
        assertEquals("EXCHANGE_REQUEST", notification.getRelatedEntityType());
        assertEquals("101", notification.getRelatedEntityId());
        assertEquals(payload.getOccurredAt(), notification.getCreatedAt());
        assertNull(notification.getReadAt());
        assertEquals(payload.getEventId(), processedEvent.getEventId());
        assertEquals("exchange.request.created", processedEvent.getEventType());
        assertNotNull(processedEvent.getProcessedAt());
    }

    @Test
    void processShouldRemainIdempotentForDuplicateEventId() {
        // arrange
        UUID eventId = UUID.randomUUID();
        ExchangeEventPayload payload = sampleEvent(eventId, "exchange.request.accepted");
        payload.setInitiatorUserId(55L);
        payload.setInitiatorUsername("owner");

        // act
        exchangeEventNotificationService.process(payload);
        exchangeEventNotificationService.process(payload);

        // assert
        assertEquals(1, notificationRepository.count());
        assertEquals(1, processedEventRepository.count());

        Notification notification = notificationRepository.findAll().get(0);
        assertEquals(42L, notification.getUserId());
        assertEquals(NotificationType.EXCHANGE_REQUEST_ACCEPTED, notification.getType());
        assertEquals("Your exchange request was accepted", notification.getTitle());
        assertEquals(
                "owner accepted your request to exchange \"Dune\" by Frank Herbert for \"The Left Hand of Darkness\" by Ursula K. Le Guin.",
                notification.getMessage()
        );
    }

    @Test
    void processShouldPersistSingleNotificationForCompletionConfirmedEvent() {
        // arrange
        ExchangeEventPayload payload = sampleEvent(UUID.randomUUID(), "exchange.request.completion_confirmed");
        payload.setCompletedByUserId(42L);
        payload.setStatus("COMPLETION_PENDING");

        // act
        exchangeEventNotificationService.process(payload);

        // assert
        List<Notification> notifications = notificationRepository.findAll();
        assertEquals(1, notifications.size());
        Notification notification = notifications.get(0);
        assertEquals(55L, notification.getUserId());
        assertEquals(NotificationType.EXCHANGE_REQUEST_COMPLETION_CONFIRMED, notification.getType());
        assertEquals("Your confirmation is needed", notification.getTitle());
        assertEquals(
                "alice confirmed the exchange of \"Dune\" by Frank Herbert and \"The Left Hand of Darkness\" by Ursula K. Le Guin. Confirm it from your side to complete the exchange.",
                notification.getMessage()
        );
    }

    @Test
    void processShouldPersistTwoUnreadNotificationsForCompletedEvent() {
        // arrange
        ExchangeEventPayload payload = sampleEvent(UUID.randomUUID(), "exchange.request.completed");

        // act
        exchangeEventNotificationService.process(payload);

        // assert
        List<Notification> notifications = new ArrayList<>(
                notificationRepository.findAllByUserId(42L, PageRequest.of(0, 10)).getContent()
        );
        notifications.addAll(notificationRepository.findAllByUserId(55L, PageRequest.of(0, 10)).getContent());

        assertEquals(2, notifications.size());
        assertEquals(
                List.of(42L, 55L),
                notifications.stream().map(Notification::getUserId).sorted().toList()
        );
        assertTrue(notifications.stream().allMatch(notification -> notification.getType() == NotificationType.EXCHANGE_REQUEST_COMPLETED));
        assertTrue(notifications.stream().allMatch(notification -> notification.getStatus() == NotificationStatus.UNREAD));
        assertTrue(notifications.stream().allMatch(notification -> notification.getReadAt() == null));
        assertTrue(notifications.stream().allMatch(notification -> "EXCHANGE_REQUEST".equals(notification.getRelatedEntityType())));
        assertTrue(notifications.stream().allMatch(notification -> "101".equals(notification.getRelatedEntityId())));
        assertTrue(notifications.stream().allMatch(notification ->
                "Your exchange of \"Dune\" by Frank Herbert and \"The Left Hand of Darkness\" by Ursula K. Le Guin is complete."
                        .equals(notification.getMessage())
        ));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestConfig {

        @Bean
        NotificationAsyncMetrics notificationAsyncMetrics() {
            return new NotificationAsyncMetrics(new SimpleMeterRegistry());
        }

        @Bean
        ExchangeEventNotificationService exchangeEventNotificationService(NotificationRepository notificationRepository,
                                                                         ProcessedEventRepository processedEventRepository,
                                                                         NotificationAsyncMetrics notificationAsyncMetrics) {
            return new ExchangeEventNotificationService(
                    notificationRepository,
                    processedEventRepository,
                    notificationAsyncMetrics
            );
        }
    }

    private ExchangeEventPayload sampleEvent(UUID eventId, String eventType) {
        return ExchangeEventPayload.builder()
                .schemaVersion(2)
                .eventId(eventId)
                .correlationId("corr-notification-jpa-123")
                .eventType(eventType)
                .occurredAt(LocalDateTime.of(2026, 5, 20, 10, 15))
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
                .status("COMPLETED")
                .build();
    }
}
