package com.secondshelf.authservice.client;

import com.secondshelf.authservice.client.dto.UserAuthRequest;
import com.secondshelf.authservice.client.dto.UserAuthResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
@RequiredArgsConstructor
public class UserServiceClient {

    private final RestClient userServiceRestClient;

    public UserAuthResponse authenticate(String username, String password) {
        try {
            return userServiceRestClient.post()
                    .uri("/internal/auth/authenticate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new UserAuthRequest(username, password))
                    .retrieve()
                    .body(UserAuthResponse.class);
        } catch (RestClientResponseException exception) {

            if (exception.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                return null;
            }
            throw exception;
        }
    }
}
