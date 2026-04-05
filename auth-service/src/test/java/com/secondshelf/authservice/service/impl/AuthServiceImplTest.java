package com.secondshelf.authservice.service.impl;

import com.secondshelf.authservice.client.UserServiceClient;
import com.secondshelf.authservice.client.dto.UserAuthResponse;
import com.secondshelf.authservice.client.dto.UserClaimsResponse;
import com.secondshelf.authservice.dto.LoginRequest;
import com.secondshelf.authservice.dto.RefreshRequest;
import com.secondshelf.authservice.dto.TokenPairResponse;
import com.secondshelf.authservice.security.JwtTokenProvider;
import com.secondshelf.authservice.service.RefreshTokenService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private UserServiceClient userServiceClient;

    @Mock
    private RefreshTokenService refreshTokenService;

    @InjectMocks
    private AuthServiceImpl authService;

    @Test
    void loginShouldReturnTokenPairWhenCredentialsAreValid() {
        // arrange
        LoginRequest request = new LoginRequest();
        request.setUsername("alice");
        request.setPassword("password123");

        UserAuthResponse user = new UserAuthResponse(
                1L,
                "alice",
                List.of("ROLE_USER")
        );

        when(userServiceClient.authenticate("alice", "password123")).thenReturn(user);
        when(refreshTokenService.issue(1L)).thenReturn("refresh-token");
        when(jwtTokenProvider.generateToken(1L, "alice", List.of("ROLE_USER")))
                .thenReturn("access-token");

        // act
        TokenPairResponse response = authService.login(request);

        // assert
        assertNotNull(response);
        assertEquals("access-token", response.getAccessToken());
        assertEquals("refresh-token", response.getRefreshToken());
        assertEquals("Bearer", response.getTokenType());

        verify(userServiceClient).authenticate("alice", "password123");
        verify(refreshTokenService).issue(1L);
        verify(jwtTokenProvider).generateToken(1L, "alice", List.of("ROLE_USER"));
    }

    @Test
    void loginShouldThrowUnauthorizedWhenCredentialsAreInvalid() {
        // arrange
        LoginRequest request = new LoginRequest();
        request.setUsername("alice");
        request.setPassword("wrong-password");

        when(userServiceClient.authenticate("alice", "wrong-password")).thenReturn(null);

        // act
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> authService.login(request)
        );

        // assert
        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
        assertEquals("Invalid username or password.", exception.getReason());

        verify(userServiceClient).authenticate("alice", "wrong-password");
        verifyNoInteractions(refreshTokenService, jwtTokenProvider);
    }

    @Test
    void refreshShouldReturnNewAccessTokenAndRotatedRefreshTokenWhenRefreshTokenIsValid() {
        // arrange
        RefreshRequest request = new RefreshRequest();
        request.setRefreshToken("old-refresh-token");

        RefreshTokenService.RotationResult rotationResult =
                new RefreshTokenService.RotationResult(7L, "new-refresh-token");

        UserClaimsResponse claims = new UserClaimsResponse(
                7L,
                "bob",
                List.of("ROLE_USER"),
                true
        );

        when(refreshTokenService.rotate("old-refresh-token")).thenReturn(rotationResult);
        when(userServiceClient.getClaims(7L)).thenReturn(claims);
        when(jwtTokenProvider.generateToken(7L, "bob", List.of("ROLE_USER")))
                .thenReturn("new-access-token");

        // act
        TokenPairResponse response = authService.refresh(request);

        // assert
        assertNotNull(response);
        assertEquals("new-access-token", response.getAccessToken());
        assertEquals("new-refresh-token", response.getRefreshToken());
        assertEquals("Bearer", response.getTokenType());

        verify(refreshTokenService).rotate("old-refresh-token");
        verify(userServiceClient).getClaims(7L);
        verify(jwtTokenProvider).generateToken(7L, "bob", List.of("ROLE_USER"));
        verify(refreshTokenService, never()).revoke(anyString());
    }

    @Test
    void refreshShouldRevokeRotatedRefreshTokenAndThrowForbiddenWhenUserIsBlocked() {
        // arrange
        RefreshRequest request = new RefreshRequest();
        request.setRefreshToken("old-refresh-token");

        RefreshTokenService.RotationResult rotationResult =
                new RefreshTokenService.RotationResult(7L, "new-refresh-token");

        UserClaimsResponse blockedClaims = new UserClaimsResponse(
                7L,
                "bob",
                List.of("ROLE_USER"),
                false
        );

        when(refreshTokenService.rotate("old-refresh-token")).thenReturn(rotationResult);
        when(userServiceClient.getClaims(7L)).thenReturn(blockedClaims);

        // act
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> authService.refresh(request)
        );

        // assert
        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
        assertEquals("Account is blocked", exception.getReason());

        verify(refreshTokenService).rotate("old-refresh-token");
        verify(userServiceClient).getClaims(7L);
        verify(refreshTokenService).revoke("new-refresh-token");
        verifyNoInteractions(jwtTokenProvider);
    }

    @Test
    void logoutShouldRevokeProvidedRefreshToken() {
        // arrange
        RefreshRequest request = new RefreshRequest();
        request.setRefreshToken("refresh-token");

        // act
        authService.logout(request);

        // assert
        verify(refreshTokenService).revoke("refresh-token");
        verifyNoInteractions(userServiceClient, jwtTokenProvider);
    }

    @Test
    void logoutAllShouldRevokeAllSessionsByProvidedRefreshToken() {
        // arrange
        RefreshRequest request = new RefreshRequest();
        request.setRefreshToken("refresh-token");

        // act
        authService.logoutAll(request);

        // assert
        verify(refreshTokenService).revokeAllByRefresh("refresh-token");
        verifyNoInteractions(userServiceClient, jwtTokenProvider);
    }
}