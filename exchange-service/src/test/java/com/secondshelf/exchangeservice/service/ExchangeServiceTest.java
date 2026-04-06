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

        BookDto book = new BookDto();
        book.setId(100L);
        book.setOwnerId(55L);
        book.setVisibility("PUBLIC");
        book.setStatus("AVAILABLE");

        when(bookServiceClient.getBook(100L)).thenReturn(book);
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
        assertEquals("Book is not public.", exception.getMessage());
        verify(exchangeRepository, never()).save(any(ExchangeRequest.class));
    }

    @Test
    void acceptShouldLockBookReserveItAndMarkRequestAccepted() {
        // arrange
        ExchangeRequest request = ExchangeRequest.builder()
                .id(10L)
                .requestedBookId(100L)
                .ownerId(55L)
                .requesterId(42L)
                .status(ExchangeStatus.PENDING)
                .message("please accept")
                .build();

        BookDto reservedBook = new BookDto();
        reservedBook.setId(100L);
        reservedBook.setOwnerId(55L);
        reservedBook.setVisibility("PUBLIC");
        reservedBook.setStatus("RESERVED");

        when(exchangeRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(request));
        when(exchangeRepository.existsByRequestedBookIdAndStatus(100L, ExchangeStatus.ACCEPTED)).thenReturn(false);
        when(bookServiceClient.reserve(100L)).thenReturn(reservedBook);
        when(exchangeRepository.save(any(ExchangeRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // act
        ExchangeResponse response = exchangeService.accept(10L, new UserPrincipal(55L, "owner"));

        // assert
        assertNotNull(response);
        assertEquals(ExchangeStatus.ACCEPTED, response.getStatus());

        verify(exchangeRepository).lockAllByRequestedBookIdAndStatuses(
                100L,
                List.of(ExchangeStatus.PENDING, ExchangeStatus.ACCEPTED)
        );
        verify(bookServiceClient).reserve(100L);
        verify(exchangeRepository).save(request);
        assertEquals(ExchangeStatus.ACCEPTED, request.getStatus());
    }

    @Test
    void acceptShouldRejectWhenBookAlreadyHasAcceptedExchange() {
        // arrange
        ExchangeRequest request = ExchangeRequest.builder()
                .id(10L)
                .requestedBookId(100L)
                .ownerId(55L)
                .requesterId(42L)
                .status(ExchangeStatus.PENDING)
                .build();

        when(exchangeRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(request));
        when(exchangeRepository.existsByRequestedBookIdAndStatus(100L, ExchangeStatus.ACCEPTED)).thenReturn(true);

        // act
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> exchangeService.accept(10L, new UserPrincipal(55L, "owner"))
        );

        // assert
        assertEquals("This book already has an accepted exchange request.", exception.getMessage());
        verify(exchangeRepository).lockAllByRequestedBookIdAndStatuses(
                100L,
                List.of(ExchangeStatus.PENDING, ExchangeStatus.ACCEPTED)
        );
        verify(bookServiceClient, never()).reserve(anyLong());
        verify(exchangeRepository, never()).save(any(ExchangeRequest.class));
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
}