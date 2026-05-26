package com.secondshelf.exchangeservice.outbox;

import com.secondshelf.exchangeservice.entity.ExchangeStatus;
import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;
import java.util.UUID;

@Value
@Builder
public class ExchangeEventPayload {
    Integer schemaVersion;
    UUID eventId;
    String correlationId;
    String eventType;
    LocalDateTime occurredAt;
    Long exchangeRequestId;
    Long initiatorUserId;
    String initiatorUsername;
    Long requesterId;
    Long ownerId;
    Long requestedBookId;
    String requestedBookTitle;
    String requestedBookAuthor;
    Long offeredBookId;
    String offeredBookTitle;
    String offeredBookAuthor;
    String requestMessage;
    Long completedByUserId;
    ExchangeStatus status;
    LocalDateTime ownerCompletionConfirmedAt;
    LocalDateTime requesterCompletionConfirmedAt;
    String repairReason;
    LocalDateTime repairRequiredAt;
    Integer repairAttempts;
    LocalDateTime lastRepairAttemptAt;
}
