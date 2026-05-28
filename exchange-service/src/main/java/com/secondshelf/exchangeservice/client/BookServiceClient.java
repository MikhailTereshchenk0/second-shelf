package com.secondshelf.exchangeservice.client;

import com.secondshelf.exchangeservice.client.dto.BookDto;
import com.secondshelf.exchangeservice.observability.CorrelationId;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Arrays;
import java.util.List;

@Component
public class BookServiceClient {

    private final RestClient rest;
    private final String internalToken;

    public BookServiceClient(@Qualifier("bookServiceRestClient") RestClient bookServiceRestClient,
                             @Value("${internal.token}") String internalToken) {
        this.rest = bookServiceRestClient;
        this.internalToken = internalToken;
    }

    public BookDto getBook(Long id) {
        return rest.get()
                .uri("/internal/books/{id}", id)
                .header("X-Internal-Token", internalToken)
                .header(CorrelationId.HEADER_NAME, CorrelationId.currentOrGenerate())
                .retrieve()
                .body(BookDto.class);
    }

    public List<BookDto> getAvailablePublicBooksByOwner(Long ownerId) {
        BookDto[] books = rest.get()
                .uri("/internal/books/owners/{ownerId}/available-public", ownerId)
                .header("X-Internal-Token", internalToken)
                .header(CorrelationId.HEADER_NAME, CorrelationId.currentOrGenerate())
                .retrieve()
                .body(BookDto[].class);
        return books == null ? List.of() : Arrays.asList(books);
    }

    public BookDto reserve(Long id) {
        return rest.post()
                .uri("/internal/books/{id}/reserve", id)
                .header("X-Internal-Token", internalToken)
                .header(CorrelationId.HEADER_NAME, CorrelationId.currentOrGenerate())
                .contentType(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(BookDto.class);
    }

    public BookDto makeAvailable(Long id) {
        return rest.post()
                .uri("/internal/books/{id}/available", id)
                .header("X-Internal-Token", internalToken)
                .header(CorrelationId.HEADER_NAME, CorrelationId.currentOrGenerate())
                .contentType(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(BookDto.class);
    }

    public BookDto markExchanged(Long id) {
        return rest.post()
                .uri("/internal/books/{id}/exchanged", id)
                .header("X-Internal-Token", internalToken)
                .header(CorrelationId.HEADER_NAME, CorrelationId.currentOrGenerate())
                .contentType(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(BookDto.class);
    }
}
