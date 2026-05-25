package com.secondshelf.exchangeservice.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.secondshelf.exchangeservice.entity.ExchangeRequest;
import com.secondshelf.exchangeservice.entity.ExchangeStatus;
import com.secondshelf.exchangeservice.entity.OutboxEvent;
import com.secondshelf.exchangeservice.entity.OutboxEventStatus;
import com.secondshelf.exchangeservice.observability.CorrelationId;
import com.secondshelf.exchangeservice.observability.ExchangeAsyncMetrics;
import com.secondshelf.exchangeservice.repository.OutboxEventRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.liquibase.enabled=false"
})
@Import(ExchangeOutboxServiceJpaTest.TestConfig.class)
class ExchangeOutboxServiceJpaTest {

    @Autowired
    private ExchangeOutboxService exchangeOutboxService;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void recordExchangeEventShouldPersistPendingOutboxEventWithPayload() throws Exception {
        // arrange
        ExchangeRequest exchangeRequest = ExchangeRequest.builder()
                .id(101L)
                .requestedBookId(1001L)
                .offeredBookId(2002L)
                .ownerId(55L)
                .requesterId(42L)
                .status(ExchangeStatus.ACCEPTED)
                .message("please exchange")
                .build();

        // act
        try (CorrelationId.Scope ignored = CorrelationId.openScope("corr-outbox-jpa-123")) {
            exchangeOutboxService.recordExchangeEvent(ExchangeEventType.EXCHANGE_REQUEST_ACCEPTED, exchangeRequest);
        }
        outboxEventRepository.flush();

        // assert
        OutboxEvent savedEvent = outboxEventRepository.findAll().get(0);
        ExchangeEventPayload payload = objectMapper.readValue(savedEvent.getPayload(), ExchangeEventPayload.class);

        assertNotNull(savedEvent.getId());
        assertEquals(OutboxEventStatus.PENDING, savedEvent.getStatus());
        assertEquals(0, savedEvent.getAttemptsCount());
        assertNull(savedEvent.getFailedAt());
        assertNull(savedEvent.getLastError());
        assertEquals("EXCHANGE_REQUEST", savedEvent.getAggregateType());
        assertEquals("101", savedEvent.getAggregateId());
        assertEquals("exchange.request.accepted", savedEvent.getEventType());
        assertNotNull(savedEvent.getEventId());
        assertEquals(savedEvent.getEventId(), payload.getEventId());
        assertEquals("corr-outbox-jpa-123", payload.getCorrelationId());
        assertEquals("exchange.request.accepted", payload.getEventType());
        assertEquals(101L, payload.getExchangeRequestId());
        assertEquals(42L, payload.getRequesterId());
        assertEquals(55L, payload.getOwnerId());
        assertEquals(1001L, payload.getRequestedBookId());
        assertEquals(2002L, payload.getOfferedBookId());
        assertEquals(ExchangeStatus.ACCEPTED, payload.getStatus());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void recordExchangeEventShouldRequireActiveTransaction() {
        // arrange
        ExchangeRequest exchangeRequest = ExchangeRequest.builder()
                .id(202L)
                .requestedBookId(3003L)
                .ownerId(77L)
                .requesterId(88L)
                .status(ExchangeStatus.PENDING)
                .build();

        // act + assert
        assertThrows(
                IllegalTransactionStateException.class,
                () -> exchangeOutboxService.recordExchangeEvent(ExchangeEventType.EXCHANGE_REQUEST_CREATED, exchangeRequest)
        );
        assertEquals(0, outboxEventRepository.count());
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestConfig {

        @Bean
        ObjectMapper objectMapper() {
            return JsonMapper.builder().findAndAddModules().build();
        }

        @Bean
        ExchangeAsyncMetrics exchangeAsyncMetrics() {
            return new ExchangeAsyncMetrics(new SimpleMeterRegistry());
        }

        @Bean
        ExchangeOutboxEventFactory exchangeOutboxEventFactory(ObjectMapper objectMapper,
                                                              ExchangeAsyncMetrics exchangeAsyncMetrics) {
            return new ExchangeOutboxEventFactory(objectMapper, exchangeAsyncMetrics);
        }

        @Bean
        ExchangeOutboxService exchangeOutboxService(ExchangeOutboxEventFactory exchangeOutboxEventFactory,
                                                    OutboxEventRepository outboxEventRepository) {
            return new ExchangeOutboxService(exchangeOutboxEventFactory, outboxEventRepository);
        }
    }
}
