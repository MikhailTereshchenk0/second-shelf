package com.secondshelf.exchangeservice.service;

import com.secondshelf.exchangeservice.client.BookServiceClient;
import com.secondshelf.exchangeservice.client.UserServiceClient;
import com.secondshelf.exchangeservice.client.dto.BookDto;
import com.secondshelf.exchangeservice.client.dto.UserContactDto;
import com.secondshelf.exchangeservice.dto.CreateExchangeRequest;
import com.secondshelf.exchangeservice.dto.ExchangeResponse;
import com.secondshelf.exchangeservice.dto.OwnerOfferRequest;
import com.secondshelf.exchangeservice.entity.ExchangeRequest;
import com.secondshelf.exchangeservice.entity.ExchangeStatus;
import com.secondshelf.exchangeservice.exception.ExchangeBadRequestException;
import com.secondshelf.exchangeservice.exception.ExchangeConflictException;
import com.secondshelf.exchangeservice.exception.ExchangeForbiddenException;
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
    private UserServiceClient userServiceClient;

    @Mock
    private ExchangeOutboxService exchangeOutboxService;

    @InjectMocks
    private ExchangeService exchangeService;

    @Test
    void createShouldSavePendingRequestWithoutOfferedBook() {
        CreateExchangeRequest request = new CreateExchangeRequest();
        request.setRequestedBookId(100L);
        request.setOfferedBookId(200L);
        request.setMessage("Can meet near the station.");

        when(bookServiceClient.getBook(100L))
                .thenReturn(book(100L, 55L, "The Left Hand of Darkness", "Ursula K. Le Guin"));
        when(exchangeRepository.existsByRequesterIdAndRequestedBookIdAndStatusIn(
                42L,
                100L,
                activeStatuses()
        )).thenReturn(false);
        when(exchangeRepository.save(any(ExchangeRequest.class))).thenAnswer(invocation -> {
            ExchangeRequest saved = invocation.getArgument(0);
            saved.setId(1L);
            return saved;
        });

        ExchangeResponse response = exchangeService.create(request, new UserPrincipal(42L, "alice"));

        assertEquals(1L, response.getId());
        assertEquals(100L, response.getRequestedBookId());
        assertNull(response.getOfferedBookId());
        assertNull(response.getOfferedBookTitle());
        assertEquals(55L, response.getOwnerId());
        assertEquals(42L, response.getRequesterId());
        assertEquals("alice", response.getRequesterUsernameSnapshot());
        assertEquals(ExchangeStatus.PENDING, response.getStatus());
        assertNull(response.getOwnerPhoneNumber());

        ArgumentCaptor<ExchangeRequest> exchangeCaptor = ArgumentCaptor.forClass(ExchangeRequest.class);
        verify(exchangeRepository).save(exchangeCaptor.capture());
        ExchangeRequest savedRequest = exchangeCaptor.getValue();
        assertEquals(100L, savedRequest.getRequestedBookId());
        assertNull(savedRequest.getOfferedBookId());
        assertEquals("Can meet near the station.", savedRequest.getMessage());

        verify(bookServiceClient).getBook(100L);
        verify(bookServiceClient, never()).getBook(200L);
        verify(exchangeOutboxService).recordExchangeEvent(
                ExchangeEventType.EXCHANGE_REQUEST_CREATED,
                savedRequest,
                eventContext(42L, "alice")
        );
    }

    @Test
    void createShouldRejectDuplicateActiveRequestForSameRequestedBook() {
        CreateExchangeRequest request = new CreateExchangeRequest();
        request.setRequestedBookId(100L);

        when(bookServiceClient.getBook(100L))
                .thenReturn(book(100L, 55L, "The Left Hand of Darkness", "Ursula K. Le Guin"));
        when(exchangeRepository.existsByRequesterIdAndRequestedBookIdAndStatusIn(
                42L,
                100L,
                activeStatuses()
        )).thenReturn(true);

        ExchangeConflictException exception = assertThrows(
                ExchangeConflictException.class,
                () -> exchangeService.create(request, new UserPrincipal(42L, "alice"))
        );

        assertEquals("DUPLICATE_ACTIVE_EXCHANGE_REQUEST", exception.getCode());
        verify(exchangeRepository, never()).save(any(ExchangeRequest.class));
        verifyNoInteractions(exchangeOutboxService);
    }

    @Test
    void createShouldReturnExistingExchangeForIdenticalIdempotentReplayIgnoringDeprecatedOfferedBook() {
        CreateExchangeRequest request = new CreateExchangeRequest();
        request.setRequestedBookId(100L);
        request.setOfferedBookId(999L);
        request.setMessage("Same message.");

        ExchangeRequest existingRequest = ExchangeRequest.builder()
                .id(77L)
                .requestedBookId(100L)
                .ownerId(55L)
                .requesterId(42L)
                .status(ExchangeStatus.PENDING)
                .message("Same message.")
                .idempotencyKeyHash("hash")
                .build();

        when(exchangeRepository.findByRequesterIdAndIdempotencyKeyHash(eq(42L), anyString()))
                .thenReturn(Optional.of(existingRequest));

        ExchangeResponse response = exchangeService.create(request, new UserPrincipal(42L, "alice"), "retry-key-123456");

        assertEquals(77L, response.getId());
        assertEquals(ExchangeStatus.PENDING, response.getStatus());
        verifyNoInteractions(bookServiceClient);
        verify(exchangeRepository, never()).save(any(ExchangeRequest.class));
        verifyNoInteractions(exchangeOutboxService);
    }

    @Test
    void offerShouldMovePendingRequestToOwnerOfferedAndExposeRequesterContactToOwner() {
        ExchangeRequest request = pendingRequest();
        OwnerOfferRequest offerRequest = new OwnerOfferRequest();
        offerRequest.setOfferedBookId(200L);

        BookDto offeredBook = book(200L, 42L, "Dune", "Frank Herbert");
        when(exchangeRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(request));
        when(bookServiceClient.getBook(200L)).thenReturn(offeredBook);
        when(bookServiceClient.getAvailablePublicBooksByOwner(42L)).thenReturn(List.of(offeredBook));
        when(userServiceClient.getContact(42L)).thenReturn(contact(42L, "alice", "+375291112233"));
        when(exchangeRepository.save(any(ExchangeRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ExchangeResponse response = exchangeService.offer(10L, offerRequest, new UserPrincipal(55L, "owner"));

        assertEquals(ExchangeStatus.OWNER_OFFERED, response.getStatus());
        assertEquals(200L, response.getOfferedBookId());
        assertEquals("Dune", response.getOfferedBookTitle());
        assertEquals("owner", response.getOwnerUsernameSnapshot());
        assertEquals("+375291112233", response.getRequesterPhoneNumber());
        assertEquals(1, response.getRequesterAvailableBooks().size());
        assertNull(response.getOwnerPhoneNumber());

        verify(exchangeOutboxService).recordExchangeEvent(
                ExchangeEventType.EXCHANGE_REQUEST_OWNER_OFFERED,
                request,
                eventContext(55L, "owner")
        );
    }

    @Test
    void offerShouldRejectRequestedBookAsCounterOffer() {
        ExchangeRequest request = pendingRequest();
        OwnerOfferRequest offerRequest = new OwnerOfferRequest();
        offerRequest.setOfferedBookId(100L);

        when(exchangeRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(request));

        ExchangeBadRequestException exception = assertThrows(
                ExchangeBadRequestException.class,
                () -> exchangeService.offer(10L, offerRequest, new UserPrincipal(55L, "owner"))
        );

        assertEquals("INVALID_EXCHANGE_BOOK_SELECTION", exception.getCode());
        verify(bookServiceClient, never()).getBook(anyLong());
        verify(exchangeRepository, never()).save(any(ExchangeRequest.class));
    }

    @Test
    void offerShouldRejectBookNotOwnedByRequester() {
        ExchangeRequest request = pendingRequest();
        OwnerOfferRequest offerRequest = new OwnerOfferRequest();
        offerRequest.setOfferedBookId(200L);

        when(exchangeRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(request));
        when(bookServiceClient.getBook(200L)).thenReturn(book(200L, 99L, "Dune", "Frank Herbert"));

        ExchangeForbiddenException exception = assertThrows(
                ExchangeForbiddenException.class,
                () -> exchangeService.offer(10L, offerRequest, new UserPrincipal(55L, "owner"))
        );

        assertEquals("OFFERED_BOOK_NOT_OWNED_BY_REQUESTER", exception.getCode());
        verify(exchangeRepository, never()).save(any(ExchangeRequest.class));
    }

    @Test
    void acceptShouldReserveBooksDeclineConflictsAndExposeOwnerPhoneToRequester() {
        ExchangeRequest request = ownerOfferedRequest();
        ExchangeRequest pendingConflict = ExchangeRequest.builder()
                .id(11L)
                .requestedBookId(100L)
                .ownerId(55L)
                .requesterId(77L)
                .status(ExchangeStatus.PENDING)
                .build();
        ExchangeRequest offeredConflict = ExchangeRequest.builder()
                .id(12L)
                .requestedBookId(300L)
                .offeredBookId(200L)
                .ownerId(88L)
                .requesterId(42L)
                .status(ExchangeStatus.OWNER_OFFERED)
                .build();

        when(exchangeRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(request));
        when(bookServiceClient.getBook(100L)).thenReturn(book(100L, 55L, "The Left Hand of Darkness", "Ursula K. Le Guin"));
        when(bookServiceClient.getBook(200L)).thenReturn(book(200L, 42L, "Dune", "Frank Herbert"));
        when(exchangeRepository.lockAllActiveByBookIds(List.of(100L, 200L), activeStatuses()))
                .thenReturn(List.of(request, pendingConflict, offeredConflict));
        when(exchangeRepository.existsAnotherByStatusesAndBookIds(10L, List.of(100L, 200L), reservedStatuses()))
                .thenReturn(false);
        when(exchangeRepository.save(any(ExchangeRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(exchangeRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        when(bookServiceClient.reserve(100L)).thenReturn(new BookDto());
        when(bookServiceClient.reserve(200L)).thenReturn(new BookDto());
        when(userServiceClient.getContact(55L)).thenReturn(contact(55L, "owner", "+375292223344"));

        ExchangeResponse response = exchangeService.accept(10L, new UserPrincipal(42L, "alice"));

        assertEquals(ExchangeStatus.ACCEPTED, response.getStatus());
        assertEquals("alice", response.getRequesterUsernameSnapshot());
        assertEquals("+375292223344", response.getOwnerPhoneNumber());
        assertNull(response.getRequesterPhoneNumber());
        assertEquals(ExchangeStatus.DECLINED, pendingConflict.getStatus());
        assertEquals(ExchangeStatus.DECLINED, offeredConflict.getStatus());

        verify(bookServiceClient).reserve(100L);
        verify(bookServiceClient).reserve(200L);
        verify(exchangeRepository).saveAll(List.of(pendingConflict, offeredConflict));
        verify(exchangeOutboxService).recordExchangeEvent(
                ExchangeEventType.EXCHANGE_REQUEST_ACCEPTED,
                request,
                eventContext(42L, "alice")
        );
        verify(exchangeOutboxService).recordExchangeEvent(
                ExchangeEventType.EXCHANGE_REQUEST_DECLINED,
                pendingConflict,
                eventContext(42L, "alice")
        );
        verify(exchangeOutboxService).recordExchangeEvent(
                ExchangeEventType.EXCHANGE_REQUEST_DECLINED,
                offeredConflict,
                eventContext(42L, "alice")
        );
    }

    @Test
    void acceptShouldRejectOwnerTryingToFinalizeOffer() {
        ExchangeRequest request = ownerOfferedRequest();
        when(exchangeRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(request));

        ExchangeForbiddenException exception = assertThrows(
                ExchangeForbiddenException.class,
                () -> exchangeService.accept(10L, new UserPrincipal(55L, "owner"))
        );

        assertEquals("ONLY_REQUESTER_CAN_ACCEPT", exception.getCode());
        verify(bookServiceClient, never()).reserve(anyLong());
        verify(exchangeRepository, never()).save(any(ExchangeRequest.class));
    }

    @Test
    void acceptShouldRejectPendingRequestWithoutOwnerOffer() {
        ExchangeRequest request = pendingRequest();
        when(exchangeRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(request));

        ExchangeConflictException exception = assertThrows(
                ExchangeConflictException.class,
                () -> exchangeService.accept(10L, new UserPrincipal(42L, "alice"))
        );

        assertEquals("INVALID_EXCHANGE_STATUS_TRANSITION", exception.getCode());
        assertEquals("Only OWNER_OFFERED request can be accepted by requester.", exception.getMessage());
        verify(bookServiceClient, never()).reserve(anyLong());
    }

    @Test
    void declineShouldAllowOwnerToRejectPendingRequestWithoutRevealingOwnerPhone() {
        ExchangeRequest request = pendingRequest();
        when(exchangeRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(request));
        when(userServiceClient.getContact(42L)).thenReturn(contact(42L, "alice", "+375291112233"));
        when(bookServiceClient.getAvailablePublicBooksByOwner(42L)).thenReturn(List.of());
        when(exchangeRepository.save(any(ExchangeRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ExchangeResponse response = exchangeService.decline(10L, new UserPrincipal(55L, "owner"));

        assertEquals(ExchangeStatus.DECLINED, response.getStatus());
        assertEquals("+375291112233", response.getRequesterPhoneNumber());
        assertNull(response.getOwnerPhoneNumber());
        verify(exchangeOutboxService).recordExchangeEvent(
                ExchangeEventType.EXCHANGE_REQUEST_DECLINED,
                request,
                eventContext(55L, "owner")
        );
    }

    @Test
    void declineOfferShouldCancelOwnerOfferWithoutRevealingOwnerPhone() {
        ExchangeRequest request = ownerOfferedRequest();
        when(exchangeRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(request));
        when(exchangeRepository.save(any(ExchangeRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ExchangeResponse response = exchangeService.declineOffer(10L, new UserPrincipal(42L, "alice"));

        assertEquals(ExchangeStatus.CANCELLED, response.getStatus());
        assertNull(response.getOwnerPhoneNumber());
        verify(userServiceClient, never()).getContact(55L);
        verify(exchangeOutboxService).recordExchangeEvent(
                ExchangeEventType.EXCHANGE_REQUEST_CANCELLED,
                request,
                eventContext(42L, "alice")
        );
    }

    @Test
    void cancelShouldReleaseBothBooksForAcceptedRequest() {
        ExchangeRequest request = ownerOfferedRequest();
        request.setStatus(ExchangeStatus.ACCEPTED);

        when(exchangeRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(request));
        when(bookServiceClient.makeAvailable(100L)).thenReturn(new BookDto());
        when(bookServiceClient.makeAvailable(200L)).thenReturn(new BookDto());
        when(exchangeRepository.save(any(ExchangeRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ExchangeResponse response = exchangeService.cancel(10L, new UserPrincipal(42L, "alice"));

        assertEquals(ExchangeStatus.CANCELLED, response.getStatus());
        verify(bookServiceClient).makeAvailable(100L);
        verify(bookServiceClient).makeAvailable(200L);
        verify(exchangeOutboxService).recordExchangeEvent(
                ExchangeEventType.EXCHANGE_REQUEST_CANCELLED,
                request,
                eventContext(42L, "alice")
        );
    }

    @Test
    void myIncomingShouldIncludeRequesterPhoneAndAvailablePublicBooks() {
        ExchangeRequest request = pendingRequest();
        BookDto requesterBook = book(200L, 42L, "Dune", "Frank Herbert");

        when(exchangeRepository.findAllByOwnerId(55L, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(request)));
        when(userServiceClient.getContact(42L)).thenReturn(contact(42L, "alice", "+375291112233"));
        when(bookServiceClient.getAvailablePublicBooksByOwner(42L)).thenReturn(List.of(requesterBook));

        var result = exchangeService.myIncoming(new UserPrincipal(55L, "owner"), PageRequest.of(0, 20));

        assertEquals(1, result.getTotalElements());
        ExchangeResponse response = result.getContent().get(0);
        assertEquals("+375291112233", response.getRequesterPhoneNumber());
        assertNull(response.getOwnerPhoneNumber());
        assertEquals(1, response.getRequesterAvailableBooks().size());
        assertEquals(200L, response.getRequesterAvailableBooks().get(0).getId());
    }

    private ExchangeRequest pendingRequest() {
        return ExchangeRequest.builder()
                .id(10L)
                .requestedBookId(100L)
                .requestedBookTitle("The Left Hand of Darkness")
                .requestedBookAuthor("Ursula K. Le Guin")
                .ownerId(55L)
                .requesterId(42L)
                .requesterUsernameSnapshot("alice")
                .status(ExchangeStatus.PENDING)
                .message("Can meet near the station.")
                .build();
    }

    private ExchangeRequest ownerOfferedRequest() {
        ExchangeRequest request = pendingRequest();
        request.setOfferedBookId(200L);
        request.setOfferedBookTitle("Dune");
        request.setOfferedBookAuthor("Frank Herbert");
        request.setOwnerUsernameSnapshot("owner");
        request.setStatus(ExchangeStatus.OWNER_OFFERED);
        return request;
    }

    private BookDto book(Long id, Long ownerId, String title, String author) {
        BookDto book = new BookDto();
        book.setId(id);
        book.setOwnerId(ownerId);
        book.setTitle(title);
        book.setAuthor(author);
        book.setVisibility("PUBLIC");
        book.setStatus("AVAILABLE");
        return book;
    }

    private UserContactDto contact(Long id, String username, String phoneNumber) {
        UserContactDto contact = new UserContactDto();
        contact.setId(id);
        contact.setUsername(username);
        contact.setPhoneNumber(phoneNumber);
        return contact;
    }

    private List<ExchangeStatus> activeStatuses() {
        return List.of(
                ExchangeStatus.PENDING,
                ExchangeStatus.OWNER_OFFERED,
                ExchangeStatus.ACCEPTED,
                ExchangeStatus.COMPLETION_PENDING,
                ExchangeStatus.REPAIR_REQUIRED
        );
    }

    private List<ExchangeStatus> reservedStatuses() {
        return List.of(
                ExchangeStatus.ACCEPTED,
                ExchangeStatus.COMPLETION_PENDING,
                ExchangeStatus.REPAIR_REQUIRED
        );
    }

    private ExchangeEventContext eventContext(Long initiatorUserId, String initiatorUsername) {
        return ExchangeEventContext.builder()
                .initiatorUserId(initiatorUserId)
                .initiatorUsername(initiatorUsername)
                .build();
    }
}
