package com.secondshelf.exchangeservice.outbox;

import com.secondshelf.exchangeservice.entity.ExchangeStatus;
import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;

@Value
@Builder
public class ExchangeEventPayload {
    Long exchangeId;
    Long requestedBookId;
    Long offeredBookId;
    Long ownerId;
    Long requesterId;
    ExchangeStatus status;
    LocalDateTime occurredAt;
}
