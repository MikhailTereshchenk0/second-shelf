package com.secondshelf.authservice.ratelimit;

import com.secondshelf.authservice.dto.LoginRequest;
import com.secondshelf.authservice.dto.RefreshRequest;
import com.secondshelf.authservice.dto.RegisterRequest;
import com.secondshelf.observability.AuditEvent;
import com.secondshelf.observability.AuditLogger;
import com.secondshelf.observability.AuditOutcome;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.Locale;

@Service
public class AuthRateLimitService {

    private static final AuditLogger AUDIT_LOGGER = AuditLogger.forClass(AuthRateLimitService.class);
    private static final String RATE_LIMIT_ERROR_CODE = "AUTH_RATE_LIMIT_EXCEEDED";
    private static final String RATE_LIMIT_MESSAGE = "Authentication rate limit exceeded. Please retry later.";

    private final AuthRateLimitProperties properties;
    private final InMemoryTokenBucketRateLimiter loginLimiter;
    private final InMemoryTokenBucketRateLimiter registerLimiter;
    private final InMemoryTokenBucketRateLimiter refreshLimiter;

    @Autowired
    public AuthRateLimitService(AuthRateLimitProperties properties) {
        this(properties, Clock.systemUTC());
    }

    AuthRateLimitService(AuthRateLimitProperties properties, Clock clock) {
        this.properties = properties;
        this.loginLimiter = createLimiter(properties.getLogin(), clock);
        this.registerLimiter = createLimiter(properties.getRegister(), clock);
        this.refreshLimiter = createLimiter(properties.getRefresh(), clock);
    }

    public void checkLogin(HttpServletRequest httpRequest, LoginRequest request) {
        if (!properties.isEnabled()) {
            return;
        }

        String clientIp = resolveClientIp(httpRequest);
        String username = normalizeUsername(request.getUsername());
        InMemoryTokenBucketRateLimiter.RateLimitDecision decision =
                loginLimiter.tryConsume(username + "|" + clientIp);

        if (!decision.allowed()) {
            auditExceeded("/api/auth/login", clientIp, username, null);
            throw new AuthRateLimitExceededException(RATE_LIMIT_MESSAGE, decision.retryAfterSeconds());
        }
    }

    public void checkRegister(HttpServletRequest httpRequest, RegisterRequest request) {
        if (!properties.isEnabled()) {
            return;
        }

        String clientIp = resolveClientIp(httpRequest);
        InMemoryTokenBucketRateLimiter.RateLimitDecision decision = registerLimiter.tryConsume(clientIp);

        if (!decision.allowed()) {
            auditExceeded("/api/auth/register", clientIp, normalizeUsername(request.getUsername()), null);
            throw new AuthRateLimitExceededException(RATE_LIMIT_MESSAGE, decision.retryAfterSeconds());
        }
    }

    public void checkRefresh(HttpServletRequest httpRequest, RefreshRequest request) {
        if (!properties.isEnabled()) {
            return;
        }

        String clientIp = resolveClientIp(httpRequest);
        String fingerprint = properties.getRefresh().isIncludeTokenFingerprint()
                ? fingerprint(request.getRefreshToken())
                : null;

        String key = clientIp;
        if (fingerprint != null) {
            key = key + "|" + fingerprint;
        }

        InMemoryTokenBucketRateLimiter.RateLimitDecision decision = refreshLimiter.tryConsume(key);

        if (!decision.allowed()) {
            auditExceeded("/api/auth/refresh", clientIp, null, fingerprint);
            throw new AuthRateLimitExceededException(RATE_LIMIT_MESSAGE, decision.retryAfterSeconds());
        }
    }

    private InMemoryTokenBucketRateLimiter createLimiter(AuthRateLimitProperties.EndpointLimit limit, Clock clock) {
        return new InMemoryTokenBucketRateLimiter(
                new InMemoryTokenBucketRateLimiter.TokenBucketRule(
                        limit.getCapacity(),
                        limit.getRefillTokens(),
                        limit.getRefillPeriod()
                ),
                clock
        );
    }

    private void auditExceeded(String endpoint, String clientIp, String username, String refreshFingerprint) {
        AuditEvent.Builder event = AuditEvent.builder("AUTH_RATE_LIMIT_EXCEEDED", AuditOutcome.FAILURE)
                .reason("Authentication rate limit exceeded")
                .errorCode(RATE_LIMIT_ERROR_CODE)
                .attribute("endpoint", endpoint)
                .attribute("clientIp", clientIp);

        if (StringUtils.hasText(username)) {
            event.attribute("username", username);
        }
        if (StringUtils.hasText(refreshFingerprint)) {
            event.attribute("refreshFingerprint", refreshFingerprint);
        }

        AUDIT_LOGGER.log(event.build());
    }

    private String normalizeUsername(String username) {
        if (!StringUtils.hasText(username)) {
            return "anonymous";
        }
        return username.trim().toLowerCase(Locale.ROOT);
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwardedFor = firstNonBlankCsvValue(request.getHeader("X-Forwarded-For"));
        if (StringUtils.hasText(forwardedFor)) {
            return forwardedFor;
        }

        String forwarded = parseForwardedHeader(request.getHeader("Forwarded"));
        if (StringUtils.hasText(forwarded)) {
            return forwarded;
        }

        String realIp = request.getHeader("X-Real-IP");
        if (StringUtils.hasText(realIp)) {
            return realIp.trim();
        }

        String remoteAddr = request.getRemoteAddr();
        return StringUtils.hasText(remoteAddr) ? remoteAddr.trim() : "unknown";
    }

    private String firstNonBlankCsvValue(String headerValue) {
        if (!StringUtils.hasText(headerValue)) {
            return null;
        }

        for (String value : headerValue.split(",")) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }

        return null;
    }

    private String parseForwardedHeader(String headerValue) {
        if (!StringUtils.hasText(headerValue)) {
            return null;
        }

        for (String part : headerValue.split(";")) {
            String trimmed = part.trim();
            if (!trimmed.regionMatches(true, 0, "for=", 0, 4)) {
                continue;
            }

            String candidate = trimmed.substring(4).trim();
            if (candidate.startsWith("\"") && candidate.endsWith("\"") && candidate.length() > 1) {
                candidate = candidate.substring(1, candidate.length() - 1);
            }
            if (candidate.startsWith("[") && candidate.endsWith("]") && candidate.length() > 1) {
                candidate = candidate.substring(1, candidate.length() - 1);
            }
            return candidate;
        }

        return null;
    }

    private String fingerprint(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }
}
