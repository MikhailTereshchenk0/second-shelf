package com.secondshelf.exchangeservice.service;

import com.secondshelf.exchangeservice.dto.OutboxEventSummaryResponse;
import com.secondshelf.exchangeservice.dto.OutboxRetryResponse;
import com.secondshelf.exchangeservice.entity.OutboxEvent;
import com.secondshelf.exchangeservice.entity.OutboxEventStatus;
import com.secondshelf.exchangeservice.exception.ExchangeConflictException;
import com.secondshelf.exchangeservice.exception.ExchangeNotFoundException;
import com.secondshelf.exchangeservice.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ExchangeOutboxAdminService {

    private final OutboxEventRepository outboxEventRepository;

    @Transactional(readOnly = true)
    public List<OutboxEventSummaryResponse> findTerminalFailedEvents() {
        return outboxEventRepository.findTop100ByStatusOrderByFailedAtDescCreatedAtDesc(OutboxEventStatus.TERMINAL_FAILED)
                .stream()
                .map(OutboxEventSummaryResponse::from)
                .toList();
    }

    @Transactional
    public OutboxRetryResponse retryTerminalFailedEvent(UUID eventId) {
        OutboxEvent event = outboxEventRepository.findForUpdateByEventId(eventId)
                .orElseThrow(() -> new ExchangeNotFoundException(
                        "OUTBOX_EVENT_NOT_FOUND",
                        "Outbox event not found."
                ));

        if (event.getStatus() != OutboxEventStatus.TERMINAL_FAILED) {
            throw new ExchangeConflictException(
                    "OUTBOX_EVENT_NOT_TERMINAL_FAILED",
                    "Only terminally failed outbox events can be retried manually."
            );
        }

        event.requeueTerminalFailure(LocalDateTime.now());
        return OutboxRetryResponse.requeued(outboxEventRepository.save(event));
    }
}
