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
                List.of(ExchangeStatus.PENDING, ExchangeStatus.ACCEPTED, ExchangeStatus.COMPLETION_PENDING)
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
        assertEquals(ExchangeStatus.PENDING, savedRequest.getStatus());
        assertEquals("I would like to exchange this book.", savedRequest.getMessage());

        verify(exchangeOutboxService).recordExchangeEvent(
                ExchangeEventType.EXCHANGE_REQUEST_CREATED,
                savedRequest,
                eventContext(42L, "alice")
        );
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
                List.of(ExchangeStatus.PENDING, ExchangeStatus.ACCEPTED, ExchangeStatus.COMPLETION_PENDING)
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
                List.of(ExchangeStatus.PENDING, ExchangeStatus.ACCEPTED, ExchangeStatus.COMPLETION_PENDING)
        )).thenReturn(List.of(request));
        when(exchangeRepository.existsAnotherByStatusesAndBookIds(
                10L,
                List.of(100L, 200L),
                List.of(ExchangeStatus.ACCEPTED, ExchangeStatus.COMPLETION_PENDING)
        )).thenReturn(false);
        when(exchangeRepository.save(any(ExchangeRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // act
        ExchangeResponse response = exchangeService.accept(10L, new UserPrincipal(55L, "owner"));

        // assert
        assertNotNull(response);
        assertEquals(ExchangeStatus.ACCEPTED, response.getStatus());

        verify(exchangeRepository).lockAllActiveByBookIds(
                List.of(100L, 200L),
                List.of(ExchangeStatus.PENDING, ExchangeStatus.ACCEPTED, ExchangeStatus.COMPLETION_PENDING)
        );
        verify(bookServiceClient).reserve(100L);
        verify(bookServiceClient).reserve(200L);
        verify(exchangeRepository, never()).saveAll(anyList());
        verify(exchangeRepository).save(request);
        assertEquals(ExchangeStatus.ACCEPTED, request.getStatus());
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
                List.of(ExchangeStatus.PENDING, ExchangeStatus.ACCEPTED, ExchangeStatus.COMPLETION_PENDING)
        )).thenReturn(List.of(request, conflictingRequest));
        when(exchangeRepository.existsAnotherByStatusesAndBookIds(
                10L,
                List.of(100L, 200L),
                List.of(ExchangeStatus.ACCEPTED, ExchangeStatus.COMPLETION_PENDING)
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
                List.of(ExchangeStatus.ACCEPTED, ExchangeStatus.COMPLETION_PENDING)
        )).thenReturn(true);
        when(exchangeRepository.lockAllActiveByBookIds(
                List.of(100L, 200L),
                List.of(ExchangeStatus.PENDING, ExchangeStatus.ACCEPTED, ExchangeStatus.COMPLETION_PENDING)
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
                List.of(ExchangeStatus.PENDING, ExchangeStatus.ACCEPTED, ExchangeStatus.COMPLETION_PENDING)
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
                List.of(ExchangeStatus.ACCEPTED, ExchangeStatus.COMPLETION_PENDING)
        )).thenReturn(false);

        when(bookServiceClient.reserve(100L)).thenReturn(new BookDto());
        when(bookServiceClient.reserve(200L)).thenThrow(new IllegalStateException("Offered book cannot be reserved."));
        when(exchangeRepository.lockAllActiveByBookIds(
                List.of(100L, 200L),
                List.of(ExchangeStatus.PENDING, ExchangeStatus.ACCEPTED, ExchangeStatus.COMPLETION_PENDING)
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
        assertNotNull(response.getOwnerCompletionConfirmedAt());
        assertNull(response.getRequesterCompletionConfirmedAt());
        verify(bookServiceClient, never()).markExchanged(anyLong());
        verify(exchangeRepository).save(request);
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
        assertEquals("The Dispossessed", result.getContent().get(0).getRequestedBookTitle());
        assertEquals("William Gibson", result.getContent().get(0).getOfferedBookAuthor());
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
        assertNotNull(response.getOwnerCompletionConfirmedAt());
        assertNotNull(response.getRequesterCompletionConfirmedAt());

        verify(bookServiceClient).markExchanged(100L);
        verify(bookServiceClient).markExchanged(200L);
        verify(exchangeRepository).save(request);
        assertEquals(ExchangeStatus.COMPLETED, request.getStatus());
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
    void completeShouldNotSaveRequestWhenSecondBookCompletionFails() {
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
        when(bookServiceClient.markExchanged(100L)).thenReturn(new BookDto());
        when(bookServiceClient.markExchanged(200L))
                .thenThrow(new IllegalStateException("Offered book cannot be completed."));

        // act
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> exchangeService.complete(10L, new UserPrincipal(42L, "requester"))
        );

        // assert
        assertEquals("Offered book cannot be completed.", exception.getMessage());

        verify(bookServiceClient).markExchanged(100L);
        verify(bookServiceClient).markExchanged(200L);
        verify(exchangeRepository, never()).save(any(ExchangeRequest.class));
        assertEquals(ExchangeStatus.COMPLETION_PENDING, request.getStatus());
        assertNull(request.getRequesterCompletionConfirmedAt());
        verifyNoInteractions(exchangeOutboxService);
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
