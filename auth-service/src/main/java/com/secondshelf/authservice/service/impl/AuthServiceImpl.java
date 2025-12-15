package com.secondshelf.authservice.service.impl;

import com.secondshelf.authservice.client.UserServiceClient;
import com.secondshelf.authservice.client.dto.UserAuthResponse;
import com.secondshelf.authservice.client.dto.UserClaimsResponse;
import com.secondshelf.authservice.dto.*;
import com.secondshelf.authservice.security.JwtTokenProvider;
import com.secondshelf.authservice.service.AuthService;
import com.secondshelf.authservice.service.RefreshTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {
    private final JwtTokenProvider jwtTokenProvider;
    private final UserServiceClient userServiceClient;
    private final RefreshTokenService refreshTokenService;

    @Override
    public TokenPairResponse register(RegisterRequest request) {
        userServiceClient.createUser(request);

        UserAuthResponse user = userServiceClient.authenticate(request.getUsername(), request.getPassword());
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Cannot authenticate after registration");
        }

        String refresh = refreshTokenService.issue(user.getUserId());
        String access = jwtTokenProvider.generateToken(user.getUserId(), user.getUsername(), user.getRoles());
        return new TokenPairResponse(access, refresh, "Bearer");
    }

    @Override
    public TokenPairResponse login(LoginRequest loginRequest) {
        UserAuthResponse user = userServiceClient.authenticate(
                loginRequest.getUsername(),
                loginRequest.getPassword()
        );

        if(user == null)
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password.");

        String refresh = refreshTokenService.issue(user.getUserId());
        String access = jwtTokenProvider.generateToken(user.getUserId(), user.getUsername(), user.getRoles());
        return new TokenPairResponse(access, refresh, "Bearer");
    }

    @Override
    public TokenPairResponse refresh(RefreshRequest request) {
        var rotation = refreshTokenService.rotate(request.getRefreshToken());

        UserClaimsResponse claims = userServiceClient.getClaims(rotation.userId());

        if (!claims.isEnabled()) {
            refreshTokenService.revoke(rotation.refreshToken());
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Account is blocked");
        }

        String access = jwtTokenProvider.generateToken(
                claims.getUserId(),
                claims.getUsername(),
                claims.getRoles()
        );

        return new TokenPairResponse(access, rotation.refreshToken(), "Bearer");
    }

    @Override
    public void logout(RefreshRequest request) {
        refreshTokenService.revoke(request.getRefreshToken());
    }

    @Override
    public void logoutAll(RefreshRequest request) {
        refreshTokenService.revokeAllByRefresh(request.getRefreshToken());
    }
}
