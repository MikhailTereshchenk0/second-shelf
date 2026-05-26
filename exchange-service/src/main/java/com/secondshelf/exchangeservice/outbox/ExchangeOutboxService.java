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

    private static final String EXCHANGE_REQUEST_AGGREGATE_TYPE = "EXCHANGE_REQUEST";

    private final ExchangeOutboxEventFactory exchangeOutboxEventFactory;
    private final OutboxEventRepository outboxEventRepository;

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordExchangeEvent(ExchangeEventType eventType, ExchangeRequest exchangeRequest) {
        recordExchangeEvent(eventType, exchangeRequest, null);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordExchangeEvent(ExchangeEventType eventType,
                                    ExchangeRequest exchangeRequest,
                                    ExchangeEventContext eventContext) {
        outboxEventRepository.save(exchangeOutboxEventFactory.create(eventType, exchangeRequest, eventContext));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordExchangeEventIfAbsent(ExchangeEventType eventType,
                                            ExchangeRequest exchangeRequest,
                                            ExchangeEventContext eventContext) {
        if (!hasRecordedExchangeEvent(eventType, exchangeRequest)) {
            recordExchangeEvent(eventType, exchangeRequest, eventContext);
        }
    }

    @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
    public boolean hasRecordedExchangeEvent(ExchangeEventType eventType, ExchangeRequest exchangeRequest) {
        return outboxEventRepository.existsByAggregateTypeAndAggregateIdAndEventType(
                EXCHANGE_REQUEST_AGGREGATE_TYPE,
                String.valueOf(exchangeRequest.getId()),
                eventType.getValue()
        );
    }
}
