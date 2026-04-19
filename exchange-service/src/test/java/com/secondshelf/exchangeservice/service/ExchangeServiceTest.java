package com.secondshelf.exchangeservice.service;

import com.secondshelf.exchangeservice.client.BookServiceClient;
import com.secondshelf.exchangeservice.client.dto.BookDto;
import com.secondshelf.exchangeservice.dto.CreateExchangeRequest;
import com.secondshelf.exchangeservice.dto.ExchangeResponse;
import com.secondshelf.exchangeservice.entity.ExchangeRequest;
import com.secondshelf.exchangeservice.entity.ExchangeStatus;
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
                List.of(ExchangeStatus.PENDING, ExchangeStatus.ACCEPTED)
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
        assertEquals(200L, response.getOfferedBookId());
        assertEquals(55L, response.getOwnerId());
        assertEquals(42L, response.getRequesterId());
        assertEquals(ExchangeStatus.PENDING, response.getStatus());
        assertEquals("I would like to exchange this book.", response.getMessage());

        verify(exchangeRepository).save(exchangeCaptor.capture());

        ExchangeRequest savedRequest = exchangeCaptor.getValue();
        assertEquals(100L, savedRequest.getRequestedBookId());
        assertEquals(200L, savedRequest.getOfferedBookId());
        assertEquals(55L, savedRequest.getOwnerId());
        assertEquals(42L, savedRequest.getRequesterId());
        assertEquals(ExchangeStatus.PENDING, savedRequest.getStatus());
        assertEquals("I would like to exchange this book.", savedRequest.getMessage());
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
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> exchangeService.create(request, new UserPrincipal(42L, "alice"))
        );

        // assert
        assertEquals("You cannot request exchange for your own book.", exception.getMessage());
        verify(exchangeRepository, never()).save(any(ExchangeRequest.class));
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
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> exchangeService.create(request, new UserPrincipal(42L, "alice"))
        );

        // assert
        assertEquals("Requested book must be public.", exception.getMessage());
        verify(exchangeRepository, never()).save(any(ExchangeRequest.class));
    }

    @Test
    void createShouldRejectWhenRequestedAndOfferedBookAreSame() {
        // arrange
        CreateExchangeRequest request = new CreateExchangeRequest();
        request.setRequestedBookId(100L);
        request.setOfferedBookId(100L);

        // act
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> exchangeService.create(request, new UserPrincipal(42L, "alice"))
        );

        // assert
        assertEquals("Requested book and offered book must be different.", exception.getMessage());
        verify(bookServiceClient, never()).getBook(anyLong());
        verify(exchangeRepository, never()).save(any(ExchangeRequest.class));
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
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> exchangeService.create(request, new UserPrincipal(42L, "alice"))
        );

        // assert
        assertEquals("Offered book must belong to requester.", exception.getMessage());
        verify(exchangeRepository, never()).save(any(ExchangeRequest.class));
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
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> exchangeService.create(request, new UserPrincipal(42L, "alice"))
        );

        // assert
        assertEquals("Offered book must be public.", exception.getMessage());
        verify(exchangeRepository, never()).save(any(ExchangeRequest.class));
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
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> exchangeService.create(request, new UserPrincipal(42L, "alice"))
        );

        // assert
        assertEquals("Offered book must be available.", exception.getMessage());
        verify(exchangeRepository, never()).save(any(ExchangeRequest.class));
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
                List.of(ExchangeStatus.PENDING, ExchangeStatus.ACCEPTED)
        )).thenReturn(true);

        // act
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> exchangeService.create(request, new UserPrincipal(42L, "alice"))
        );

        // assert
        assertEquals("Duplicate active exchange request already exists.", exception.getMessage());
        verify(exchangeRepository, never()).save(any(ExchangeRequest.class));
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
                List.of(ExchangeStatus.PENDING, ExchangeStatus.ACCEPTED)
        )).thenReturn(List.of(request));
        when(exchangeRepository.existsAnotherByStatusAndBookIds(
                10L,
                List.of(100L, 200L),
                ExchangeStatus.ACCEPTED
        )).thenReturn(false);
        when(exchangeRepository.save(any(ExchangeRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // act
        ExchangeResponse response = exchangeService.accept(10L, new UserPrincipal(55L, "owner"));

        // assert
        assertNotNull(response);
        assertEquals(ExchangeStatus.ACCEPTED, response.getStatus());

        verify(exchangeRepository).lockAllActiveByBookIds(
                List.of(100L, 200L),
                List.of(ExchangeStatus.PENDING, ExchangeStatus.ACCEPTED)
        );
        verify(bookServiceClient).reserve(100L);
        verify(bookServiceClient).reserve(200L);
        verify(exchangeRepository, never()).saveAll(anyList());
        verify(exchangeRepository).save(request);
        assertEquals(ExchangeStatus.ACCEPTED, request.getStatus());
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
        when(exchangeRepository.existsAnotherByStatusAndBookIds(
                10L,
                List.of(100L, 200L),
                ExchangeStatus.ACCEPTED
        )).thenReturn(true);
        when(exchangeRepository.lockAllActiveByBookIds(
                List.of(100L, 200L),
                List.of(ExchangeStatus.PENDING, ExchangeStatus.ACCEPTED)
        )).thenReturn(List.of(request));

        // act
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> exchangeService.accept(10L, new UserPrincipal(55L, "owner"))
        );

        // assert
        assertEquals("One of the books already participates in another accepted exchange.", exception.getMessage());
        verify(exchangeRepository).lockAllActiveByBookIds(
                List.of(100L, 200L),
                List.of(ExchangeStatus.PENDING, ExchangeStatus.ACCEPTED)
        );
        verify(bookServiceClient, never()).reserve(anyLong());
        verify(exchangeRepository, never()).save(any(ExchangeRequest.class));
        verify(exchangeRepository, never()).saveAll(anyList());
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
        when(exchangeRepository.existsAnotherByStatusAndBookIds(
                10L,
                List.of(100L, 200L),
                ExchangeStatus.ACCEPTED
        )).thenReturn(false);

        when(bookServiceClient.reserve(100L)).thenReturn(new BookDto());
        when(bookServiceClient.reserve(200L)).thenThrow(new IllegalStateException("Offered book cannot be reserved."));
        when(exchangeRepository.lockAllActiveByBookIds(
                List.of(100L, 200L),
                List.of(ExchangeStatus.PENDING, ExchangeStatus.ACCEPTED)
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
    }

    @Test
    void completeShouldMarkBookExchangedAndSetCompletedStatus() {
        // arrange
        ExchangeRequest request = ExchangeRequest.builder()
                .id(13L)
                .requestedBookId(103L)
                .ownerId(55L)
                .requesterId(42L)
                .status(ExchangeStatus.ACCEPTED)
                .build();

        BookDto exchangedBook = new BookDto();
        exchangedBook.setId(103L);
        exchangedBook.setStatus("EXCHANGED");

        when(exchangeRepository.findByIdForUpdate(13L)).thenReturn(Optional.of(request));
        when(bookServiceClient.markExchanged(103L)).thenReturn(exchangedBook);
        when(exchangeRepository.save(any(ExchangeRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // act
        ExchangeResponse response = exchangeService.complete(13L, new UserPrincipal(55L, "owner"));

        // assert
        assertEquals(ExchangeStatus.COMPLETED, response.getStatus());
        verify(bookServiceClient).markExchanged(103L);
        verify(exchangeRepository).save(request);
    }

    @Test
    void myOutgoingShouldReturnRequestsOfCurrentRequester() {
        // arrange
        ExchangeRequest request = ExchangeRequest.builder()
                .id(20L)
                .requestedBookId(200L)
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
    }

    @Test
    void completeShouldMarkBothBooksExchangedAndSetCompletedStatus() {
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
        when(exchangeRepository.save(any(ExchangeRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // act
        ExchangeResponse response = exchangeService.complete(10L, new UserPrincipal(55L, "owner"));

        // assert
        assertNotNull(response);
        assertEquals(ExchangeStatus.COMPLETED, response.getStatus());

        verify(bookServiceClient).markExchanged(100L);
        verify(bookServiceClient).markExchanged(200L);
        verify(exchangeRepository).save(request);
        assertEquals(ExchangeStatus.COMPLETED, request.getStatus());
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
                .status(ExchangeStatus.ACCEPTED)
                .build();

        when(exchangeRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(request));
        when(bookServiceClient.markExchanged(100L)).thenReturn(new BookDto());
        when(bookServiceClient.markExchanged(200L))
                .thenThrow(new IllegalStateException("Offered book cannot be completed."));

        // act
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> exchangeService.complete(10L, new UserPrincipal(55L, "owner"))
        );

        // assert
        assertEquals("Offered book cannot be completed.", exception.getMessage());

        verify(bookServiceClient).markExchanged(100L);
        verify(bookServiceClient).markExchanged(200L);
        verify(exchangeRepository, never()).save(any(ExchangeRequest.class));
        assertEquals(ExchangeStatus.ACCEPTED, request.getStatus());
    }
}