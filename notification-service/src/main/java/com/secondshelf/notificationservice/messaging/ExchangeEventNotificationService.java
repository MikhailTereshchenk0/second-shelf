package com.secondshelf.notificationservice.messaging;

import com.secondshelf.notificationservice.entity.NotificationChannel;
import com.secondshelf.notificationservice.entity.NotificationType;
import com.secondshelf.notificationservice.entity.ProcessedEvent;
import com.secondshelf.notificationservice.exception.NotificationBadRequestException;
import com.secondshelf.notificationservice.observability.NotificationAsyncMetrics;
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

    private final ProcessedEventRepository processedEventRepository;
    private final NotificationChannelDispatcher notificationChannelDispatcher;
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

            List<NotificationDeliveryCommand> commands = buildNotificationCommands(eventPayload, eventType);
            commands.forEach(notificationChannelDispatcher::deliver);
            notificationAsyncMetrics.incrementNotificationsCreated(eventPayload.getEventType(), commands.size());
            notificationAsyncMetrics.incrementProcessed(eventPayload.getEventType());

            log.info(
                    "Created {} notification(s) for exchange event eventId={}, eventType={}",
                    commands.size(),
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

    private List<NotificationDeliveryCommand> buildNotificationCommands(ExchangeEventPayload eventPayload, ExchangeEventType eventType) {
        return switch (eventType) {
            case EXCHANGE_REQUEST_CREATED -> List.of(buildNotificationCommand(
                    eventPayload.getOwnerId(),
                    NotificationType.EXCHANGE_REQUEST_CREATED,
                    "New exchange request",
                    buildCreatedMessage(eventPayload),
                    eventPayload
            ));
            case EXCHANGE_REQUEST_OWNER_OFFERED -> List.of(buildNotificationCommand(
                    eventPayload.getRequesterId(),
                    NotificationType.EXCHANGE_REQUEST_OWNER_OFFERED,
                    "New exchange offer",
                    buildOwnerOfferedMessage(eventPayload),
                    eventPayload
            ));
            case EXCHANGE_REQUEST_ACCEPTED -> List.of(buildNotificationCommand(
                    eventPayload.getOwnerId(),
                    NotificationType.EXCHANGE_REQUEST_ACCEPTED,
                    "Your exchange offer was accepted",
                    buildAcceptedMessage(eventPayload),
                    eventPayload
            ));
            case EXCHANGE_REQUEST_DECLINED -> List.of(buildNotificationCommand(
                    eventPayload.getRequesterId(),
                    NotificationType.EXCHANGE_REQUEST_DECLINED,
                    "Your exchange request was declined",
                    buildDeclinedMessage(eventPayload),
                    eventPayload
            ));
            case EXCHANGE_REQUEST_CANCELLED -> List.of(buildNotificationCommand(
                    eventPayload.getOwnerId(),
                    NotificationType.EXCHANGE_REQUEST_CANCELLED,
                    "Exchange request cancelled",
                    buildCancelledMessage(eventPayload),
                    eventPayload
            ));
            case EXCHANGE_REQUEST_COMPLETION_CONFIRMED -> List.of(buildNotificationCommand(
                    resolveCompletionConfirmationRecipient(eventPayload),
                    NotificationType.EXCHANGE_REQUEST_COMPLETION_CONFIRMED,
                    "Your confirmation is needed",
                    buildCompletionConfirmedMessage(eventPayload),
                    eventPayload
            ));
            case EXCHANGE_REQUEST_COMPLETED -> List.of(
                    buildNotificationCommand(
                            eventPayload.getRequesterId(),
                            NotificationType.EXCHANGE_REQUEST_COMPLETED,
                            "Exchange completed",
                            buildCompletedMessage(eventPayload),
                            eventPayload
                    ),
                    buildNotificationCommand(
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
                + " wants your "
                + formatBook(eventPayload.getRequestedBookTitle(), eventPayload.getRequestedBookAuthor(), eventPayload.getRequestedBookId())
                + ".";
        return appendRequestMessage(message, eventPayload.getRequestMessage());
    }

    private String buildOwnerOfferedMessage(ExchangeEventPayload eventPayload) {
        return resolveActorLabel(eventPayload, "The book owner")
                + " offered to exchange "
                + formatBook(eventPayload.getRequestedBookTitle(), eventPayload.getRequestedBookAuthor(), eventPayload.getRequestedBookId())
                + " for your "
                + formatBook(eventPayload.getOfferedBookTitle(), eventPayload.getOfferedBookAuthor(), eventPayload.getOfferedBookId())
                + ". Confirm or decline the offer.";
    }

    private String buildAcceptedMessage(ExchangeEventPayload eventPayload) {
        return resolveActorLabel(eventPayload, "The requester")
                + " accepted the offer to exchange "
                + formatBook(eventPayload.getRequestedBookTitle(), eventPayload.getRequestedBookAuthor(), eventPayload.getRequestedBookId())
                + " for "
                + formatBook(eventPayload.getOfferedBookTitle(), eventPayload.getOfferedBookAuthor(), eventPayload.getOfferedBookId())
                + ".";
    }

    private String buildDeclinedMessage(ExchangeEventPayload eventPayload) {
        return resolveActorLabel(eventPayload, "The book owner")
                + " declined your request for "
                + formatBook(eventPayload.getRequestedBookTitle(), eventPayload.getRequestedBookAuthor(), eventPayload.getRequestedBookId())
                + ".";
    }

    private String buildCancelledMessage(ExchangeEventPayload eventPayload) {
        return resolveActorLabel(eventPayload, "Another reader")
                + " cancelled the request for your "
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

    private NotificationDeliveryCommand buildNotificationCommand(Long userId,
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

        return NotificationDeliveryCommand.builder()
                .channel(NotificationChannel.IN_APP)
                .userId(userId)
                .type(type)
                .title(title)
                .message(message)
                .relatedEntityType(RELATED_ENTITY_TYPE)
                .relatedEntityId(String.valueOf(eventPayload.getExchangeRequestId()))
                .occurredAt(resolveCreatedAt(eventPayload))
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
        if (eventPayload.getOfferedBookId() == null && requiresOfferedBook(eventPayload.getEventType())) {
            throw new NotificationBadRequestException(
                    "INVALID_EXCHANGE_EVENT",
                    "Offered book id must not be null."
            );
        }
    }

    private boolean requiresOfferedBook(String eventType) {
        return ExchangeEventType.EXCHANGE_REQUEST_OWNER_OFFERED.getValue().equals(eventType)
                || ExchangeEventType.EXCHANGE_REQUEST_ACCEPTED.getValue().equals(eventType)
                || ExchangeEventType.EXCHANGE_REQUEST_COMPLETION_CONFIRMED.getValue().equals(eventType)
                || ExchangeEventType.EXCHANGE_REQUEST_COMPLETED.getValue().equals(eventType);
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
