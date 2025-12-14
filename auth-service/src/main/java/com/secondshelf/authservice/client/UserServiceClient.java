package com.secondshelf.authservice.client;

import com.secondshelf.authservice.client.dto.UserAuthRequest;
import com.secondshelf.authservice.client.dto.UserAuthResponse;
import com.secondshelf.authservice.dto.RegisterRequest;
import com.secondshelf.authservice.exception.UserServiceClientException;
import com.secondshelf.authservice.exception.advice.ErrorResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class UserServiceClient {

    private final RestClient userServiceRestClient;
    private final ObjectMapper objectMapper;

    @Value("${internal.token}")
    private String internalToken;

    public void createUser(RegisterRequest request) {
        try {
            userServiceRestClient.post()
                    .uri("/internal/users")
                    .header("X-Internal-Token", internalToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpClientErrorException ex) {
            throw translateUserServiceError(ex);
        }
    }

    public UserAuthResponse authenticate(String username, String password) {
        try {
            return userServiceRestClient.post()
                    .uri("/internal/auth/authenticate")
                    .header("X-Internal-Token", internalToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new UserAuthRequest(username, password))
                    .retrieve()
                    .body(UserAuthResponse.class);
        } catch (HttpClientErrorException.Unauthorized ex) {
            return null;
        } catch (HttpClientErrorException ex) {
            throw translateUserServiceError(ex);
        }
    }

    private RuntimeException translateUserServiceError(HttpClientErrorException ex) {
        // user-service уже отдает ErrorResponse (timestamp/code/message/details)
        try {
            ErrorResponse downstream = objectMapper.readValue(ex.getResponseBodyAsByteArray(), ErrorResponse.class);
            HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());
            return new UserServiceClientException(status, downstream);
        } catch (Exception parseFail) {
            HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());
            return new UserServiceClientException(status, "User-service error: " + ex.getResponseBodyAsString());
        }
    }
}
