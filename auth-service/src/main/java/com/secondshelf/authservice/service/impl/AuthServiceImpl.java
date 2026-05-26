package com.secondshelf.authservice.service.impl;

import com.secondshelf.authservice.client.UserServiceClient;
import com.secondshelf.authservice.client.dto.UserAuthResponse;
import com.secondshelf.authservice.client.dto.UserClaimsResponse;
import com.secondshelf.authservice.dto.*;
import com.secondshelf.authservice.security.JwtTokenProvider;
import com.secondshelf.authservice.exception.UserServiceClientException;
import com.secondshelf.authservice.service.AuthService;
import com.secondshelf.authservice.service.RefreshTokenService;
import com.secondshelf.observability.AuditEvent;
import com.secondshelf.observability.AuditLogger;
import com.secondshelf.observability.AuditOutcome;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private static final AuditLogger AUDIT_LOGGER = AuditLogger.forClass(AuthServiceImpl.class);

    private final JwtTokenProvider jwtTokenProvider;
    private final UserServiceClient userServiceClient;
    private final RefreshTokenService refreshTokenService;

    @Override
    public TokenPairResponse register(RegisterRequest request) {
        Long userId = null;

        try {
            userServiceClient.createUser(request);

            UserAuthResponse user = userServiceClient.authenticate(request.getUsername(), request.getPassword());
            if (user == null) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Cannot authenticate after registration");
            }

            userId = user.getUserId();
            String refresh = refreshTokenService.issue(user.getUserId());
            String access = jwtTokenProvider.generateToken(user.getUserId(), user.getUsername(), user.getRoles());

            AUDIT_LOGGER.log(AuditEvent.builder("AUTH_REGISTRATION", AuditOutcome.SUCCESS)
                    .actorUserId(userId)
                    .targetUserId(userId)
                    .build());

            return new TokenPairResponse(access, refresh, "Bearer");
        } catch (RuntimeException ex) {
            AUDIT_LOGGER.log(AuditEvent.builder("AUTH_REGISTRATION", AuditOutcome.FAILURE)
                    .actorUserId(userId)
                    .targetUserId(userId)
                    .reason(resolveReason(ex))
                    .errorCode(resolveErrorCode(ex))
                    .build());
            throw ex;
        }
    }

    @Override
    public TokenPairResponse login(LoginRequest loginRequest) {
        Long userId = null;

        try {
            UserAuthResponse user = userServiceClient.authenticate(
                    loginRequest.getUsername(),
                    loginRequest.getPassword()
            );

            if (user == null) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password.");
            }

            userId = user.getUserId();
            String refresh = refreshTokenService.issue(user.getUserId());
            String access = jwtTokenProvider.generateToken(user.getUserId(), user.getUsername(), user.getRoles());

            AUDIT_LOGGER.log(AuditEvent.builder("AUTH_LOGIN", AuditOutcome.SUCCESS)
                    .actorUserId(userId)
                    .targetUserId(userId)
                    .build());

            return new TokenPairResponse(access, refresh, "Bearer");
        } catch (RuntimeException ex) {
            AUDIT_LOGGER.log(AuditEvent.builder("AUTH_LOGIN", AuditOutcome.FAILURE)
                    .actorUserId(userId)
                    .targetUserId(userId)
                    .reason(resolveReason(ex))
                    .errorCode(resolveErrorCode(ex))
                    .build());
            throw ex;
        }
    }

    @Override
    public TokenPairResponse refresh(RefreshRequest request) {
        Long userId = null;

        try {
            var rotation = refreshTokenService.rotate(request.getRefreshToken());
            userId = rotation.userId();

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

            AUDIT_LOGGER.log(AuditEvent.builder("AUTH_REFRESH", AuditOutcome.SUCCESS)
                    .actorUserId(userId)
                    .targetUserId(userId)
                    .build());

            return new TokenPairResponse(access, rotation.refreshToken(), "Bearer");
        } catch (RuntimeException ex) {
            AUDIT_LOGGER.log(AuditEvent.builder("AUTH_REFRESH", AuditOutcome.FAILURE)
                    .actorUserId(userId)
                    .targetUserId(userId)
                    .reason(resolveReason(ex))
                    .errorCode(resolveErrorCode(ex))
                    .build());
            throw ex;
        }
    }

    @Override
    public void logout(RefreshRequest request) {
        Long userId = null;
        try {
            userId = refreshTokenService.revoke(request.getRefreshToken());
            AUDIT_LOGGER.log(AuditEvent.builder("AUTH_LOGOUT", AuditOutcome.SUCCESS)
                    .actorUserId(userId)
                    .targetUserId(userId)
                    .build());
        } catch (RuntimeException ex) {
            AUDIT_LOGGER.log(AuditEvent.builder("AUTH_LOGOUT", AuditOutcome.FAILURE)
                    .actorUserId(userId)
                    .targetUserId(userId)
                    .reason(resolveReason(ex))
                    .errorCode(resolveErrorCode(ex))
                    .build());
            throw ex;
        }
    }

    @Override
    public void logoutAll(RefreshRequest request) {
        Long userId = null;
        try {
            userId = refreshTokenService.revokeAllByRefresh(request.getRefreshToken());
            AUDIT_LOGGER.log(AuditEvent.builder("AUTH_LOGOUT_ALL", AuditOutcome.SUCCESS)
                    .actorUserId(userId)
                    .targetUserId(userId)
                    .build());
        } catch (RuntimeException ex) {
            AUDIT_LOGGER.log(AuditEvent.builder("AUTH_LOGOUT_ALL", AuditOutcome.FAILURE)
                    .actorUserId(userId)
                    .targetUserId(userId)
                    .reason(resolveReason(ex))
                    .errorCode(resolveErrorCode(ex))
                    .build());
            throw ex;
        }
    }

    private String resolveReason(RuntimeException ex) {
        if (ex instanceof ResponseStatusException responseStatusException
                && responseStatusException.getReason() != null) {
            return responseStatusException.getReason();
        }
        return ex.getMessage();
    }

    private String resolveErrorCode(RuntimeException ex) {
        if (ex instanceof UserServiceClientException userServiceClientException
                && userServiceClientException.getDownstream() != null) {
            return userServiceClientException.getDownstream().getErrorCode();
        }

        if (ex instanceof ResponseStatusException responseStatusException) {
            String reason = responseStatusException.getReason();
            if ("Invalid username or password.".equals(reason)) {
                return "INVALID_CREDENTIALS";
            }
            if ("Cannot authenticate after registration".equals(reason)) {
                return "POST_REGISTRATION_AUTH_FAILED";
            }
            if ("Account is blocked".equals(reason)) {
                return "ACCOUNT_BLOCKED";
            }
            if ("Refresh token is missing".equals(reason)) {
                return "REFRESH_TOKEN_MISSING";
            }
            if ("Invalid refresh token".equals(reason)) {
                return "INVALID_REFRESH_TOKEN";
            }
            if ("Refresh token is expired".equals(reason)) {
                return "REFRESH_TOKEN_EXPIRED";
            }
            if ("Refresh token reuse detected".equals(reason)) {
                return "REFRESH_TOKEN_REUSE_DETECTED";
            }
        }

        return null;
    }
}
