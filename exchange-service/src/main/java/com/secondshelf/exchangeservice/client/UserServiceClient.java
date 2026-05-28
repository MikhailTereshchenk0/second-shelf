package com.secondshelf.exchangeservice.client;

import com.secondshelf.exchangeservice.client.dto.UserContactDto;
import com.secondshelf.exchangeservice.observability.CorrelationId;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class UserServiceClient {

    private final RestClient rest;
    private final String internalToken;

    public UserServiceClient(@Qualifier("userServiceRestClient") RestClient userServiceRestClient,
                             @Value("${internal.token}") String internalToken) {
        this.rest = userServiceRestClient;
        this.internalToken = internalToken;
    }

    public UserContactDto getContact(Long userId) {
        return rest.get()
                .uri("/internal/users/{id}/contact", userId)
                .header("X-Internal-Token", internalToken)
                .header(CorrelationId.HEADER_NAME, CorrelationId.currentOrGenerate())
                .retrieve()
                .body(UserContactDto.class);
    }
}
