package com.secondshelf.authservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.secondshelf.authservice.config.SecurityConfig;
import com.secondshelf.authservice.dto.LoginRequest;
import com.secondshelf.authservice.dto.RefreshRequest;
import com.secondshelf.authservice.dto.RegisterRequest;
import com.secondshelf.authservice.dto.TokenPairResponse;
import com.secondshelf.authservice.exception.handler.GlobalExceptionHandler;
import com.secondshelf.authservice.security.JwtAuthenticationFilter;
import com.secondshelf.authservice.security.JwtTokenProvider;
import com.secondshelf.authservice.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, GlobalExceptionHandler.class})
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @Test
    void registerShouldAcceptStrongPassword() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("alice");
        request.setEmail("alice.reader@example.com");
        request.setFirstName("Alice");
        request.setLastName("Reader");
        request.setPassword("V3ry$trongPwd");

        TokenPairResponse response = new TokenPairResponse(
                "access-token",
                "refresh-token",
                "Bearer"
        );

        when(authService.register(any(RegisterRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-token"))
                .andExpect(jsonPath("$.refreshToken").value("refresh-token"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"));

        verify(authService).register(any(RegisterRequest.class));
    }

    @Test
    void registerShouldRejectWeakPassword() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("alice");
        request.setEmail("alice.reader@example.com");
        request.setFirstName("Alice");
        request.setLastName("Reader");
        request.setPassword("weakpass12");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("Request validation failed"))
                .andExpect(jsonPath("$.details.password")
                        .value("Password must contain at least one uppercase letter."));
    }

    @Test
    void registerShouldRejectPasswordContainingUsernameOrEmailLocalPart() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("alice");
        request.setEmail("reader@example.com");
        request.setFirstName("Alice");
        request.setLastName("Reader");
        request.setPassword("Sup3r!alicePwd");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("Request validation failed"))
                .andExpect(jsonPath("$.details.password").value("Password must not contain username."));
    }

    @Test
    void loginShouldReturnTokenPairWhenRequestIsValid() throws Exception {
        // arrange
        LoginRequest request = new LoginRequest();
        request.setUsername("alice");
        request.setPassword("password123");

        TokenPairResponse response = new TokenPairResponse(
                "access-token",
                "refresh-token",
                "Bearer"
        );

        when(authService.login(any(LoginRequest.class))).thenReturn(response);

        // act + assert
        mockMvc.perform(post("/api/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-token"))
                .andExpect(jsonPath("$.refreshToken").value("refresh-token"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"));

        verify(authService).login(any(LoginRequest.class));
    }

    @Test
    void loginShouldReturnValidationErrorWhenRequestIsInvalid() throws Exception {
        // arrange
        LoginRequest request = new LoginRequest();
        request.setUsername("");
        request.setPassword("");

        // act + assert
        mockMvc.perform(post("/api/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("Request validation failed"))
                .andExpect(jsonPath("$.details.username").value("Username must not be blank."))
                .andExpect(jsonPath("$.details.password").value("Password must not be blank."));
    }

    @Test
    void loginShouldReturnUnauthorizedWhenCredentialsAreInvalid() throws Exception {
        // arrange
        LoginRequest request = new LoginRequest();
        request.setUsername("alice");
        request.setPassword("wrong-password");

        when(authService.login(any(LoginRequest.class)))
                .thenThrow(new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Invalid username or password."
                ));

        // act + assert
        mockMvc.perform(post("/api/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("401 UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("Invalid username or password."));
    }

    @Test
    void refreshShouldReturnTokenPairWhenRequestIsValid() throws Exception {
        // arrange
        RefreshRequest request = new RefreshRequest();
        request.setRefreshToken("old-refresh-token");

        TokenPairResponse response = new TokenPairResponse(
                "new-access-token",
                "new-refresh-token",
                "Bearer"
        );

        when(authService.refresh(any(RefreshRequest.class))).thenReturn(response);

        // act + assert
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("new-access-token"))
                .andExpect(jsonPath("$.refreshToken").value("new-refresh-token"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"));

        verify(authService).refresh(any(RefreshRequest.class));
    }

    @Test
    void logoutShouldReturnNoContent() throws Exception {
        // arrange
        RefreshRequest request = new RefreshRequest();
        request.setRefreshToken("refresh-token");

        // act + assert
        mockMvc.perform(post("/api/auth/logout")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNoContent());

        verify(authService).logout(any(RefreshRequest.class));
    }

    @Test
    void logoutAllShouldReturnNoContent() throws Exception {
        // arrange
        RefreshRequest request = new RefreshRequest();
        request.setRefreshToken("refresh-token");

        // act + assert
        mockMvc.perform(post("/api/auth/logout-all")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNoContent());

        verify(authService).logoutAll(any(RefreshRequest.class));
    }

    @Test
    void meShouldReturnCurrentAuthenticatedUserFromJwtToken() throws Exception {
        // arrange
        when(jwtTokenProvider.validateToken("valid-token")).thenReturn(true);
        when(jwtTokenProvider.getUsername("valid-token")).thenReturn("alice");
        when(jwtTokenProvider.getRoles("valid-token")).thenReturn(List.of("ROLE_USER"));

        // act + assert
        mockMvc.perform(get("/api/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer valid-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("alice"))
                .andExpect(jsonPath("$.roles[0]").value("ROLE_USER"));
    }
}
