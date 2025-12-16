package com.secondshelf.exchangeservice.client;

import com.secondshelf.exchangeservice.client.dto.BookDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class BookServiceClient {

    private final RestClient rest;
    private final String internalToken;

    public BookServiceClient(RestClient bookServiceRestClient,
                             @Value("${internal.token}") String internalToken) {
        this.rest = bookServiceRestClient;
        this.internalToken = internalToken;
    }

    public BookDto getBook(Long id) {
        return rest.get()
                .uri("/internal/books/{id}", id)
                .header("X-Internal-Token", internalToken)
                .retrieve()
                .body(BookDto.class);
    }

    public BookDto reserve(Long id) {
        return rest.post()
                .uri("/internal/books/{id}/reserve", id)
                .header("X-Internal-Token", internalToken)
                .contentType(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(BookDto.class);
    }

    public BookDto makeAvailable(Long id) {
        return rest.post()
                .uri("/internal/books/{id}/available", id)
                .header("X-Internal-Token", internalToken)
                .contentType(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(BookDto.class);
    }

    public BookDto markExchanged(Long id) {
        return rest.post()
                .uri("/internal/books/{id}/exchanged", id)
                .header("X-Internal-Token", internalToken)
                .contentType(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(BookDto.class);
    }
}
