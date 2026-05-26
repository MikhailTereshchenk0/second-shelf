package com.secondshelf.exchangeservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.secondshelf.exchangeservice.client.BookServiceClient;
import com.secondshelf.exchangeservice.client.dto.BookDto;
import com.secondshelf.exchangeservice.dto.CreateExchangeRequest;
import com.secondshelf.exchangeservice.entity.ExchangeRequest;
import com.secondshelf.exchangeservice.entity.ExchangeStatus;
import com.secondshelf.exchangeservice.entity.OutboxEvent;
import com.secondshelf.exchangeservice.entity.OutboxEventStatus;
import com.secondshelf.exchangeservice.observability.CorrelationId;
import com.secondshelf.exchangeservice.observability.ExchangeAsyncMetrics;
import com.secondshelf.exchangeservice.outbox.ExchangeEventPayload;
import com.secondshelf.exchangeservice.outbox.ExchangeOutboxEventFactory;
import com.secondshelf.exchangeservice.outbox.ExchangeOutboxService;
import com.secondshelf.exchangeservice.repository.ExchangeRepository;
import com.secondshelf.exchangeservice.repository.OutboxEventRepository;
import com.secondshelf.exchangeservice.security.UserPrincipal;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.liquibase.enabled=false"
})
@Import(ExchangeServiceJpaTest.TestConfig.class)
class ExchangeServiceJpaTest {

    @Autowired
    private ExchangeService exchangeService;

    @Autowired
    private ExchangeRepository exchangeRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TestEntityManager entityManager;

    @MockitoBean
    private BookServiceClient bookServiceClient;

    @Test
    void createShouldPersistExchangeAndCreatedOutboxEventInSameTransaction() throws Exception {
        CreateExchangeRequest createRequest = new CreateExchangeRequest();
        createRequest.setRequestedBookId(1001L);
        createRequest.setOfferedBookId(2002L);
        createRequest.setMessage("Can meet near the station.");

        when(bookServiceClient.getBook(1001L)).thenReturn(book(1001L, 55L, "The Left Hand of Darkness", "Ursula K. Le Guin"));
        when(bookServiceClient.getBook(2002L)).thenReturn(book(2002L, 42L, "Dune", "Frank Herbert"));

        try (CorrelationId.Scope ignored = CorrelationId.openScope("corr-create-jpa-123")) {
            exchangeService.create(createRequest, new UserPrincipal(42L, "alice"));
        }
        entityManager.flush();
        entityManager.clear();

        List<ExchangeRequest> exchanges = exchangeRepository.findAll();
        List<OutboxEvent> outboxEvents = outboxEventRepository.findAll();

        assertEquals(1, exchanges.size());
        assertEquals(1, outboxEvents.size());

        ExchangeRequest persistedRequest = exchanges.get(0);
        OutboxEvent outboxEvent = outboxEvents.get(0);
        ExchangeEventPayload payload = objectMapper.readValue(outboxEvent.getPayload(), ExchangeEventPayload.class);

        assertEquals(ExchangeStatus.PENDING, persistedRequest.getStatus());
        assertEquals("The Left Hand of Darkness", persistedRequest.getRequestedBookTitle());
        assertEquals("Dune", persistedRequest.getOfferedBookTitle());
        assertEquals("exchange.request.created", outboxEvent.getEventType());
        assertEquals(String.valueOf(persistedRequest.getId()), outboxEvent.getAggregateId());
        assertEquals(persistedRequest.getId(), payload.getExchangeRequestId());
        assertEquals("corr-create-jpa-123", payload.getCorrelationId());
        assertEquals(42L, payload.getInitiatorUserId());
    }

    @Test
    void acceptShouldReserveBooksAutoDeclineConflictsAndPersistOutboxEvents() throws Exception {
        ExchangeRequest target = exchangeRepository.saveAndFlush(acceptedCandidate());
        ExchangeRequest requestedBookConflict = exchangeRepository.saveAndFlush(conflictingPendingRequest(1001L, 3003L, 77L, 55L));
        ExchangeRequest offeredBookConflict = exchangeRepository.saveAndFlush(conflictingPendingRequest(4004L, 2002L, 88L, 66L));

        when(bookServiceClient.reserve(1001L)).thenReturn(new BookDto());
        when(bookServiceClient.reserve(2002L)).thenReturn(new BookDto());

        try (CorrelationId.Scope ignored = CorrelationId.openScope("corr-accept-jpa-123")) {
            exchangeService.accept(target.getId(), new UserPrincipal(55L, "owner"));
        }
        entityManager.flush();
        entityManager.clear();

        ExchangeRequest persistedTarget = exchangeRepository.findById(target.getId()).orElseThrow();
        ExchangeRequest persistedRequestedConflict = exchangeRepository.findById(requestedBookConflict.getId()).orElseThrow();
        ExchangeRequest persistedOfferedConflict = exchangeRepository.findById(offeredBookConflict.getId()).orElseThrow();
        List<OutboxEvent> outboxEvents = outboxEventRepository.findAll();

        assertEquals(ExchangeStatus.ACCEPTED, persistedTarget.getStatus());
        assertEquals("owner", persistedTarget.getOwnerUsernameSnapshot());
        assertEquals(ExchangeStatus.DECLINED, persistedRequestedConflict.getStatus());
        assertEquals(ExchangeStatus.DECLINED, persistedOfferedConflict.getStatus());

        assertEquals(3, outboxEvents.size());
        assertEquals(1, countEvents(outboxEvents, "exchange.request.accepted"));
        assertEquals(2, countEvents(outboxEvents, "exchange.request.declined"));
        for (OutboxEvent outboxEvent : outboxEvents) {
            ExchangeEventPayload payload = objectMapper.readValue(outboxEvent.getPayload(), ExchangeEventPayload.class);
            assertEquals("corr-accept-jpa-123", payload.getCorrelationId());
            assertEquals(55L, payload.getInitiatorUserId());
            assertEquals("owner", payload.getInitiatorUsername());
        }

        verify(bookServiceClient).reserve(1001L);
        verify(bookServiceClient).reserve(2002L);
    }

    @Test
    void cancelAcceptedShouldReleaseBothBooksAndPersistCancelledOutboxEvent() throws Exception {
        ExchangeRequest request = exchangeRepository.saveAndFlush(acceptedExchangeRequest());

        when(bookServiceClient.makeAvailable(1001L)).thenReturn(new BookDto());
        when(bookServiceClient.makeAvailable(2002L)).thenReturn(new BookDto());

        try (CorrelationId.Scope ignored = CorrelationId.openScope("corr-cancel-jpa-123")) {
            exchangeService.cancel(request.getId(), new UserPrincipal(42L, "alice"));
        }
        entityManager.flush();
        entityManager.clear();

        ExchangeRequest persistedRequest = exchangeRepository.findById(request.getId()).orElseThrow();
        OutboxEvent outboxEvent = singleOutboxEvent();
        ExchangeEventPayload payload = objectMapper.readValue(outboxEvent.getPayload(), ExchangeEventPayload.class);

        assertEquals(ExchangeStatus.CANCELLED, persistedRequest.getStatus());
        assertEquals("alice", persistedRequest.getRequesterUsernameSnapshot());
        assertEquals("exchange.request.cancelled", outboxEvent.getEventType());
        assertEquals(request.getId(), payload.getExchangeRequestId());
        assertEquals("corr-cancel-jpa-123", payload.getCorrelationId());
        assertEquals(42L, payload.getInitiatorUserId());

        verify(bookServiceClient).makeAvailable(1001L);
        verify(bookServiceClient).makeAvailable(2002L);
    }

    @Test
    void completeShouldBeIdempotentForRepeatedConfirmationWithoutExtraOutboxEvent() {
        LocalDateTime ownerConfirmedAt = LocalDateTime.of(2026, 5, 24, 18, 45);
        ExchangeRequest request = exchangeRepository.saveAndFlush(
                completionPendingExchangeRequest(ownerConfirmedAt)
        );

        var response = exchangeService.complete(request.getId(), new UserPrincipal(55L, "owner"));
        entityManager.flush();
        entityManager.clear();

        ExchangeRequest persistedRequest = exchangeRepository.findById(request.getId()).orElseThrow();

        assertEquals(ExchangeStatus.COMPLETION_PENDING, response.getStatus());
        assertEquals(ownerConfirmedAt, response.getOwnerCompletionConfirmedAt());
        assertNull(response.getRequesterCompletionConfirmedAt());
        assertEquals(ownerConfirmedAt, persistedRequest.getOwnerCompletionConfirmedAt());
        assertEquals(0, outboxEventRepository.count());
        verifyNoInteractions(bookServiceClient);
    }

    @Test
    void completeShouldPersistFirstConfirmationAndCompletionConfirmedOutboxPayload() throws Exception {
        // arrange
        ExchangeRequest request = exchangeRepository.saveAndFlush(acceptedExchangeRequest());

        // act
        LocalDateTime ownerConfirmedAt;
        var response = (com.secondshelf.exchangeservice.dto.ExchangeResponse) null;
        try (CorrelationId.Scope ignored = CorrelationId.openScope("corr-complete-first-jpa-123")) {
            response = exchangeService.complete(request.getId(), new UserPrincipal(55L, "owner"));
            ownerConfirmedAt = response.getOwnerCompletionConfirmedAt();
        }
        entityManager.flush();
        entityManager.clear();

        // assert
        ExchangeRequest persistedRequest = exchangeRepository.findById(request.getId()).orElseThrow();
        OutboxEvent outboxEvent = singleOutboxEvent();
        ExchangeEventPayload payload = objectMapper.readValue(outboxEvent.getPayload(), ExchangeEventPayload.class);

        assertNotNull(ownerConfirmedAt);
        assertEquals("The Left Hand of Darkness", response.getRequestedBookTitle());
        assertEquals("Ursula K. Le Guin", response.getRequestedBookAuthor());
        assertEquals("Dune", response.getOfferedBookTitle());
        assertEquals("Frank Herbert", response.getOfferedBookAuthor());
        assertEquals(ExchangeStatus.COMPLETION_PENDING, persistedRequest.getStatus());
        assertEquals(ownerConfirmedAt, persistedRequest.getOwnerCompletionConfirmedAt());
        assertNull(persistedRequest.getRequesterCompletionConfirmedAt());

        assertEquals(OutboxEventStatus.PENDING, outboxEvent.getStatus());
        assertEquals("exchange.request.completion_confirmed", outboxEvent.getEventType());
        assertEquals(2, payload.getSchemaVersion());
        assertEquals("corr-complete-first-jpa-123", payload.getCorrelationId());
        assertEquals("exchange.request.completion_confirmed", payload.getEventType());
        assertEquals(55L, payload.getInitiatorUserId());
        assertEquals("owner", payload.getInitiatorUsername());
        assertEquals(55L, payload.getCompletedByUserId());
        assertEquals(ExchangeStatus.COMPLETION_PENDING, payload.getStatus());
        assertEquals(ownerConfirmedAt, payload.getOwnerCompletionConfirmedAt());
        assertNull(payload.getRequesterCompletionConfirmedAt());
        assertEquals("The Left Hand of Darkness", payload.getRequestedBookTitle());
        assertEquals("Dune", payload.getOfferedBookTitle());
        assertEquals("Can meet near the station.", payload.getRequestMessage());

        verifyNoInteractions(bookServiceClient);
    }

    @Test
    void completeShouldPersistSecondConfirmationAndCompletedOutboxPayload() throws Exception {
        // arrange
        LocalDateTime ownerConfirmedAt = LocalDateTime.of(2026, 5, 24, 18, 45);
        ExchangeRequest request = exchangeRepository.saveAndFlush(
                completionPendingExchangeRequest(ownerConfirmedAt)
        );
        when(bookServiceClient.markExchanged(1001L)).thenReturn(new BookDto());
        when(bookServiceClient.markExchanged(2002L)).thenReturn(new BookDto());

        // act
        LocalDateTime requesterConfirmedAt;
        var response = (com.secondshelf.exchangeservice.dto.ExchangeResponse) null;
        try (CorrelationId.Scope ignored = CorrelationId.openScope("corr-complete-second-jpa-123")) {
            response = exchangeService.complete(request.getId(), new UserPrincipal(42L, "alice"));
            requesterConfirmedAt = response.getRequesterCompletionConfirmedAt();
        }
        entityManager.flush();
        entityManager.clear();

        // assert
        ExchangeRequest persistedRequest = exchangeRepository.findById(request.getId()).orElseThrow();
        OutboxEvent outboxEvent = singleOutboxEvent();
        ExchangeEventPayload payload = objectMapper.readValue(outboxEvent.getPayload(), ExchangeEventPayload.class);

        assertNotNull(requesterConfirmedAt);
        assertEquals("The Left Hand of Darkness", response.getRequestedBookTitle());
        assertEquals("Ursula K. Le Guin", response.getRequestedBookAuthor());
        assertEquals("Dune", response.getOfferedBookTitle());
        assertEquals("Frank Herbert", response.getOfferedBookAuthor());
        assertEquals(ExchangeStatus.COMPLETED, persistedRequest.getStatus());
        assertEquals(ownerConfirmedAt, persistedRequest.getOwnerCompletionConfirmedAt());
        assertEquals(requesterConfirmedAt, persistedRequest.getRequesterCompletionConfirmedAt());

        assertEquals(OutboxEventStatus.PENDING, outboxEvent.getStatus());
        assertEquals("exchange.request.completed", outboxEvent.getEventType());
        assertEquals(2, payload.getSchemaVersion());
        assertEquals("corr-complete-second-jpa-123", payload.getCorrelationId());
        assertEquals("exchange.request.completed", payload.getEventType());
        assertEquals(42L, payload.getInitiatorUserId());
        assertEquals("alice", payload.getInitiatorUsername());
        assertEquals(42L, payload.getCompletedByUserId());
        assertEquals(ExchangeStatus.COMPLETED, payload.getStatus());
        assertEquals(ownerConfirmedAt, payload.getOwnerCompletionConfirmedAt());
        assertEquals(requesterConfirmedAt, payload.getRequesterCompletionConfirmedAt());
        assertEquals("The Left Hand of Darkness", payload.getRequestedBookTitle());
        assertEquals("Dune", payload.getOfferedBookTitle());

        verify(bookServiceClient).markExchanged(1001L);
        verify(bookServiceClient).markExchanged(2002L);
    }

    private OutboxEvent singleOutboxEvent() {
        List<OutboxEvent> outboxEvents = outboxEventRepository.findAll();
        assertEquals(1, outboxEvents.size());
        return outboxEvents.get(0);
    }

    private ExchangeRequest acceptedExchangeRequest() {
        return ExchangeRequest.builder()
                .requestedBookId(1001L)
                .requestedBookTitle("The Left Hand of Darkness")
                .requestedBookAuthor("Ursula K. Le Guin")
                .offeredBookId(2002L)
                .offeredBookTitle("Dune")
                .offeredBookAuthor("Frank Herbert")
                .ownerId(55L)
                .requesterId(42L)
                .status(ExchangeStatus.ACCEPTED)
                .message("Can meet near the station.")
                .build();
    }

    private ExchangeRequest acceptedCandidate() {
        ExchangeRequest request = acceptedExchangeRequest();
        request.setStatus(ExchangeStatus.PENDING);
        return request;
    }

    private ExchangeRequest conflictingPendingRequest(Long requestedBookId,
                                                      Long offeredBookId,
                                                      Long requesterId,
                                                      Long ownerId) {
        return ExchangeRequest.builder()
                .requestedBookId(requestedBookId)
                .requestedBookTitle("Conflict requested " + requestedBookId)
                .requestedBookAuthor("Author")
                .offeredBookId(offeredBookId)
                .offeredBookTitle("Conflict offered " + offeredBookId)
                .offeredBookAuthor("Author")
                .ownerId(ownerId)
                .requesterId(requesterId)
                .status(ExchangeStatus.PENDING)
                .build();
    }

    private ExchangeRequest completionPendingExchangeRequest(LocalDateTime ownerConfirmedAt) {
        ExchangeRequest request = acceptedExchangeRequest();
        request.setStatus(ExchangeStatus.COMPLETION_PENDING);
        request.setOwnerCompletionConfirmedAt(ownerConfirmedAt);
        return request;
    }

    private long countEvents(List<OutboxEvent> outboxEvents, String eventType) {
        return outboxEvents.stream()
                .filter(event -> eventType.equals(event.getEventType()))
                .count();
    }

    private BookDto book(Long id, Long ownerId, String title, String author) {
        BookDto book = new BookDto();
        book.setId(id);
        book.setOwnerId(ownerId);
        book.setTitle(title);
        book.setAuthor(author);
        book.setStatus("AVAILABLE");
        book.setVisibility("PUBLIC");
        return book;
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

        @Bean
        ExchangeService exchangeService(ExchangeRepository exchangeRepository,
                                        BookServiceClient bookServiceClient,
                                        ExchangeOutboxService exchangeOutboxService) {
            return new ExchangeService(exchangeRepository, bookServiceClient, exchangeOutboxService);
        }
    }
}
