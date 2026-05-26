package com.secondshelf.exchangeservice.dto;

import com.secondshelf.exchangeservice.entity.OutboxEvent;
import com.secondshelf.exchangeservice.entity.OutboxEventStatus;
import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;
import java.util.UUID;

@Value
@Builder
public class OutboxEventSummaryResponse {

    Long id;
    UUID eventId;
    String aggregateType;
    String aggregateId;
    String eventType;
    OutboxEventStatus status;
    int attemptsCount;
    int manualRetryCount;
    LocalDateTime createdAt;
    LocalDateTime nextAttemptAt;
    LocalDateTime firstFailedAt;
    LocalDateTime failedAt;
    LocalDateTime manualRetriedAt;
    String errorCode;
    String lastError;

    public static OutboxEventSummaryResponse from(OutboxEvent event) {
        return OutboxEventSummaryResponse.builder()
                .id(event.getId())
                .eventId(event.getEventId())
                .aggregateType(event.getAggregateType())
                .aggregateId(event.getAggregateId())
                .eventType(event.getEventType())
                .status(event.getStatus())
                .attemptsCount(event.getAttemptsCount())
                .manualRetryCount(event.getManualRetryCount())
                .createdAt(event.getCreatedAt())
                .nextAttemptAt(event.getNextAttemptAt())
                .firstFailedAt(event.getFirstFailedAt())
                .failedAt(event.getFailedAt())
                .manualRetriedAt(event.getManualRetriedAt())
                .errorCode(event.getErrorCode())
                .lastError(event.getLastError())
                .build();
    }
}
