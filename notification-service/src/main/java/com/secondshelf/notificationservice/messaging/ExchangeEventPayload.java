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
    private Integer schemaVersion;
    private UUID eventId;
    private String correlationId;
    private String eventType;
    private LocalDateTime occurredAt;
    private Long exchangeRequestId;
    private Long initiatorUserId;
    private String initiatorUsername;
    private Long requesterId;
    private Long ownerId;
    private Long requestedBookId;
    private String requestedBookTitle;
    private String requestedBookAuthor;
    private Long offeredBookId;
    private String offeredBookTitle;
    private String offeredBookAuthor;
    private String requestMessage;
    private Long completedByUserId;
    private String status;
    private LocalDateTime ownerCompletionConfirmedAt;
    private LocalDateTime requesterCompletionConfirmedAt;
}
