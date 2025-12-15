package com.secondshelf.authservice.service;

import com.secondshelf.authservice.entity.RefreshToken;
import com.secondshelf.authservice.repository.RefreshTokenRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Base64;

@Service
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final Clock clock = Clock.systemUTC();
    private final long refreshTtlMs;
    private final SecureRandom secureRandom = new SecureRandom();

    public RefreshTokenService(
            RefreshTokenRepository refreshTokenRepository,
            @Value("${jwt.refresh-expiration-ms:2592000000}") long refreshTtlMs
    ) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.refreshTtlMs = refreshTtlMs;
    }

    /**
     * Создать новую refresh-сессию для пользователя.
     * Возвращаем "сырой" refresh token (его ты отдаёшь клиенту).
     */
    @Transactional
    public String issue(Long userId) {
        String raw = generateRawToken();
        String hash = sha256Hex(raw);

        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime expiresAt = now.plusNanos(refreshTtlMs * 1_000_000);

        RefreshToken entity = RefreshToken.builder()
                .userId(userId)
                .tokenHash(hash)
                .expiresAt(expiresAt)
                .revokedAt(null)
                .createdAt(now)
                .build();

        refreshTokenRepository.save(entity);
        return raw;
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

        String hash = sha256Hex(rawRefreshToken);

        RefreshToken current = refreshTokenRepository.findByTokenHashForUpdate(hash)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid refresh token"));

        LocalDateTime now = LocalDateTime.now(clock);

        if (current.isRevoked()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token is revoked");
        }
        if (current.isExpired(now)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token is expired");
        }

        current.setRevokedAt(now);
        refreshTokenRepository.save(current);

        String newRaw = generateRawToken();
        String newHash = sha256Hex(newRaw);

        LocalDateTime expiresAt = now.plusNanos(refreshTtlMs * 1_000_000);

        RefreshToken next = RefreshToken.builder()
                .userId(current.getUserId())
                .tokenHash(newHash)
                .expiresAt(expiresAt)
                .revokedAt(null)
                .createdAt(now)
                .build();

        refreshTokenRepository.save(next);

        return new RotationResult(current.getUserId(), newRaw);
    }

    /**
     * Отозвать конкретный refresh token (logout на одном устройстве).
     */
    @Transactional
    public void revoke(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) return;

        String hash = sha256Hex(rawRefreshToken);

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
                .forEach(t -> t.setRevokedAt(now));
    }

    @Transactional
    public void revokeAllByRefresh(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) return;

        String hash = sha256Hex(rawRefreshToken);

        refreshTokenRepository.findByTokenHashForUpdate(hash).ifPresent(token -> {
            revokeAll(token.getUserId());
        });
    }


    private String generateRawToken() {
        // 48 байт ~ 64 символа base64url — хорошо по энтропии
        byte[] bytes = new byte[48];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String sha256Hex(String raw) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(raw.getBytes(StandardCharsets.UTF_8));
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

    public record RotationResult(Long userId, String refreshToken) {}
}
