package com.secondshelf.authservice.service;

import com.secondshelf.authservice.entity.RefreshToken;
import com.secondshelf.authservice.repository.RefreshTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    private static final String PEPPER = "test-refresh-token-pepper";
    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-05-26T12:00:00Z"), ZoneOffset.UTC);
    private static final LocalDateTime NOW = LocalDateTime.ofInstant(FIXED_CLOCK.instant(), ZoneOffset.UTC);

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    private RefreshTokenService refreshTokenService;

    @BeforeEach
    void setUp() {
        refreshTokenService = new RefreshTokenService(
                refreshTokenRepository,
                2_592_000_000L,
                PEPPER,
                FIXED_CLOCK,
                new SecureRandom()
        );
        lenient().when(refreshTokenRepository.save(any(RefreshToken.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void rotateShouldRevokeCurrentTokenAndIssueReplacementInSameFamily() {
        String rawToken = "refresh-token-1";
        String tokenHash = hmacSha256Hex(rawToken);
        String familyId = "family-1";

        RefreshToken current = RefreshToken.builder()
                .id(10L)
                .userId(42L)
                .tokenHash(tokenHash)
                .tokenFamilyId(familyId)
                .expiresAt(NOW.plusDays(30))
                .createdAt(NOW.minusDays(1))
                .build();

        when(refreshTokenRepository.findByTokenHashForUpdate(tokenHash)).thenReturn(Optional.of(current));

        RefreshTokenService.RotationResult result = refreshTokenService.rotate(rawToken);

        assertEquals(42L, result.userId());
        assertNotNull(result.refreshToken());
        assertNotEquals(rawToken, result.refreshToken());

        ArgumentCaptor<RefreshToken> saveCaptor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository, times(2)).save(saveCaptor.capture());

        List<RefreshToken> savedTokens = saveCaptor.getAllValues();
        RefreshToken revoked = savedTokens.get(0);
        RefreshToken replacement = savedTokens.get(1);

        assertSame(current, revoked);
        assertEquals(NOW, revoked.getRevokedAt());
        assertEquals(NOW, revoked.getLastUsedAt());
        assertEquals(hmacSha256Hex(result.refreshToken()), revoked.getReplacedByHash());

        assertEquals(42L, replacement.getUserId());
        assertEquals(familyId, replacement.getTokenFamilyId());
        assertEquals(revoked.getReplacedByHash(), replacement.getTokenHash());
        assertNull(replacement.getRevokedAt());
        assertNull(replacement.getLastUsedAt());
        assertNull(replacement.getReuseDetectedAt());
    }

    @Test
    void reuseOfRevokedTokenShouldRevokeAllActiveTokensInFamily() {
        String rawToken = "refresh-token-old";
        String tokenHash = hmacSha256Hex(rawToken);
        String familyId = "family-2";

        RefreshToken reusedToken = RefreshToken.builder()
                .id(20L)
                .userId(7L)
                .tokenHash(tokenHash)
                .tokenFamilyId(familyId)
                .expiresAt(NOW.plusDays(10))
                .revokedAt(NOW.minusHours(1))
                .createdAt(NOW.minusDays(2))
                .build();

        RefreshToken latestActive = RefreshToken.builder()
                .id(21L)
                .userId(7L)
                .tokenHash("active-hash")
                .tokenFamilyId(familyId)
                .expiresAt(NOW.plusDays(10))
                .createdAt(NOW.minusHours(1))
                .build();

        when(refreshTokenRepository.findByTokenHashForUpdate(tokenHash)).thenReturn(Optional.of(reusedToken));
        when(refreshTokenRepository.findAllByTokenFamilyIdAndRevokedAtIsNullForUpdate(familyId))
                .thenReturn(List.of(latestActive));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> refreshTokenService.rotate(rawToken)
        );

        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
        assertEquals("Refresh token reuse detected", exception.getReason());
        assertEquals(NOW, reusedToken.getReuseDetectedAt());
        assertEquals(NOW, latestActive.getRevokedAt());
        verify(refreshTokenRepository).save(reusedToken);
    }

    @Test
    void latestTokenShouldNotRefreshAfterReuseDetectionRevokesItsFamily() {
        String familyId = "family-3";
        String rawOldToken = "refresh-token-reused";
        String rawLatestToken = "refresh-token-latest";
        String oldTokenHash = hmacSha256Hex(rawOldToken);
        String latestTokenHash = hmacSha256Hex(rawLatestToken);

        RefreshToken oldRevoked = RefreshToken.builder()
                .id(30L)
                .userId(8L)
                .tokenHash(oldTokenHash)
                .tokenFamilyId(familyId)
                .expiresAt(NOW.plusDays(10))
                .revokedAt(NOW.minusMinutes(30))
                .createdAt(NOW.minusDays(5))
                .build();

        RefreshToken latestActive = RefreshToken.builder()
                .id(31L)
                .userId(8L)
                .tokenHash(latestTokenHash)
                .tokenFamilyId(familyId)
                .expiresAt(NOW.plusDays(10))
                .createdAt(NOW.minusMinutes(30))
                .build();

        when(refreshTokenRepository.findByTokenHashForUpdate(oldTokenHash)).thenReturn(Optional.of(oldRevoked));
        when(refreshTokenRepository.findByTokenHashForUpdate(latestTokenHash)).thenReturn(Optional.of(latestActive));
        when(refreshTokenRepository.findAllByTokenFamilyIdAndRevokedAtIsNullForUpdate(eq(familyId)))
                .thenAnswer(invocation -> latestActive.getRevokedAt() == null ? List.of(latestActive) : List.of());

        assertThrows(ResponseStatusException.class, () -> refreshTokenService.rotate(rawOldToken));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> refreshTokenService.rotate(rawLatestToken)
        );

        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
        assertEquals("Refresh token reuse detected", exception.getReason());
        assertEquals(NOW, latestActive.getRevokedAt());
    }

    @Test
    void logoutAllShouldStillRevokeAllActiveTokensForUser() {
        String rawToken = "refresh-token-reference";
        String tokenHash = hmacSha256Hex(rawToken);

        RefreshToken referenceToken = RefreshToken.builder()
                .id(40L)
                .userId(11L)
                .tokenHash(tokenHash)
                .tokenFamilyId("family-4")
                .expiresAt(NOW.plusDays(5))
                .createdAt(NOW.minusDays(1))
                .build();

        RefreshToken activeA = RefreshToken.builder()
                .id(41L)
                .userId(11L)
                .tokenHash("hash-a")
                .tokenFamilyId("family-4")
                .expiresAt(NOW.plusDays(5))
                .createdAt(NOW.minusHours(5))
                .build();

        RefreshToken activeB = RefreshToken.builder()
                .id(42L)
                .userId(11L)
                .tokenHash("hash-b")
                .tokenFamilyId("family-5")
                .expiresAt(NOW.plusDays(5))
                .createdAt(NOW.minusHours(2))
                .build();

        when(refreshTokenRepository.findByTokenHashForUpdate(tokenHash)).thenReturn(Optional.of(referenceToken));
        when(refreshTokenRepository.findAllByUserIdAndRevokedAtIsNull(11L)).thenReturn(List.of(activeA, activeB));

        refreshTokenService.revokeAllByRefresh(rawToken);

        assertEquals(NOW, activeA.getRevokedAt());
        assertEquals(NOW, activeB.getRevokedAt());
    }

    @Test
    void constructorShouldRejectBlankPepper() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new RefreshTokenService(
                        refreshTokenRepository,
                        1_000L,
                        " ",
                        FIXED_CLOCK,
                        new SecureRandom()
                )
        );

        assertEquals("auth.refresh-token.pepper must not be blank", exception.getMessage());
    }

    private String hmacSha256Hex(String rawToken) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(PEPPER.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(rawToken.getBytes(StandardCharsets.UTF_8));
            return toHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException(e);
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
}
