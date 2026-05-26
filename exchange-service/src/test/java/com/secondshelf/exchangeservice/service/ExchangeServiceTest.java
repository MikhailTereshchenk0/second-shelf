package com.secondshelf.exchangeservice.service;

import com.secondshelf.exchangeservice.client.BookServiceClient;
import com.secondshelf.exchangeservice.client.dto.BookDto;
import com.secondshelf.exchangeservice.dto.CreateExchangeRequest;
import com.secondshelf.exchangeservice.dto.ExchangeResponse;
import com.secondshelf.exchangeservice.entity.ExchangeRequest;
import com.secondshelf.exchangeservice.entity.ExchangeStatus;
import com.secondshelf.exchangeservice.exception.ExchangeBadRequestException;
import com.secondshelf.exchangeservice.exception.ExchangeConflictException;
import com.secondshelf.exchangeservice.exception.ExchangeForbiddenException;
import com.secondshelf.exchangeservice.exception.ExchangeNotFoundException;
import com.secondshelf.exchangeservice.outbox.ExchangeEventContext;
import com.secondshelf.exchangeservice.outbox.ExchangeEventType;
import com.secondshelf.exchangeservice.outbox.ExchangeOutboxService;
import com.secondshelf.exchangeservice.repository.ExchangeRepository;
import com.secondshelf.exchangeservice.security.UserPrincipal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExchangeServiceTest {

    @Mock
    private ExchangeRepository exchangeRepository;

    @Mock
    private BookServiceClient bookServiceClient;

    @Mock
    private ExchangeOutboxService exchangeOutboxService;

    @InjectMocks
    private ExchangeService exchangeService;

    @Test
    void createShouldSavePendingRequestForAvailablePublicBook() {
        // arrange
        CreateExchangeRequest request = new CreateExchangeRequest();
        request.setRequestedBookId(100L);
        request.setOfferedBookId(200L);
        request.setMessage("I would like to exchange this book.");

        BookDto requestedBook = new BookDto();
        requestedBook.setId(100L);
        requestedBook.setOwnerId(55L);
        requestedBook.setTitle("The Left Hand of Darkness");
        requestedBook.setAuthor("Ursula K. Le Guin");
        requestedBook.setVisibility("PUBLIC");
        requestedBook.setStatus("AVAILABLE");

        BookDto offeredBook = new BookDto();
        offeredBook.setId(200L);
        offeredBook.setOwnerId(42L);
        offeredBook.setTitle("Dune");
        offeredBook.setAuthor("Frank Herbert");
        offeredBook.setVisibility("PUBLIC");
        offeredBook.setStatus("AVAILABLE");

        when(bookServiceClient.getBook(100L)).thenReturn(requestedBook);
        when(bookServiceClient.getBook(200L)).thenReturn(offeredBook);

        when(exchangeRepository.existsByRequesterIdAndRequestedBookIdAndOfferedBookIdAndStatusIn(
                42L,
                100L,
                200L,
                List.of(ExchangeStatus.PENDING, ExchangeStatus.ACCEPTED, ExchangeStatus.COMPLETION_PENDING, ExchangeStatus.REPAIR_REQUIRED)
        )).thenReturn(false);

        when(exchangeRepository.save(any(ExchangeRequest.class))).thenAnswer(invocation -> {
            ExchangeRequest saved = invocation.getArgument(0);
            saved.setId(1L);
            return saved;
        });

        ArgumentCaptor<ExchangeRequest> exchangeCaptor = ArgumentCaptor.forClass(ExchangeRequest.class);

        // act
        ExchangeResponse response = exchangeService.create(request, new UserPrincipal(42L, "alice"));

        // assert
        assertNotNull(response);
        assertEquals(1L, response.getId());
        assertEquals(100L, response.getRequestedBookId());
        assertEquals("The Left Hand of Darkness", response.getRequestedBookTitle());
        assertEquals("Ursula K. Le Guin", response.getRequestedBookAuthor());
        assertEquals(200L, response.getOfferedBookId());
        assertEquals("Dune", response.getOfferedBookTitle());
        assertEquals("Frank Herbert", response.getOfferedBookAuthor());
        assertEquals(55L, response.getOwnerId());
        assertEquals(42L, response.getRequesterId());
        assertNull(response.getOwnerUsernameSnapshot());
        assertEquals("alice", response.getRequesterUsernameSnapshot());
        assertEquals(ExchangeStatus.PENDING, response.getStatus());
        assertEquals("I would like to exchange this book.", response.getMessage());

        verify(exchangeRepository).save(exchangeCaptor.capture());

        ExchangeRequest savedRequest = exchangeCaptor.getValue();
        assertEquals(100L, savedRequest.getRequestedBookId());
        assertEquals(200L, savedRequest.getOfferedBookId());
        assertEquals("The Left Hand of Darkness", savedRequest.getRequestedBookTitle());
        assertEquals("Ursula K. Le Guin", savedRequest.getRequestedBookAuthor());
        assertEquals("Dune", savedRequest.getOfferedBookTitle());
        assertEquals("Frank Herbert", savedRequest.getOfferedBookAuthor());
        assertEquals(55L, savedRequest.getOwnerId());
        assertEquals(42L, savedRequest.getRequesterId());
        assertNull(savedRequest.getOwnerUsernameSnapshot());
        assertEquals("alice", savedRequest.getRequesterUsernameSnapshot());
        assertEquals(ExchangeStatus.PENDING, savedRequest.getStatus());
        assertEquals("I would like to exchange this book.", savedRequest.getMessage());

        verify(exchangeOutboxService).recordExchangeEvent(
                ExchangeEventType.EXCHANGE_REQUEST_CREATED,
                savedRequest,
                eventContext(42L, "alice")
        );
    }

    @Test
    void createShouldSavePendingRequestWithIdempotencyKeyHash() {
        // arrange
        CreateExchangeRequest request = new CreateExchangeRequest();
        request.setRequestedBookId(100L);
        request.setOfferedBookId(200L);
        request.setMessage("I would like to exchange this book.");

        BookDto requestedBook = book(100L, 55L, "The Left Hand of Darkness", "Ursula K. Le Guin", "AVAILABLE", "PUBLIC");
        BookDto offeredBook = book(200L, 42L, "Dune", "Frank Herbert", "AVAILABLE", "PUBLIC");

        when(exchangeRepository.findByRequesterIdAndIdempotencyKeyHash(eq(42L), anyString()))
                .thenReturn(Optional.empty());
        when(bookServiceClient.getBook(100L)).thenReturn(requestedBook);
        when(bookServiceClient.getBook(200L)).thenReturn(offeredBook);
        when(exchangeRepository.existsByRequesterIdAndRequestedBookIdAndOfferedBookIdAndStatusIn(
                42L,
                100L,
                200L,
                List.of(ExchangeStatus.PENDING, ExchangeStatus.ACCEPTED, ExchangeStatus.COMPLETION_PENDING, ExchangeStatus.REPAIR_REQUIRED)
        )).thenReturn(false);
        when(exchangeRepository.save(any(ExchangeRequest.class))).thenAnswer(invocation -> {
            ExchangeRequest saved = invocation.getArgument(0);
            saved.setId(1L);
            return saved;
        });

        ArgumentCaptor<ExchangeRequest> exchangeCaptor = ArgumentCaptor.forClass(ExchangeRequest.class);

        // act
        ExchangeResponse response = exchangeService.create(request, new UserPrincipal(42L, "alice"), "retry-key-123456");

        // assert
        assertEquals(1L, response.getId());
        assertEquals(ExchangeStatus.PENDING, response.getStatus());

        verify(exchangeRepository).save(exchangeCaptor.capture());
        ExchangeRequest savedRequest = exchangeCaptor.getValue();
        assertNull(savedRequest.getIdempotencyKey());
        assertNotNull(savedRequest.getIdempotencyKeyHash());
        assertEquals(64, savedRequest.getIdempotencyKeyHash().length());
        verify(exchangeOutboxService).recordExchangeEvent(
                ExchangeEventType.EXCHANGE_REQUEST_CREATED,
                savedRequest,
                eventContext(42L, "alice")
        );
    }

    @Test
    void createShouldReturnExistingExchangeForIdenticalIdempotentReplay() {
        // arrange
        CreateExchangeRequest request = new CreateExchangeRequest();
        request.setRequestedBookId(100L);
        request.setOfferedBookId(200L);
        request.setMessage("I would like to exchange this book.");

        ExchangeRequest existingRequest = ExchangeRequest.builder()
                .id(77L)
                .requestedBookId(100L)
                .requestedBookTitle("The Left Hand of Darkness")
                .requestedBookAuthor("Ursula K. Le Guin")
                .offeredBookId(200L)
                .offeredBookTitle("Dune")
                .offeredBookAuthor("Frank Herbert")
                .ownerId(55L)
                .requesterId(42L)
                .requesterUsernameSnapshot("alice")
                .status(ExchangeStatus.PENDING)
                .message("I would like to exchange this book.")
                .idempotencyKeyHash("hash")
                .build();

        when(exchangeRepository.findByRequesterIdAndIdempotencyKeyHash(eq(42L), anyString()))
                .thenReturn(Optional.of(existingRequest));

        // act
        ExchangeResponse response = exchangeService.create(request, new UserPrincipal(42L, "alice"), "retry-key-123456");

        // assert
        assertEquals(77L, response.getId());
        assertEquals(ExchangeStatus.PENDING, response.getStatus());
        assertEquals("The Left Hand of Darkness", response.getRequestedBookTitle());

        verify(exchangeRepository).findByRequesterIdAndIdempotencyKeyHash(eq(42L), anyString());
        verifyNoInteractions(bookServiceClient);
        verify(exchangeRepository, never()).save(any(ExchangeRequest.class));
        verifyNoInteractions(exchangeOutboxService);
    }

    @Test
    void createShouldRejectSameIdempotencyKeyWithDifferentPayload() {
        // arrange
        CreateExchangeRequest request = new CreateExchangeRequest();
        request.setRequestedBookId(100L);
        request.setOfferedBookId(201L);
        request.setMessage("A different message.");

        ExchangeRequest existingRequest = ExchangeRequest.builder()
                .id(77L)
                .requestedBookId(100L)
                .offeredBookId(200L)
                .ownerId(55L)
                .requesterId(42L)
                .status(ExchangeStatus.PENDING)
                .message("I would like to exchange this book.")
                .idempotencyKeyHash("hash")
                .build();

        when(exchangeRepository.findByRequesterIdAndIdempotencyKeyHash(eq(42L), anyString()))
                .thenReturn(Optional.of(existingRequest));

        // act
        ExchangeConflictException exception = assertThrows(
                ExchangeConflictException.class,
                () -> exchangeService.create(request, new UserPrincipal(42L, "alice"), "retry-key-123456")
        );

        // assert
        assertEquals("IDEMPOTENCY_KEY_REUSED_WITH_DIFFERENT_REQUEST", exception.getCode());
        assertEquals("Idempotency-Key was already used with a different exchange request payload.", exception.getMessage());

        verifyNoInteractions(bookServiceClient);
        verify(exchangeRepository, never()).save(any(ExchangeRequest.class));
        verifyNoInteractions(exchangeOutboxService);
    }

    @Test
    void createShouldRejectInvalidIdempotencyKeyLength() {
        // arrange
        CreateExchangeRequest request = new CreateExchangeRequest();
        request.setRequestedBookId(100L);
        request.setOfferedBookId(200L);

        // act
        ExchangeBadRequestException exception = assertThrows(
                ExchangeBadRequestException.class,
                () -> exchangeService.create(request, new UserPrincipal(42L, "alice"), "too-short")
        );

        // assert
        assertEquals("INVALID_IDEMPOTENCY_KEY", exception.getCode());
        assertEquals("Idempotency-Key length must be between 16 and 128 characters.", exception.getMessage());

        verifyNoInteractions(exchangeRepository);
        verifyNoInteractions(bookServiceClient);
        verifyNoInteractions(exchangeOutboxService);
    }

    @Test
    void createShouldRejectRequestForOwnBook() {
        // arrange
        CreateExchangeRequest request = new CreateExchangeRequest();
        request.setRequestedBookId(100L);
        request.setOfferedBookId(200L);

        BookDto book = new BookDto();
        book.setId(100L);
        book.setOwnerId(42L);
        book.setVisibility("PUBLIC");
        book.setStatus("AVAILABLE");

        when(bookServiceClient.getBook(100L)).thenReturn(book);

        // act
        ExchangeBadRequestException exception = assertThrows(
                ExchangeBadRequestException.class,
                () -> exchangeService.create(request, new UserPrincipal(42L, "alice"))
        );

        // assert
        assertEquals("OWN_BOOK_EXCHANGE_NOT_ALLOWED", exception.getCode());
        assertEquals("You cannot request exchange for your own book.", exception.getMessage());
        verify(exchangeRepository, never()).save(any(ExchangeRequest.class));
        verifyNoInteractions(exchangeOutboxService);
    }

    @Test
    void createShouldRejectNonPublicBook() {
        // arrange
        CreateExchangeRequest request = new CreateExchangeRequest();
        request.setRequestedBookId(100L);
        request.setOfferedBookId(200L);

        BookDto book = new BookDto();
        book.setId(100L);
        book.setOwnerId(55L);
        book.setVisibility("PRIVATE");
        book.setStatus("AVAILABLE");

        when(bookServiceClient.getBook(100L)).thenReturn(book);

        // act
        ExchangeConflictException exception = assertThrows(
                ExchangeConflictException.class,
                () -> exchangeService.create(request, new UserPrincipal(42L, "alice"))
        );

        // assert
        assertEquals("REQUESTED_BOOK_NOT_PUBLIC", exception.getCode());
        assertEquals("Requested book must be public.", exception.getMessage());
        verify(exchangeRepository, never()).save(any(ExchangeRequest.class));
        verifyNoInteractions(exchangeOutboxService);
    }

    @Test
    void createShouldRejectWhenRequestedAndOfferedBookAreSame() {
        // arrange
        CreateExchangeRequest request = new CreateExchangeRequest();
        request.setRequestedBookId(100L);
        request.setOfferedBookId(100L);

        // act
        ExchangeBadRequestException exception = assertThrows(
                ExchangeBadRequestException.class,
                () -> exchangeService.create(request, new UserPrincipal(42L, "alice"))
        );

        // assert
        assertEquals("INVALID_EXCHANGE_BOOK_SELECTION", exception.getCode());
        assertEquals("Requested book and offered book must be different.", exception.getMessage());
        verify(bookServiceClient, never()).getBook(anyLong());
        verify(exchangeRepository, never()).save(any(ExchangeRequest.class));
        verifyNoInteractions(exchangeOutboxService);
    }

    @Test
    void createShouldRejectWhenOfferedBookDoesNotBelongToRequester() {
        // arrange
        CreateExchangeRequest request = new CreateExchangeRequest();
        request.setRequestedBookId(100L);
        request.setOfferedBookId(200L);

        BookDto requestedBook = new BookDto();
        requestedBook.setId(100L);
        requestedBook.setOwnerId(55L);
        requestedBook.setVisibility("PUBLIC");
        requestedBook.setStatus("AVAILABLE");

        BookDto offeredBook = new BookDto();
        offeredBook.setId(200L);
        offeredBook.setOwnerId(99L);
        offeredBook.setVisibility("PUBLIC");
        offeredBook.setStatus("AVAILABLE");

        when(bookServiceClient.getBook(100L)).thenReturn(requestedBook);
        when(bookServiceClient.getBook(200L)).thenReturn(offeredBook);

        // act
        ExchangeForbiddenException exception = assertThrows(
                ExchangeForbiddenException.class,
                () -> exchangeService.create(request, new UserPrincipal(42L, "alice"))
        );

        // assert
        assertEquals("OFFERED_BOOK_NOT_OWNED_BY_REQUESTER", exception.getCode());
        assertEquals("Offered book must belong to requester.", exception.getMessage());
        verify(exchangeRepository, never()).save(any(ExchangeRequest.class));
        verifyNoInteractions(exchangeOutboxService);
    }

    @Test
    void createShouldRejectWhenOfferedBookIsNotPublic() {
        // arrange
        CreateExchangeRequest request = new CreateExchangeRequest();
        request.setRequestedBookId(100L);
        request.setOfferedBookId(200L);

        BookDto requestedBook = new BookDto();
        requestedBook.setId(100L);
        requestedBook.setOwnerId(55L);
        requestedBook.setVisibility("PUBLIC");
        requestedBook.setStatus("AVAILABLE");

        BookDto offeredBook = new BookDto();
        offeredBook.setId(200L);
        offeredBook.setOwnerId(42L);
        offeredBook.setVisibility("PRIVATE");
        offeredBook.setStatus("AVAILABLE");

        when(bookServiceClient.getBook(100L)).thenReturn(requestedBook);
        when(bookServiceClient.getBook(200L)).thenReturn(offeredBook);

        // act
        ExchangeConflictException exception = assertThrows(
                ExchangeConflictException.class,
                () -> exchangeService.create(request, new UserPrincipal(42L, "alice"))
        );

        // assert
        assertEquals("OFFERED_BOOK_NOT_PUBLIC", exception.getCode());
        assertEquals("Offered book must be public.", exception.getMessage());
        verify(exchangeRepository, never()).save(any(ExchangeRequest.class));
        verifyNoInteractions(exchangeOutboxService);
    }

    @Test
    void createShouldRejectWhenOfferedBookIsNotAvailable() {
        // arrange
        CreateExchangeRequest request = new CreateExchangeRequest();
        request.setRequestedBookId(100L);
        request.setOfferedBookId(200L);

        BookDto requestedBook = new BookDto();
        requestedBook.setId(100L);
        requestedBook.setOwnerId(55L);
        requestedBook.setVisibility("PUBLIC");
        requestedBook.setStatus("AVAILABLE");

        BookDto offeredBook = new BookDto();
        offeredBook.setId(200L);
        offeredBook.setOwnerId(42L);
        offeredBook.setVisibility("PUBLIC");
        offeredBook.setStatus("RESERVED");

        when(bookServiceClient.getBook(100L)).thenReturn(requestedBook);
        when(bookServiceClient.getBook(200L)).thenReturn(offeredBook);

        // act
        ExchangeConflictException exception = assertThrows(
                ExchangeConflictException.class,
                () -> exchangeService.create(request, new UserPrincipal(42L, "alice"))
        );

        // assert
        assertEquals("OFFERED_BOOK_NOT_AVAILABLE", exception.getCode());
        assertEquals("Offered book must be available.", exception.getMessage());
        verify(exchangeRepository, never()).save(any(ExchangeRequest.class));
        verifyNoInteractions(exchangeOutboxService);
    }

    @Test
    void createShouldRejectDuplicateActiveRequest() {
        // arrange
        CreateExchangeRequest request = new CreateExchangeRequest();
        request.setRequestedBookId(100L);
        request.setOfferedBookId(200L);

        BookDto requestedBook = new BookDto();
        requestedBook.setId(100L);
        requestedBook.setOwnerId(55L);
        requestedBook.setVisibility("PUBLIC");
        requestedBook.setStatus("AVAILABLE");

        BookDto offeredBook = new BookDto();
        offeredBook.setId(200L);
        offeredBook.setOwnerId(42L);
        offeredBook.setVisibility("PUBLIC");
        offeredBook.setStatus("AVAILABLE");

        when(bookServiceClient.getBook(100L)).thenReturn(requestedBook);
        when(bookServiceClient.getBook(200L)).thenReturn(offeredBook);
        when(exchangeRepository.existsByRequesterIdAndRequestedBookIdAndOfferedBookIdAndStatusIn(
                42L,
                100L,
                200L,
                List.of(ExchangeStatus.PENDING, ExchangeStatus.ACCEPTED, ExchangeStatus.COMPLETION_PENDING, ExchangeStatus.REPAIR_REQUIRED)
        )).thenReturn(true);

        // act
        ExchangeConflictException exception = assertThrows(
                ExchangeConflictException.class,
                () -> exchangeService.create(request, new UserPrincipal(42L, "alice"))
        );

        // assert
        assertEquals("DUPLICATE_ACTIVE_EXCHANGE_REQUEST", exception.getCode());
        assertEquals("Duplicate active exchange request already exists.", exception.getMessage());
        verify(exchangeRepository, never()).save(any(ExchangeRequest.class));
        verifyNoInteractions(exchangeOutboxService);
    }

    @Test
    void createShouldMapRequestedBookNotFoundToDomainException() {
        // arrange
        CreateExchangeRequest request = new CreateExchangeRequest();
        request.setRequestedBookId(100L);
        request.setOfferedBookId(200L);

        when(bookServiceClient.getBook(100L)).thenThrow(bookServiceException(HttpStatus.NOT_FOUND));

        // act
        ExchangeNotFoundException exception = assertThrows(
                ExchangeNotFoundException.class,
                () -> exchangeService.create(request, new UserPrincipal(42L, "alice"))
        );

        // assert
        assertEquals("REQUESTED_BOOK_NOT_FOUND", exception.getCode());
        assertEquals("Requested book not found.", exception.getMessage());
        verify(bookServiceClient, never()).getBook(200L);
        verify(exchangeRepository, never()).save(any(ExchangeRequest.class));
        verifyNoInteractions(exchangeOutboxService);
    }

    @Test
    void createShouldMapOfferedBookNotFoundToDomainException() {
        // arrange
        CreateExchangeRequest request = new CreateExchangeRequest();
        request.setRequestedBookId(100L);
        request.setOfferedBookId(200L);

        BookDto requestedBook = new BookDto();
        requestedBook.setId(100L);
        requestedBook.setOwnerId(55L);
        requestedBook.setVisibility("PUBLIC");
        requestedBook.setStatus("AVAILABLE");

        when(bookServiceClient.getBook(100L)).thenReturn(requestedBook);
        when(bookServiceClient.getBook(200L)).thenThrow(bookServiceException(HttpStatus.NOT_FOUND));

        // act
        ExchangeNotFoundException exception = assertThrows(
                ExchangeNotFoundException.class,
                () -> exchangeService.create(request, new UserPrincipal(42L, "alice"))
        );

        // assert
        assertEquals("OFFERED_BOOK_NOT_FOUND", exception.getCode());
        assertEquals("Offered book not found.", exception.getMessage());
        verify(exchangeRepository, never()).save(any(ExchangeRequest.class));
        verifyNoInteractions(exchangeOutboxService);
    }

    @Test
    void acceptShouldLockBothBooksReserveThemAndMarkRequestAccepted() {
        // arrange
        ExchangeRequest request = ExchangeRequest.builder()
                .id(10L)
                .requestedBookId(100L)
                .offeredBookId(200L)
                .ownerId(55L)
                .requesterId(42L)
                .status(ExchangeStatus.PENDING)
                .message("please accept")
                .build();

        when(exchangeRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(request));
        when(exchangeRepository.lockAllActiveByBookIds(
                List.of(100L, 200L),
                List.of(ExchangeStatus.PENDING, ExchangeStatus.ACCEPTED, ExchangeStatus.COMPLETION_PENDING, ExchangeStatus.REPAIR_REQUIRED)
        )).thenReturn(List.of(request));
        when(exchangeRepository.existsAnotherByStatusesAndBookIds(
                10L,
                List.of(100L, 200L),
                List.of(ExchangeStatus.ACCEPTED, ExchangeStatus.COMPLETION_PENDING, ExchangeStatus.REPAIR_REQUIRED)
        )).thenReturn(false);
        when(exchangeRepository.save(any(ExchangeRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // act
        ExchangeResponse response = exchangeService.accept(10L, new UserPrincipal(55L, "owner"));

        // assert
        assertNotNull(response);
        assertEquals(ExchangeStatus.ACCEPTED, response.getStatus());
        assertEquals("owner", response.getOwnerUsernameSnapshot());
        assertNull(response.getRequesterUsernameSnapshot());

        verify(exchangeRepository).lockAllActiveByBookIds(
                List.of(100L, 200L),
                List.of(ExchangeStatus.PENDING, ExchangeStatus.ACCEPTED, ExchangeStatus.COMPLETION_PENDING, ExchangeStatus.REPAIR_REQUIRED)
        );
        verify(bookServiceClient).reserve(100L);
        verify(bookServiceClient).reserve(200L);
        verify(exchangeRepository, never()).saveAll(anyList());
        verify(exchangeRepository).save(request);
        assertEquals(ExchangeStatus.ACCEPTED, request.getStatus());
        assertEquals("owner", request.getOwnerUsernameSnapshot());
        assertNull(request.getRequesterUsernameSnapshot());
        verify(exchangeOutboxService).recordExchangeEvent(
                ExchangeEventType.EXCHANGE_REQUEST_ACCEPTED,
                request,
                eventContext(55L, "owner")
        );
    }

    @Test
    void acceptShouldRecordDeclinedEventsForConflictingPendingRequests() {
        // arrange
        ExchangeRequest request = ExchangeRequest.builder()
                .id(10L)
                .requestedBookId(100L)
                .offeredBookId(200L)
                .ownerId(55L)
                .requesterId(42L)
                .status(ExchangeStatus.PENDING)
                .build();

        ExchangeRequest conflictingRequest = ExchangeRequest.builder()
                .id(11L)
                .requestedBookId(100L)
                .offeredBookId(300L)
                .ownerId(55L)
                .requesterId(77L)
                .status(ExchangeStatus.PENDING)
                .build();

        when(exchangeRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(request));
        when(exchangeRepository.lockAllActiveByBookIds(
                List.of(100L, 200L),
                List.of(ExchangeStatus.PENDING, ExchangeStatus.ACCEPTED, ExchangeStatus.COMPLETION_PENDING, ExchangeStatus.REPAIR_REQUIRED)
        )).thenReturn(List.of(request, conflictingRequest));
        when(exchangeRepository.existsAnotherByStatusesAndBookIds(
                10L,
                List.of(100L, 200L),
                List.of(ExchangeStatus.ACCEPTED, ExchangeStatus.COMPLETION_PENDING, ExchangeStatus.REPAIR_REQUIRED)
        )).thenReturn(false);
        when(exchangeRepository.save(any(ExchangeRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(exchangeRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        // act
        ExchangeResponse response = exchangeService.accept(10L, new UserPrincipal(55L, "owner"));

        // assert
        assertEquals(ExchangeStatus.ACCEPTED, response.getStatus());
        assertEquals(ExchangeStatus.DECLINED, conflictingRequest.getStatus());

        verify(exchangeRepository).saveAll(List.of(conflictingRequest));
        verify(exchangeOutboxService).recordExchangeEvent(
                ExchangeEventType.EXCHANGE_REQUEST_ACCEPTED,
                request,
                eventContext(55L, "owner")
        );
        verify(exchangeOutboxService).recordExchangeEvent(
                ExchangeEventType.EXCHANGE_REQUEST_DECLINED,
                conflictingRequest,
                eventContext(55L, "owner")
        );
    }

    @Test
    void acceptShouldRejectWhenAnyBookAlreadyParticipatesInAcceptedExchange() {
        // arrange
        ExchangeRequest request = ExchangeRequest.builder()
                .id(10L)
                .requestedBookId(100L)
                .offeredBookId(200L)
                .ownerId(55L)
                .requesterId(42L)
                .status(ExchangeStatus.PENDING)
                .build();

        when(exchangeRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(request));
        when(exchangeRepository.existsAnotherByStatusesAndBookIds(
                10L,
                List.of(100L, 200L),
                List.of(ExchangeStatus.ACCEPTED, ExchangeStatus.COMPLETION_PENDING, ExchangeStatus.REPAIR_REQUIRED)
        )).thenReturn(true);
        when(exchangeRepository.lockAllActiveByBookIds(
                List.of(100L, 200L),
                List.of(ExchangeStatus.PENDING, ExchangeStatus.ACCEPTED, ExchangeStatus.COMPLETION_PENDING, ExchangeStatus.REPAIR_REQUIRED)
        )).thenReturn(List.of(request));

        // act
        ExchangeConflictException exception = assertThrows(
                ExchangeConflictException.class,
                () -> exchangeService.accept(10L, new UserPrincipal(55L, "owner"))
        );

        // assert
        assertEquals("BOOK_ALREADY_IN_ACCEPTED_EXCHANGE", exception.getCode());
        assertEquals("One of the books already participates in another accepted exchange.", exception.getMessage());
        verify(exchangeRepository).lockAllActiveByBookIds(
                List.of(100L, 200L),
                List.of(ExchangeStatus.PENDING, ExchangeStatus.ACCEPTED, ExchangeStatus.COMPLETION_PENDING, ExchangeStatus.REPAIR_REQUIRED)
        );
        verify(bookServiceClient, never()).reserve(anyLong());
        verify(exchangeRepository, never()).save(any(ExchangeRequest.class));
        verify(exchangeRepository, never()).saveAll(anyList());
        verifyNoInteractions(exchangeOutboxService);
    }

    @Test
    void acceptShouldRejectWhenActionIsPerformedByAnotherUser() {
        // arrange
        ExchangeRequest request = ExchangeRequest.builder()
                .id(10L)
                .requestedBookId(100L)
                .offeredBookId(200L)
                .ownerId(55L)
                .requesterId(42L)
                .status(ExchangeStatus.PENDING)
                .build();

        when(exchangeRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(request));

        // act
        ExchangeForbiddenException exception = assertThrows(
                ExchangeForbiddenException.class,
                () -> exchangeService.accept(10L, new UserPrincipal(99L, "intruder"))
        );

        // assert
        assertEquals("ONLY_OWNER_CAN_ACCEPT", exception.getCode());
        assertEquals("Only owner can accept.", exception.getMessage());
        verify(bookServiceClient, never()).reserve(anyLong());
        verify(exchangeRepository, never()).save(any(ExchangeRequest.class));
        verifyNoInteractions(exchangeOutboxService);
    }

    @Test
    void acceptShouldRejectRequesterAcceptingOwnOutgoingRequest() {
        ExchangeRequest request = ExchangeRequest.builder()
                .id(10L)
                .requestedBookId(100L)
                .offeredBookId(200L)
                .ownerId(55L)
                .requesterId(42L)
                .status(ExchangeStatus.PENDING)
                .build();

        when(exchangeRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(request));

        ExchangeForbiddenException exception = assertThrows(
                ExchangeForbiddenException.class,
                () -> exchangeService.accept(10L, new UserPrincipal(42L, "requester"))
        );

        assertEquals("ONLY_OWNER_CAN_ACCEPT", exception.getCode());
        assertEquals("Only owner can accept.", exception.getMessage());
        verify(bookServiceClient, never()).reserve(anyLong());
        verify(exchangeRepository, never()).save(any(ExchangeRequest.class));
        verifyNoInteractions(exchangeOutboxService);
    }

    @Test
    void acceptShouldRejectInvalidStatusTransition() {
        // arrange
        ExchangeRequest request = ExchangeRequest.builder()
                .id(10L)
                .requestedBookId(100L)
                .offeredBookId(200L)
                .ownerId(55L)
                .requesterId(42L)
                .status(ExchangeStatus.DECLINED)
                .build();

        when(exchangeRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(request));

        // act
        ExchangeConflictException exception = assertThrows(
                ExchangeConflictException.class,
                () -> exchangeService.accept(10L, new UserPrincipal(55L, "owner"))
        );

        // assert
        assertEquals("INVALID_EXCHANGE_STATUS_TRANSITION", exception.getCode());
        assertEquals("Only PENDING request can be accepted.", exception.getMessage());
        verify(bookServiceClient, never()).reserve(anyLong());
        verify(exchangeRepository, never()).save(any(ExchangeRequest.class));
        verifyNoInteractions(exchangeOutboxService);
    }

    @Test
    void acceptShouldRollbackFirstReservationWhenSecondReservationFails() {
        // arrange
        ExchangeRequest request = ExchangeRequest.builder()
                .id(10L)
                .requestedBookId(100L)
                .offeredBookId(200L)
                .ownerId(55L)
                .requesterId(42L)
                .status(ExchangeStatus.PENDING)
                .build();

        when(exchangeRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(request));
        when(exchangeRepository.existsAnotherByStatusesAndBookIds(
                10L,
                List.of(100L, 200L),
                List.of(ExchangeStatus.ACCEPTED, ExchangeStatus.COMPLETION_PENDING, ExchangeStatus.REPAIR_REQUIRED)
        )).thenReturn(false);

        when(bookServiceClient.reserve(100L)).thenReturn(new BookDto());
        when(bookServiceClient.reserve(200L)).thenThrow(new IllegalStateException("Offered book cannot be reserved."));
        when(exchangeRepository.lockAllActiveByBookIds(
                List.of(100L, 200L),
                List.of(ExchangeStatus.PENDING, ExchangeStatus.ACCEPTED, ExchangeStatus.COMPLETION_PENDING, ExchangeStatus.REPAIR_REQUIRED)
        )).thenReturn(List.of(request));

        // act
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> exchangeService.accept(10L, new UserPrincipal(55L, "owner"))
        );

        // assert
        assertEquals("Offered book cannot be reserved.", exception.getMessage());

        verify(bookServiceClient).reserve(100L);
        verify(bookServiceClient).reserve(200L);
        verify(bookServiceClient).makeAvailable(100L);
        verify(exchangeRepository, never()).save(any(ExchangeRequest.class));
        verify(exchangeRepository, never()).saveAll(anyList());
        assertEquals(ExchangeStatus.PENDING, request.getStatus());
        verifyNoInteractions(exchangeOutboxService);
    }

    @Test
    void declineShouldAllowOwnerToDeclinePendingRequest() {
        // arrange
        ExchangeRequest request = ExchangeRequest.builder()
                .id(11L)
                .requestedBookId(101L)
                .ownerId(55L)
                .requesterId(42L)
                .status(ExchangeStatus.PENDING)
                .build();

        when(exchangeRepository.findByIdForUpdate(11L)).thenReturn(Optional.of(request));
        when(exchangeRepository.save(any(ExchangeRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // act
        ExchangeResponse response = exchangeService.decline(11L, new UserPrincipal(55L, "owner"));

        // assert
        assertEquals(ExchangeStatus.DECLINED, response.getStatus());
        verify(exchangeRepository).save(request);
        verify(exchangeOutboxService).recordExchangeEvent(
                ExchangeEventType.EXCHANGE_REQUEST_DECLINED,
                request,
                eventContext(55L, "owner")
        );
    }

    @Test
    void cancelShouldAllowRequesterToCancelPendingRequest() {
        // arrange
        ExchangeRequest request = ExchangeRequest.builder()
                .id(12L)
                .requestedBookId(102L)
                .ownerId(55L)
                .requesterId(42L)
                .status(ExchangeStatus.PENDING)
                .build();

        when(exchangeRepository.findByIdForUpdate(12L)).thenReturn(Optional.of(request));
        when(exchangeRepository.save(any(ExchangeRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // act
        ExchangeResponse response = exchangeService.cancel(12L, new UserPrincipal(42L, "requester"));

        // assert
        assertEquals(ExchangeStatus.CANCELLED, response.getStatus());
        verify(exchangeRepository).save(request);
        verify(exchangeOutboxService).recordExchangeEvent(
                ExchangeEventType.EXCHANGE_REQUEST_CANCELLED,
                request,
                eventContext(42L, "requester")
        );
    }

    @Test
    void cancelShouldRejectOwnerCancellingInsteadOfRequester() {
        ExchangeRequest request = ExchangeRequest.builder()
                .id(12L)
                .requestedBookId(102L)
                .offeredBookId(202L)
                .ownerId(55L)
                .requesterId(42L)
                .status(ExchangeStatus.PENDING)
                .build();

        when(exchangeRepository.findByIdForUpdate(12L)).thenReturn(Optional.of(request));

        ExchangeForbiddenException exception = assertThrows(
                ExchangeForbiddenException.class,
                () -> exchangeService.cancel(12L, new UserPrincipal(55L, "owner"))
        );

        assertEquals("ONLY_REQUESTER_CAN_CANCEL", exception.getCode());
        assertEquals("Only requester can cancel.", exception.getMessage());
        verify(bookServiceClient, never()).makeAvailable(anyLong());
        verify(exchangeRepository, never()).save(any(ExchangeRequest.class));
        verifyNoInteractions(exchangeOutboxService);
    }

    @Test
    void completeShouldRecordFirstConfirmationAndKeepBooksReserved() {
        // arrange
        ExchangeRequest request = ExchangeRequest.builder()
                .id(13L)
                .requestedBookId(103L)
                .offeredBookId(203L)
                .ownerId(55L)
                .requesterId(42L)
                .status(ExchangeStatus.ACCEPTED)
                .build();

        when(exchangeRepository.findByIdForUpdate(13L)).thenReturn(Optional.of(request));
        when(exchangeRepository.save(any(ExchangeRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // act
        ExchangeResponse response = exchangeService.complete(13L, new UserPrincipal(55L, "owner"));

        // assert
        assertEquals(ExchangeStatus.COMPLETION_PENDING, response.getStatus());
        assertEquals("owner", response.getOwnerUsernameSnapshot());
        assertNull(response.getRequesterUsernameSnapshot());
        assertNotNull(response.getOwnerCompletionConfirmedAt());
        assertNull(response.getRequesterCompletionConfirmedAt());
        verify(bookServiceClient, never()).markExchanged(anyLong());
        verify(exchangeRepository).save(request);
        assertEquals("owner", request.getOwnerUsernameSnapshot());
        verify(exchangeOutboxService).recordExchangeEvent(
                ExchangeEventType.EXCHANGE_REQUEST_COMPLETION_CONFIRMED,
                request,
                eventContext(55L, "owner", 55L)
        );
    }

    @Test
    void myOutgoingShouldReturnRequestsOfCurrentRequester() {
        // arrange
        ExchangeRequest request = ExchangeRequest.builder()
                .id(20L)
                .requestedBookId(200L)
                .requestedBookTitle("The Dispossessed")
                .requestedBookAuthor("Ursula K. Le Guin")
                .offeredBookId(300L)
                .offeredBookTitle("Neuromancer")
                .offeredBookAuthor("William Gibson")
                .ownerId(55L)
                .requesterId(42L)
                .ownerUsernameSnapshot("owner")
                .requesterUsernameSnapshot("alice")
                .status(ExchangeStatus.PENDING)
                .build();

        when(exchangeRepository.findAllByRequesterId(42L, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(request)));

        // act
        var result = exchangeService.myOutgoing(new UserPrincipal(42L, "alice"), PageRequest.of(0, 20));

        // assert
        assertEquals(1, result.getTotalElements());
        assertEquals(20L, result.getContent().get(0).getId());
        assertEquals(42L, result.getContent().get(0).getRequesterId());
        assertEquals("owner", result.getContent().get(0).getOwnerUsernameSnapshot());
        assertEquals("alice", result.getContent().get(0).getRequesterUsernameSnapshot());
        assertEquals("The Dispossessed", result.getContent().get(0).getRequestedBookTitle());
        assertEquals("William Gibson", result.getContent().get(0).getOfferedBookAuthor());
    }

    @Test
    void myIncomingShouldMapOldRowsWithNullParticipantSnapshots() {
        // arrange
        ExchangeRequest request = ExchangeRequest.builder()
                .id(21L)
                .requestedBookId(201L)
                .requestedBookTitle("A Fire Upon the Deep")
                .requestedBookAuthor("Vernor Vinge")
                .offeredBookId(301L)
                .offeredBookTitle("Use of Weapons")
                .offeredBookAuthor("Iain M. Banks")
                .ownerId(55L)
                .requesterId(42L)
                .status(ExchangeStatus.PENDING)
                .build();

        when(exchangeRepository.findAllByOwnerId(55L, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(request)));

        // act
        var result = exchangeService.myIncoming(new UserPrincipal(55L, "owner"), PageRequest.of(0, 20));

        // assert
        assertEquals(1, result.getTotalElements());
        assertEquals(21L, result.getContent().get(0).getId());
        assertNull(result.getContent().get(0).getOwnerUsernameSnapshot());
        assertNull(result.getContent().get(0).getRequesterUsernameSnapshot());
        assertEquals("A Fire Upon the Deep", result.getContent().get(0).getRequestedBookTitle());
    }

    @Test
    void completeShouldMarkBothBooksExchangedAfterSecondConfirmation() {
        // arrange
        ExchangeRequest request = ExchangeRequest.builder()
                .id(10L)
                .requestedBookId(100L)
                .offeredBookId(200L)
                .ownerId(55L)
                .requesterId(42L)
                .status(ExchangeStatus.COMPLETION_PENDING)
                .ownerCompletionConfirmedAt(LocalDateTime.now().minusMinutes(15))
                .build();

        when(exchangeRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(request));
        when(exchangeRepository.save(any(ExchangeRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // act
        ExchangeResponse response = exchangeService.complete(10L, new UserPrincipal(42L, "requester"));

        // assert
        assertNotNull(response);
        assertEquals(ExchangeStatus.COMPLETED, response.getStatus());
        assertNull(response.getOwnerUsernameSnapshot());
        assertEquals("requester", response.getRequesterUsernameSnapshot());
        assertNotNull(response.getOwnerCompletionConfirmedAt());
        assertNotNull(response.getRequesterCompletionConfirmedAt());

        verify(bookServiceClient).markExchanged(100L);
        verify(bookServiceClient).markExchanged(200L);
        verify(exchangeRepository).save(request);
        assertEquals(ExchangeStatus.COMPLETED, request.getStatus());
        assertEquals("requester", request.getRequesterUsernameSnapshot());
        assertNotNull(request.getRequesterCompletionConfirmedAt());
        verify(exchangeOutboxService).recordExchangeEvent(
                ExchangeEventType.EXCHANGE_REQUEST_COMPLETED,
                request,
                eventContext(42L, "requester", 42L)
        );
    }

    @Test
    void completeShouldBeIdempotentWhenSameParticipantConfirmsAgain() {
        // arrange
        LocalDateTime ownerConfirmedAt = LocalDateTime.now().minusMinutes(5);
        ExchangeRequest request = ExchangeRequest.builder()
                .id(10L)
                .requestedBookId(100L)
                .offeredBookId(200L)
                .ownerId(55L)
                .requesterId(42L)
                .status(ExchangeStatus.COMPLETION_PENDING)
                .ownerCompletionConfirmedAt(ownerConfirmedAt)
                .build();

        when(exchangeRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(request));

        // act
        ExchangeResponse response = exchangeService.complete(10L, new UserPrincipal(55L, "owner"));

        // assert
        assertEquals(ExchangeStatus.COMPLETION_PENDING, response.getStatus());
        assertEquals(ownerConfirmedAt, response.getOwnerCompletionConfirmedAt());
        assertNull(response.getRequesterCompletionConfirmedAt());
        verify(bookServiceClient, never()).markExchanged(anyLong());
        verify(exchangeRepository, never()).save(any(ExchangeRequest.class));
        verifyNoInteractions(exchangeOutboxService);
    }

    @Test
    void completeShouldRejectUserWhoIsNotExchangeParticipant() {
        // arrange
        ExchangeRequest request = ExchangeRequest.builder()
                .id(10L)
                .requestedBookId(100L)
                .offeredBookId(200L)
                .ownerId(55L)
                .requesterId(42L)
                .status(ExchangeStatus.ACCEPTED)
                .build();

        when(exchangeRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(request));

        // act
        ExchangeForbiddenException exception = assertThrows(
                ExchangeForbiddenException.class,
                () -> exchangeService.complete(10L, new UserPrincipal(99L, "intruder"))
        );

        // assert
        assertEquals("ONLY_EXCHANGE_PARTICIPANT_CAN_COMPLETE", exception.getCode());
        assertEquals("Only exchange participants can confirm completion.", exception.getMessage());
        verify(bookServiceClient, never()).markExchanged(anyLong());
        verify(exchangeRepository, never()).save(any(ExchangeRequest.class));
        verifyNoInteractions(exchangeOutboxService);
    }

    @Test
    void completeShouldMarkRepairRequiredWhenSecondBookCompletionFailsAfterFirstTransition() {
        // arrange
        LocalDateTime ownerConfirmedAt = LocalDateTime.now().minusMinutes(15);
        ExchangeRequest request = ExchangeRequest.builder()
                .id(10L)
                .requestedBookId(100L)
                .offeredBookId(200L)
                .ownerId(55L)
                .requesterId(42L)
                .status(ExchangeStatus.COMPLETION_PENDING)
                .ownerCompletionConfirmedAt(ownerConfirmedAt)
                .build();

        when(exchangeRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(request));
        when(bookServiceClient.markExchanged(100L)).thenReturn(new BookDto());
        when(bookServiceClient.markExchanged(200L))
                .thenThrow(new IllegalStateException("Offered book cannot be completed."));
        when(exchangeRepository.save(any(ExchangeRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // act
        ExchangeResponse response = exchangeService.complete(10L, new UserPrincipal(42L, "requester"));

        // assert
        assertEquals(ExchangeStatus.REPAIR_REQUIRED, response.getStatus());
        assertEquals(ExchangeStatus.REPAIR_REQUIRED, request.getStatus());
        assertTrue(response.getRepairReason().contains("PARTIAL_COMPLETION_FAILED"));
        assertTrue(response.getRepairReason().contains("Offered book cannot be completed."));
        assertNotNull(response.getRepairRequiredAt());
        assertEquals(0, response.getRepairAttempts());
        assertEquals(ownerConfirmedAt, response.getOwnerCompletionConfirmedAt());
        assertNotNull(response.getRequesterCompletionConfirmedAt());

        verify(bookServiceClient).markExchanged(100L);
        verify(bookServiceClient).markExchanged(200L);
        verify(exchangeRepository).save(request);
        verify(exchangeOutboxService).recordExchangeEvent(
                ExchangeEventType.EXCHANGE_REQUEST_REPAIR_REQUIRED,
                request,
                eventContext(42L, "requester", 42L)
        );
    }

    @Test
    void cancelShouldReleaseBothBooksForAcceptedRequest() {
        // arrange
        ExchangeRequest request = ExchangeRequest.builder()
                .id(10L)
                .requestedBookId(100L)
                .offeredBookId(200L)
                .ownerId(55L)
                .requesterId(42L)
                .status(ExchangeStatus.ACCEPTED)
                .build();

        when(exchangeRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(request));
        when(bookServiceClient.makeAvailable(100L)).thenReturn(new BookDto());
        when(bookServiceClient.makeAvailable(200L)).thenReturn(new BookDto());
        when(exchangeRepository.save(any(ExchangeRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // act
        ExchangeResponse response = exchangeService.cancel(10L, new UserPrincipal(42L, "alice"));

        // assert
        assertNotNull(response);
        assertEquals(ExchangeStatus.CANCELLED, response.getStatus());

        verify(bookServiceClient).makeAvailable(100L);
        verify(bookServiceClient).makeAvailable(200L);
        verify(exchangeRepository).save(request);
        assertEquals(ExchangeStatus.CANCELLED, request.getStatus());
        verify(exchangeOutboxService).recordExchangeEvent(
                ExchangeEventType.EXCHANGE_REQUEST_CANCELLED,
                request,
                eventContext(42L, "alice")
        );
    }

    @Test
    void cancelShouldRollbackFirstReleasedBookWhenSecondReleaseFails() {
        // arrange
        ExchangeRequest request = ExchangeRequest.builder()
                .id(10L)
                .requestedBookId(100L)
                .offeredBookId(200L)
                .ownerId(55L)
                .requesterId(42L)
                .status(ExchangeStatus.ACCEPTED)
                .build();

        when(exchangeRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(request));
        when(bookServiceClient.makeAvailable(100L)).thenReturn(new BookDto());
        when(bookServiceClient.makeAvailable(200L))
                .thenThrow(new IllegalStateException("Offered book cannot be released."));
        when(bookServiceClient.reserve(100L)).thenReturn(new BookDto());

        // act
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> exchangeService.cancel(10L, new UserPrincipal(42L, "alice"))
        );

        // assert
        assertEquals("Offered book cannot be released.", exception.getMessage());

        verify(bookServiceClient).makeAvailable(100L);
        verify(bookServiceClient).makeAvailable(200L);
        verify(bookServiceClient).reserve(100L);
        verify(exchangeRepository, never()).save(any(ExchangeRequest.class));
        assertEquals(ExchangeStatus.ACCEPTED, request.getStatus());
        verifyNoInteractions(exchangeOutboxService);
    }

    @Test
    void cancelShouldMarkRepairRequiredWhenReleaseRollbackFails() {
        // arrange
        ExchangeRequest request = ExchangeRequest.builder()
                .id(10L)
                .requestedBookId(100L)
                .offeredBookId(200L)
                .ownerId(55L)
                .requesterId(42L)
                .status(ExchangeStatus.ACCEPTED)
                .build();

        when(exchangeRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(request));
        when(bookServiceClient.makeAvailable(100L)).thenReturn(new BookDto());
        when(bookServiceClient.makeAvailable(200L))
                .thenThrow(new IllegalStateException("Offered book cannot be released."));
        when(bookServiceClient.reserve(100L))
                .thenThrow(new IllegalStateException("Requested book could not be reserved again."));
        when(exchangeRepository.save(any(ExchangeRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // act
        ExchangeResponse response = exchangeService.cancel(10L, new UserPrincipal(42L, "alice"));

        // assert
        assertEquals(ExchangeStatus.REPAIR_REQUIRED, response.getStatus());
        assertEquals(ExchangeStatus.REPAIR_REQUIRED, request.getStatus());
        assertTrue(response.getRepairReason().contains("CANCEL_RELEASE_COMPENSATION_FAILED"));
        assertTrue(response.getRepairReason().contains("rollback failed for books=[100]"));
        assertNotNull(response.getRepairRequiredAt());
        assertEquals(0, response.getRepairAttempts());

        verify(bookServiceClient).makeAvailable(100L);
        verify(bookServiceClient).makeAvailable(200L);
        verify(bookServiceClient).reserve(100L);
        verify(exchangeRepository).save(request);
        verify(exchangeOutboxService).recordExchangeEvent(
                ExchangeEventType.EXCHANGE_REQUEST_REPAIR_REQUIRED,
                request,
                eventContext(42L, "alice", 42L)
        );
    }

    @Test
    void repairRequiredShouldRejectOrdinaryParticipantActions() {
        assertRepairRequiredActionRejected(() -> exchangeService.accept(10L, new UserPrincipal(55L, "owner")));
        assertRepairRequiredActionRejected(() -> exchangeService.decline(10L, new UserPrincipal(55L, "owner")));
        assertRepairRequiredActionRejected(() -> exchangeService.cancel(10L, new UserPrincipal(42L, "requester")));
        assertRepairRequiredActionRejected(() -> exchangeService.complete(10L, new UserPrincipal(42L, "requester")));

        verify(bookServiceClient, never()).reserve(anyLong());
        verify(bookServiceClient, never()).makeAvailable(anyLong());
        verify(bookServiceClient, never()).markExchanged(anyLong());
        verify(exchangeRepository, never()).save(any(ExchangeRequest.class));
        verifyNoInteractions(exchangeOutboxService);
    }

    @Test
    void repairShouldCompletePartialCompletionInconsistency() {
        // arrange
        LocalDateTime repairRequiredAt = LocalDateTime.now().minusMinutes(5);
        ExchangeRequest request = ExchangeRequest.builder()
                .id(10L)
                .requestedBookId(100L)
                .offeredBookId(200L)
                .ownerId(55L)
                .requesterId(42L)
                .status(ExchangeStatus.REPAIR_REQUIRED)
                .ownerCompletionConfirmedAt(LocalDateTime.now().minusMinutes(15))
                .requesterCompletionConfirmedAt(LocalDateTime.now().minusMinutes(10))
                .repairReason("PARTIAL_COMPLETION_FAILED: books marked EXCHANGED=[100]")
                .repairRequiredAt(repairRequiredAt)
                .repairAttempts(0)
                .build();

        when(exchangeRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(request));
        when(bookServiceClient.getBook(100L)).thenReturn(book(100L, "EXCHANGED", "PRIVATE"));
        when(bookServiceClient.getBook(200L)).thenReturn(book(200L, "RESERVED", "PUBLIC"));
        when(bookServiceClient.markExchanged(200L)).thenReturn(book(200L, "EXCHANGED", "PRIVATE"));
        when(exchangeRepository.save(any(ExchangeRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // act
        ExchangeResponse response = exchangeService.repair(10L, new UserPrincipal(99L, "admin"));

        // assert
        assertEquals(ExchangeStatus.COMPLETED, response.getStatus());
        assertEquals(ExchangeStatus.COMPLETED, request.getStatus());
        assertEquals(1, response.getRepairAttempts());
        assertNotNull(response.getLastRepairAttemptAt());
        assertEquals(repairRequiredAt, response.getRepairRequiredAt());

        verify(bookServiceClient).getBook(100L);
        verify(bookServiceClient).getBook(200L);
        verify(bookServiceClient, never()).markExchanged(100L);
        verify(bookServiceClient).markExchanged(200L);
        verify(exchangeRepository).save(request);
        verify(exchangeOutboxService).recordExchangeEventIfAbsent(
                ExchangeEventType.EXCHANGE_REQUEST_COMPLETED,
                request,
                eventContext(99L, "admin")
        );
    }

    @Test
    void repairShouldIncreaseAttemptsAndLeaveRepairRequiredWhenRemoteTransitionFails() {
        // arrange
        ExchangeRequest request = ExchangeRequest.builder()
                .id(10L)
                .requestedBookId(100L)
                .offeredBookId(200L)
                .ownerId(55L)
                .requesterId(42L)
                .status(ExchangeStatus.REPAIR_REQUIRED)
                .ownerCompletionConfirmedAt(LocalDateTime.now().minusMinutes(15))
                .requesterCompletionConfirmedAt(LocalDateTime.now().minusMinutes(10))
                .repairReason("PARTIAL_COMPLETION_FAILED: books marked EXCHANGED=[]")
                .repairRequiredAt(LocalDateTime.now().minusMinutes(5))
                .repairAttempts(1)
                .build();

        when(exchangeRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(request));
        when(bookServiceClient.getBook(100L)).thenReturn(book(100L, "RESERVED", "PUBLIC"));
        when(bookServiceClient.markExchanged(100L))
                .thenThrow(new IllegalStateException("Book cannot be marked EXCHANGED during repair."));
        when(exchangeRepository.save(any(ExchangeRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // act
        ExchangeConflictException exception = assertThrows(
                ExchangeConflictException.class,
                () -> exchangeService.repair(10L, new UserPrincipal(99L, "admin"))
        );

        // assert
        assertEquals("EXCHANGE_REPAIR_FAILED", exception.getCode());
        assertEquals(ExchangeStatus.REPAIR_REQUIRED, request.getStatus());
        assertEquals(2, request.getRepairAttempts());
        assertNotNull(request.getLastRepairAttemptAt());
        assertTrue(request.getRepairReason().contains("REPAIR_ATTEMPT_FAILED"));
        assertTrue(request.getRepairReason().contains("Book cannot be marked EXCHANGED during repair."));

        verify(bookServiceClient).getBook(100L);
        verify(bookServiceClient).markExchanged(100L);
        verify(bookServiceClient, never()).getBook(200L);
        verify(exchangeRepository).save(request);
        verifyNoInteractions(exchangeOutboxService);
    }

    @Test
    void repairShouldBeIdempotentAfterSuccessfulRepair() {
        // arrange
        ExchangeRequest request = ExchangeRequest.builder()
                .id(10L)
                .requestedBookId(100L)
                .offeredBookId(200L)
                .ownerId(55L)
                .requesterId(42L)
                .status(ExchangeStatus.COMPLETED)
                .repairReason("PARTIAL_COMPLETION_FAILED: books marked EXCHANGED=[100]")
                .repairRequiredAt(LocalDateTime.now().minusMinutes(5))
                .repairAttempts(1)
                .lastRepairAttemptAt(LocalDateTime.now().minusMinutes(1))
                .build();

        when(exchangeRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(request));

        // act
        ExchangeResponse response = exchangeService.repair(10L, new UserPrincipal(99L, "admin"));

        // assert
        assertEquals(ExchangeStatus.COMPLETED, response.getStatus());
        assertEquals(1, response.getRepairAttempts());

        verifyNoInteractions(bookServiceClient);
        verify(exchangeRepository, never()).save(any(ExchangeRequest.class));
        verifyNoInteractions(exchangeOutboxService);
    }

    @Test
    void cancelShouldRejectWhenCompletionWasAlreadyConfirmed() {
        // arrange
        ExchangeRequest request = ExchangeRequest.builder()
                .id(10L)
                .requestedBookId(100L)
                .offeredBookId(200L)
                .ownerId(55L)
                .requesterId(42L)
                .status(ExchangeStatus.COMPLETION_PENDING)
                .ownerCompletionConfirmedAt(LocalDateTime.now().minusMinutes(10))
                .build();

        when(exchangeRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(request));

        // act
        ExchangeConflictException exception = assertThrows(
                ExchangeConflictException.class,
                () -> exchangeService.cancel(10L, new UserPrincipal(42L, "alice"))
        );

        // assert
        assertEquals("INVALID_EXCHANGE_STATUS_TRANSITION", exception.getCode());
        assertEquals("Only PENDING or ACCEPTED request without completion confirmation can be canceled.", exception.getMessage());
        verify(bookServiceClient, never()).makeAvailable(anyLong());
        verify(exchangeRepository, never()).save(any(ExchangeRequest.class));
        verifyNoInteractions(exchangeOutboxService);
    }

    private HttpClientErrorException bookServiceException(HttpStatus status) {
        return HttpClientErrorException.create(status, status.getReasonPhrase(), HttpHeaders.EMPTY, new byte[0], null);
    }

    private BookDto book(Long id, String status, String visibility) {
        BookDto book = new BookDto();
        book.setId(id);
        book.setStatus(status);
        book.setVisibility(visibility);
        return book;
    }

    private BookDto book(Long id,
                         Long ownerId,
                         String title,
                         String author,
                         String status,
                         String visibility) {
        BookDto book = book(id, status, visibility);
        book.setOwnerId(ownerId);
        book.setTitle(title);
        book.setAuthor(author);
        return book;
    }

    private void assertRepairRequiredActionRejected(Runnable action) {
        ExchangeRequest request = ExchangeRequest.builder()
                .id(10L)
                .requestedBookId(100L)
                .offeredBookId(200L)
                .ownerId(55L)
                .requesterId(42L)
                .status(ExchangeStatus.REPAIR_REQUIRED)
                .repairReason("manual repair required")
                .repairRequiredAt(LocalDateTime.now().minusMinutes(5))
                .repairAttempts(0)
                .build();

        when(exchangeRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(request));

        ExchangeConflictException exception = assertThrows(ExchangeConflictException.class, action::run);
        assertEquals("EXCHANGE_REPAIR_REQUIRED", exception.getCode());
        assertEquals("Exchange requires manual repair before participant actions can continue.", exception.getMessage());
    }

    private ExchangeEventContext eventContext(Long initiatorUserId, String initiatorUsername) {
        return eventContext(initiatorUserId, initiatorUsername, null);
    }

    private ExchangeEventContext eventContext(Long initiatorUserId,
                                              String initiatorUsername,
                                              Long completedByUserId) {
        return ExchangeEventContext.builder()
                .initiatorUserId(initiatorUserId)
                .initiatorUsername(initiatorUsername)
                .completedByUserId(completedByUserId)
                .build();
    }
}
