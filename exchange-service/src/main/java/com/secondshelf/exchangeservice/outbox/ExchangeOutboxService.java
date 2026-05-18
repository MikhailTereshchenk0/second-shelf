package com.secondshelf.exchangeservice.outbox;

import com.secondshelf.exchangeservice.entity.ExchangeRequest;
import com.secondshelf.exchangeservice.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ExchangeOutboxService {

    private final ExchangeOutboxEventFactory exchangeOutboxEventFactory;
    private final OutboxEventRepository outboxEventRepository;

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordExchangeEvent(ExchangeEventType eventType, ExchangeRequest exchangeRequest) {
        outboxEventRepository.save(exchangeOutboxEventFactory.create(eventType, exchangeRequest));
    }
}
