package com.secondshelf.exchangeservice.outbox;

import com.secondshelf.exchangeservice.entity.ExchangeStatus;
import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;
import java.util.UUID;

@Value
@Builder
public class ExchangeEventPayload {
    UUID eventId;
    String eventType;
    LocalDateTime occurredAt;
    Long exchangeRequestId;
    Long requesterId;
    Long ownerId;
    Long requestedBookId;
    Long offeredBookId;
    ExchangeStatus status;
}
