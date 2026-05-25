package com.secondshelf.authservice.service;

import com.secondshelf.authservice.entity.RefreshToken;
import com.secondshelf.authservice.repository.RefreshTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.UUID;

@Service
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);
    private static final String HMAC_SHA256 = "HmacSHA256";

    private final RefreshTokenRepository refreshTokenRepository;
    private final Clock clock;
    private final long refreshTtlMs;
    private final SecureRandom secureRandom;
    private final byte[] refreshTokenPepper;

    public RefreshTokenService(
            RefreshTokenRepository refreshTokenRepository,
            @Value("${jwt.refresh-expiration-ms:2592000000}") long refreshTtlMs,
            @Value("${auth.refresh-token.pepper}") String refreshTokenPepper
    ) {
        this(refreshTokenRepository, refreshTtlMs, refreshTokenPepper, Clock.systemUTC(), new SecureRandom());
    }

    RefreshTokenService(
            RefreshTokenRepository refreshTokenRepository,
            long refreshTtlMs,
            String refreshTokenPepper,
            Clock clock,
            SecureRandom secureRandom
    ) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.refreshTtlMs = refreshTtlMs;
        this.clock = clock;
        this.secureRandom = secureRandom;
        if (refreshTokenPepper == null || refreshTokenPepper.isBlank()) {
            throw new IllegalArgumentException("auth.refresh-token.pepper must not be blank");
        }
        this.refreshTokenPepper = refreshTokenPepper.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Создать новую refresh-сессию для пользователя.
     * Возвращаем "сырой" refresh token (его ты отдаёшь клиенту).
     */
    @Transactional
    public String issue(Long userId) {
        LocalDateTime now = LocalDateTime.now(clock);
        IssuedToken issuedToken = prepareIssuedToken();

        refreshTokenRepository.save(buildRefreshToken(
                userId,
                UUID.randomUUID().toString(),
                null,
                issuedToken.tokenHash(),
                now
        ));

        return issuedToken.rawToken();
    }

    /**
     * Ротация refresh: старый токен становится недействительным, выдаём новый.
     * Если токен истёк/отозван/не найден — 401.
     */
    @Transactional
    public RotationResult rotate(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token is missing");
        }

        String hash = hmacSha256Hex(rawRefreshToken);

        RefreshToken current = refreshTokenRepository.findByTokenHashForUpdate(hash)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid refresh token"));

        LocalDateTime now = LocalDateTime.now(clock);

        if (current.isRevoked()) {
            handleReuseDetection(current, now);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token reuse detected");
        }
        if (current.isExpired(now)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token is expired");
        }

        IssuedToken nextToken = prepareIssuedToken();

        current.setRevokedAt(now);
        current.setLastUsedAt(now);
        current.setReplacedByHash(nextToken.tokenHash());
        refreshTokenRepository.save(current);

        refreshTokenRepository.save(buildRefreshToken(
                current.getUserId(),
                current.getTokenFamilyId(),
                current.getUserAgent(),
                nextToken.tokenHash(),
                now
        ));

        return new RotationResult(current.getUserId(), nextToken.rawToken());
    }

    /**
     * Отозвать конкретный refresh token (logout на одном устройстве).
     */
    @Transactional
    public void revoke(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }

        String hash = hmacSha256Hex(rawRefreshToken);

        refreshTokenRepository.findByTokenHashForUpdate(hash).ifPresent(token -> {
            if (token.getRevokedAt() == null) {
                token.setRevokedAt(LocalDateTime.now(clock));
                refreshTokenRepository.save(token);
            }
        });
    }

    /**
     * Отозвать все активные refresh-токены пользователя (logout everywhere).
     */
    @Transactional
    public void revokeAll(Long userId) {
        LocalDateTime now = LocalDateTime.now(clock);
        refreshTokenRepository.findAllByUserIdAndRevokedAtIsNull(userId)
                .forEach(token -> token.setRevokedAt(now));
    }

    @Transactional
    public void revokeAllByRefresh(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }

        String hash = hmacSha256Hex(rawRefreshToken);

        refreshTokenRepository.findByTokenHashForUpdate(hash).ifPresent(token -> revokeAll(token.getUserId()));
    }

    private RefreshToken buildRefreshToken(
            Long userId,
            String tokenFamilyId,
            String userAgent,
            String tokenHash,
            LocalDateTime now
    ) {
        LocalDateTime expiresAt = now.plusNanos(refreshTtlMs * 1_000_000);
        return RefreshToken.builder()
                .userId(userId)
                .tokenFamilyId(tokenFamilyId)
                .userAgent(userAgent)
                .tokenHash(tokenHash)
                .expiresAt(expiresAt)
                .revokedAt(null)
                .lastUsedAt(null)
                .replacedByHash(null)
                .reuseDetectedAt(null)
                .createdAt(now)
                .build();
    }

    private void handleReuseDetection(RefreshToken current, LocalDateTime now) {
        if (current.getReuseDetectedAt() == null) {
            current.setReuseDetectedAt(now);
        }
        refreshTokenRepository.save(current);

        refreshTokenRepository.findAllByTokenFamilyIdAndRevokedAtIsNullForUpdate(current.getTokenFamilyId())
                .forEach(token -> token.setRevokedAt(now));

        log.warn(
                "security_audit event=refresh_token_reuse_detected userId={} tokenFamilyId={} refreshTokenId={} detectedAt={}",
                current.getUserId(),
                current.getTokenFamilyId(),
                current.getId(),
                now
        );
    }

    private IssuedToken prepareIssuedToken() {
        String raw = generateRawToken();
        return new IssuedToken(raw, hmacSha256Hex(raw));
    }

    private String generateRawToken() {
        // 48 байт ~ 64 символа base64url — хорошо по энтропии
        byte[] bytes = new byte[48];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hmacSha256Hex(String raw) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(refreshTokenPepper, HMAC_SHA256));
            byte[] digest = mac.doFinal(raw.getBytes(StandardCharsets.UTF_8));
            return toHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot hash refresh token", e);
        }
    }

    private String toHex(byte[] bytes) {
        char[] hexArray = "0123456789abcdef".toCharArray();
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }

    private record IssuedToken(String rawToken, String tokenHash) {}

    public record RotationResult(Long userId, String refreshToken) {}
}
