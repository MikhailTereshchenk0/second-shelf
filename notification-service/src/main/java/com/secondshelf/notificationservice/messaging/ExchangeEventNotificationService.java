package com.secondshelf.notificationservice.messaging;

import com.secondshelf.notificationservice.entity.Notification;
import com.secondshelf.notificationservice.entity.NotificationStatus;
import com.secondshelf.notificationservice.entity.NotificationType;
import com.secondshelf.notificationservice.entity.ProcessedEvent;
import com.secondshelf.notificationservice.exception.NotificationBadRequestException;
import com.secondshelf.notificationservice.observability.NotificationAsyncMetrics;
import com.secondshelf.notificationservice.repository.NotificationRepository;
import com.secondshelf.notificationservice.repository.ProcessedEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

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
    private final NotificationAsyncMetrics notificationAsyncMetrics;

    @Transactional
    public void process(ExchangeEventPayload eventPayload) {
        try {
            validate(eventPayload);
            ExchangeEventType eventType = parseEventType(eventPayload.getEventType());

            if (processedEventRepository.existsById(eventPayload.getEventId())) {
                log.info("Skipping duplicate exchange event eventId={}, eventType={}", eventPayload.getEventId(), eventPayload.getEventType());
                notificationAsyncMetrics.incrementIgnored(eventPayload.getEventType(), "duplicate");
                return;
            }

            if (!reserveProcessedEvent(eventPayload.getEventId(), eventPayload.getEventType())) {
                notificationAsyncMetrics.incrementIgnored(eventPayload.getEventType(), "duplicate");
                return;
            }

            List<Notification> notifications = buildNotifications(eventPayload, eventType);
            notificationRepository.saveAll(notifications);
            notificationAsyncMetrics.incrementProcessed(eventPayload.getEventType());

            log.info(
                    "Created {} notification(s) for exchange event eventId={}, eventType={}",
                    notifications.size(),
                    eventPayload.getEventId(),
                    eventPayload.getEventType()
            );
        } catch (NotificationBadRequestException ex) {
            notificationAsyncMetrics.incrementIgnored(resolveEventType(eventPayload), "invalid");
            throw ex;
        }
    }

    private boolean reserveProcessedEvent(UUID eventId, String eventType) {
        try {
            processedEventRepository.saveAndFlush(ProcessedEvent.builder()
                    .eventId(eventId)
                    .eventType(eventType)
                    .build());
            return true;
        } catch (DataIntegrityViolationException ex) {
            log.info("Skipping duplicate exchange event eventId={}, eventType={}", eventId, eventType);
            return false;
        }
    }

    private List<Notification> buildNotifications(ExchangeEventPayload eventPayload, ExchangeEventType eventType) {
        return switch (eventType) {
            case EXCHANGE_REQUEST_CREATED -> List.of(buildNotification(
                    eventPayload.getOwnerId(),
                    NotificationType.EXCHANGE_REQUEST_CREATED,
                    "New exchange request",
                    buildCreatedMessage(eventPayload),
                    eventPayload
            ));
            case EXCHANGE_REQUEST_ACCEPTED -> List.of(buildNotification(
                    eventPayload.getRequesterId(),
                    NotificationType.EXCHANGE_REQUEST_ACCEPTED,
                    "Your exchange request was accepted",
                    buildAcceptedMessage(eventPayload),
                    eventPayload
            ));
            case EXCHANGE_REQUEST_DECLINED -> List.of(buildNotification(
                    eventPayload.getRequesterId(),
                    NotificationType.EXCHANGE_REQUEST_DECLINED,
                    "Your exchange request was declined",
                    buildDeclinedMessage(eventPayload),
                    eventPayload
            ));
            case EXCHANGE_REQUEST_CANCELLED -> List.of(buildNotification(
                    eventPayload.getOwnerId(),
                    NotificationType.EXCHANGE_REQUEST_CANCELLED,
                    "Exchange request cancelled",
                    buildCancelledMessage(eventPayload),
                    eventPayload
            ));
            case EXCHANGE_REQUEST_COMPLETION_CONFIRMED -> List.of(buildNotification(
                    resolveCompletionConfirmationRecipient(eventPayload),
                    NotificationType.EXCHANGE_REQUEST_COMPLETION_CONFIRMED,
                    "Your confirmation is needed",
                    buildCompletionConfirmedMessage(eventPayload),
                    eventPayload
            ));
            case EXCHANGE_REQUEST_COMPLETED -> List.of(
                    buildNotification(
                            eventPayload.getRequesterId(),
                            NotificationType.EXCHANGE_REQUEST_COMPLETED,
                            "Exchange completed",
                            buildCompletedMessage(eventPayload),
                            eventPayload
                    ),
                    buildNotification(
                            eventPayload.getOwnerId(),
                            NotificationType.EXCHANGE_REQUEST_COMPLETED,
                            "Exchange completed",
                            buildCompletedMessage(eventPayload),
                            eventPayload
                    )
            );
        };
    }

    private String buildCreatedMessage(ExchangeEventPayload eventPayload) {
        String message = resolveActorLabel(eventPayload, "Another reader")
                + " wants to exchange "
                + formatBook(eventPayload.getOfferedBookTitle(), eventPayload.getOfferedBookAuthor(), eventPayload.getOfferedBookId())
                + " for your "
                + formatBook(eventPayload.getRequestedBookTitle(), eventPayload.getRequestedBookAuthor(), eventPayload.getRequestedBookId())
                + ".";
        return appendRequestMessage(message, eventPayload.getRequestMessage());
    }

    private String buildAcceptedMessage(ExchangeEventPayload eventPayload) {
        return resolveActorLabel(eventPayload, "The book owner")
                + " accepted your request to exchange "
                + formatBook(eventPayload.getOfferedBookTitle(), eventPayload.getOfferedBookAuthor(), eventPayload.getOfferedBookId())
                + " for "
                + formatBook(eventPayload.getRequestedBookTitle(), eventPayload.getRequestedBookAuthor(), eventPayload.getRequestedBookId())
                + ".";
    }

    private String buildDeclinedMessage(ExchangeEventPayload eventPayload) {
        return resolveActorLabel(eventPayload, "The book owner")
                + " declined your request to exchange "
                + formatBook(eventPayload.getOfferedBookTitle(), eventPayload.getOfferedBookAuthor(), eventPayload.getOfferedBookId())
                + " for "
                + formatBook(eventPayload.getRequestedBookTitle(), eventPayload.getRequestedBookAuthor(), eventPayload.getRequestedBookId())
                + ".";
    }

    private String buildCancelledMessage(ExchangeEventPayload eventPayload) {
        return resolveActorLabel(eventPayload, "Another reader")
                + " cancelled the request to exchange "
                + formatBook(eventPayload.getOfferedBookTitle(), eventPayload.getOfferedBookAuthor(), eventPayload.getOfferedBookId())
                + " for your "
                + formatBook(eventPayload.getRequestedBookTitle(), eventPayload.getRequestedBookAuthor(), eventPayload.getRequestedBookId())
                + ".";
    }

    private String buildCompletionConfirmedMessage(ExchangeEventPayload eventPayload) {
        return resolveActorLabel(eventPayload, "The other participant")
                + " confirmed the exchange of "
                + formatBook(eventPayload.getOfferedBookTitle(), eventPayload.getOfferedBookAuthor(), eventPayload.getOfferedBookId())
                + " and "
                + formatBook(eventPayload.getRequestedBookTitle(), eventPayload.getRequestedBookAuthor(), eventPayload.getRequestedBookId())
                + ". Confirm it from your side to complete the exchange.";
    }

    private String buildCompletedMessage(ExchangeEventPayload eventPayload) {
        return "Your exchange of "
                + formatBook(eventPayload.getOfferedBookTitle(), eventPayload.getOfferedBookAuthor(), eventPayload.getOfferedBookId())
                + " and "
                + formatBook(eventPayload.getRequestedBookTitle(), eventPayload.getRequestedBookAuthor(), eventPayload.getRequestedBookId())
                + " is complete.";
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

    private Long resolveCompletionConfirmationRecipient(ExchangeEventPayload eventPayload) {
        Long completedByUserId = eventPayload.getCompletedByUserId() != null
                ? eventPayload.getCompletedByUserId()
                : eventPayload.getInitiatorUserId();

        if (completedByUserId == null) {
            throw new NotificationBadRequestException(
                    "INVALID_EXCHANGE_EVENT",
                    "Completion confirmation event must contain completedByUserId."
            );
        }
        if (completedByUserId.equals(eventPayload.getRequesterId())) {
            return eventPayload.getOwnerId();
        }
        if (completedByUserId.equals(eventPayload.getOwnerId())) {
            return eventPayload.getRequesterId();
        }

        throw new NotificationBadRequestException(
                "INVALID_EXCHANGE_EVENT",
                "completedByUserId must belong to one of exchange participants."
        );
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

    private String resolveEventType(ExchangeEventPayload eventPayload) {
        return eventPayload != null ? eventPayload.getEventType() : "unknown";
    }

    private String resolveActorLabel(ExchangeEventPayload eventPayload, String fallbackLabel) {
        if (StringUtils.hasText(eventPayload.getInitiatorUsername())) {
            return eventPayload.getInitiatorUsername().trim();
        }
        if (eventPayload.getInitiatorUserId() != null) {
            return "User #" + eventPayload.getInitiatorUserId();
        }
        return fallbackLabel;
    }

    private String formatBook(String title, String author, Long bookId) {
        if (StringUtils.hasText(title) && StringUtils.hasText(author)) {
            return "\"" + title.trim() + "\" by " + author.trim();
        }
        if (StringUtils.hasText(title)) {
            return "\"" + title.trim() + "\"";
        }
        return "book #" + bookId;
    }

    private String appendRequestMessage(String message, String requestMessage) {
        if (!StringUtils.hasText(requestMessage)) {
            return message;
        }
        return message + " Message from requester: " + requestMessage.trim();
    }
}
