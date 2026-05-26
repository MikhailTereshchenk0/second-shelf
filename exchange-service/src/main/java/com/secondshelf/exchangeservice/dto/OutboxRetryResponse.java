package com.secondshelf.exchangeservice.dto;

import com.secondshelf.exchangeservice.entity.OutboxEvent;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class OutboxRetryResponse {

    OutboxEventSummaryResponse event;
    String message;

    public static OutboxRetryResponse requeued(OutboxEvent event) {
        return OutboxRetryResponse.builder()
                .event(OutboxEventSummaryResponse.from(event))
                .message("Outbox event was re-queued for the scheduled publisher.")
                .build();
    }
}
