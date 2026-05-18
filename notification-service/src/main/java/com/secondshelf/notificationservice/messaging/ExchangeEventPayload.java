package com.secondshelf.notificationservice.messaging;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ExchangeEventPayload {
    private UUID eventId;
    private String eventType;
    private LocalDateTime occurredAt;
    private Long exchangeRequestId;
    private Long requesterId;
    private Long ownerId;
    private Long requestedBookId;
    private Long offeredBookId;
    private String status;
}
