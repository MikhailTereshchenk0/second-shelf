package com.secondshelf.notificationservice.messaging;

import com.secondshelf.notificationservice.entity.Notification;
import com.secondshelf.notificationservice.entity.NotificationStatus;
import com.secondshelf.notificationservice.entity.NotificationType;
import com.secondshelf.notificationservice.entity.ProcessedEvent;
import com.secondshelf.notificationservice.exception.NotificationBadRequestException;
import com.secondshelf.notificationservice.repository.NotificationRepository;
import com.secondshelf.notificationservice.repository.ProcessedEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExchangeEventNotificationService {

    private static final String RELATED_ENTITY_TYPE = "EXCHANGE_REQUEST";

    private final NotificationRepository notificationRepository;
    private final ProcessedEventRepository processedEventRepository;

    @Transactional
    public void process(ExchangeEventPayload eventPayload) {
        validate(eventPayload);
        ExchangeEventType eventType = parseEventType(eventPayload.getEventType());

        if (processedEventRepository.existsById(eventPayload.getEventId())) {
            log.info("Skipping already processed exchange event eventId={}", eventPayload.getEventId());
            return;
        }

        if (!reserveProcessedEvent(eventPayload.getEventId(), eventPayload.getEventType())) {
            return;
        }

        List<Notification> notifications = buildNotifications(eventPayload, eventType);
        notificationRepository.saveAll(notifications);

        log.info(
                "Persisted {} notification(s) for exchange event eventId={}, eventType={}",
                notifications.size(),
                eventPayload.getEventId(),
                eventPayload.getEventType()
        );
    }

    private boolean reserveProcessedEvent(UUID eventId, String eventType) {
        try {
            processedEventRepository.saveAndFlush(ProcessedEvent.builder()
                    .eventId(eventId)
                    .eventType(eventType)
                    .build());
            return true;
        } catch (DataIntegrityViolationException ex) {
            log.info("Skipping duplicate exchange event eventId={}", eventId);
            return false;
        }
    }

    private List<Notification> buildNotifications(ExchangeEventPayload eventPayload, ExchangeEventType eventType) {
        return switch (eventType) {
            case EXCHANGE_REQUEST_CREATED -> List.of(buildNotification(
                    eventPayload.getOwnerId(),
                    NotificationType.EXCHANGE_REQUEST_CREATED,
                    "New exchange request",
                    "User #" + eventPayload.getRequesterId()
                            + " offered book #" + eventPayload.getOfferedBookId()
                            + " for your book #" + eventPayload.getRequestedBookId() + ".",
                    eventPayload
            ));
            case EXCHANGE_REQUEST_ACCEPTED -> List.of(buildNotification(
                    eventPayload.getRequesterId(),
                    NotificationType.EXCHANGE_REQUEST_ACCEPTED,
                    "Exchange request accepted",
                    "User #" + eventPayload.getOwnerId()
                            + " accepted your exchange request for book #" + eventPayload.getRequestedBookId() + ".",
                    eventPayload
            ));
            case EXCHANGE_REQUEST_DECLINED -> List.of(buildNotification(
                    eventPayload.getRequesterId(),
                    NotificationType.EXCHANGE_REQUEST_DECLINED,
                    "Exchange request declined",
                    "User #" + eventPayload.getOwnerId()
                            + " declined your exchange request for book #" + eventPayload.getRequestedBookId() + ".",
                    eventPayload
            ));
            case EXCHANGE_REQUEST_CANCELLED -> List.of(buildNotification(
                    eventPayload.getOwnerId(),
                    NotificationType.EXCHANGE_REQUEST_CANCELLED,
                    "Exchange request cancelled",
                    "User #" + eventPayload.getRequesterId()
                            + " cancelled the exchange request for your book #" + eventPayload.getRequestedBookId() + ".",
                    eventPayload
            ));
            case EXCHANGE_REQUEST_COMPLETED -> List.of(
                    buildNotification(
                            eventPayload.getRequesterId(),
                            NotificationType.EXCHANGE_REQUEST_COMPLETED,
                            "Exchange completed",
                            "Exchange request #" + eventPayload.getExchangeRequestId()
                                    + " with user #" + eventPayload.getOwnerId()
                                    + " was marked as completed.",
                            eventPayload
                    ),
                    buildNotification(
                            eventPayload.getOwnerId(),
                            NotificationType.EXCHANGE_REQUEST_COMPLETED,
                            "Exchange completed",
                            "Exchange request #" + eventPayload.getExchangeRequestId()
                                    + " with user #" + eventPayload.getRequesterId()
                                    + " was marked as completed.",
                            eventPayload
                    )
            );
        };
    }

    private Notification buildNotification(Long userId,
                                           NotificationType type,
                                           String title,
                                           String message,
                                           ExchangeEventPayload eventPayload) {
        if (userId == null) {
            throw new NotificationBadRequestException(
                    "INVALID_EXCHANGE_EVENT_RECIPIENT",
                    "Exchange event recipient is missing."
            );
        }

        return Notification.builder()
                .userId(userId)
                .type(type)
                .title(title)
                .message(message)
                .status(NotificationStatus.UNREAD)
                .relatedEntityType(RELATED_ENTITY_TYPE)
                .relatedEntityId(String.valueOf(eventPayload.getExchangeRequestId()))
                .createdAt(resolveCreatedAt(eventPayload))
                .build();
    }

    private LocalDateTime resolveCreatedAt(ExchangeEventPayload eventPayload) {
        return eventPayload.getOccurredAt() != null ? eventPayload.getOccurredAt() : LocalDateTime.now();
    }

    private ExchangeEventType parseEventType(String eventType) {
        try {
            return ExchangeEventType.fromValue(eventType);
        } catch (IllegalArgumentException ex) {
            throw new NotificationBadRequestException(
                    "UNSUPPORTED_EXCHANGE_EVENT_TYPE",
                    ex.getMessage()
            );
        }
    }

    private void validate(ExchangeEventPayload eventPayload) {
        if (eventPayload == null) {
            throw new NotificationBadRequestException(
                    "INVALID_EXCHANGE_EVENT",
                    "Exchange event payload must not be null."
            );
        }
        if (eventPayload.getEventId() == null) {
            throw new NotificationBadRequestException(
                    "INVALID_EXCHANGE_EVENT",
                    "Exchange event id must not be null."
            );
        }
        if (eventPayload.getEventType() == null || eventPayload.getEventType().isBlank()) {
            throw new NotificationBadRequestException(
                    "INVALID_EXCHANGE_EVENT",
                    "Exchange event type must not be blank."
            );
        }
        if (eventPayload.getExchangeRequestId() == null) {
            throw new NotificationBadRequestException(
                    "INVALID_EXCHANGE_EVENT",
                    "Exchange request id must not be null."
            );
        }
        if (eventPayload.getRequesterId() == null) {
            throw new NotificationBadRequestException(
                    "INVALID_EXCHANGE_EVENT",
                    "Exchange requester id must not be null."
            );
        }
        if (eventPayload.getOwnerId() == null) {
            throw new NotificationBadRequestException(
                    "INVALID_EXCHANGE_EVENT",
                    "Exchange owner id must not be null."
            );
        }
        if (eventPayload.getRequestedBookId() == null) {
            throw new NotificationBadRequestException(
                    "INVALID_EXCHANGE_EVENT",
                    "Requested book id must not be null."
            );
        }
        if (eventPayload.getOfferedBookId() == null) {
            throw new NotificationBadRequestException(
                    "INVALID_EXCHANGE_EVENT",
                    "Offered book id must not be null."
            );
        }
    }
}
