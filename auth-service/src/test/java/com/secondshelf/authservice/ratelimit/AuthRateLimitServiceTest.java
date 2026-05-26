package com.secondshelf.authservice.ratelimit;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.secondshelf.authservice.dto.LoginRequest;
import com.secondshelf.authservice.dto.RefreshRequest;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthRateLimitServiceTest {

    @Test
    void rateLimitAuditLogsShouldNotContainPasswordOrRawRefreshToken() {
        AuthRateLimitProperties properties = new AuthRateLimitProperties();
        properties.getLogin().setCapacity(1);
        properties.getLogin().setRefillTokens(1);
        properties.getLogin().setRefillPeriod(Duration.ofMinutes(1));
        properties.getRefresh().setCapacity(1);
        properties.getRefresh().setRefillTokens(1);
        properties.getRefresh().setRefillPeriod(Duration.ofMinutes(1));
        properties.getRefresh().setIncludeTokenFingerprint(true);

        AuthRateLimitService rateLimitService = new AuthRateLimitService(
                properties,
                Clock.fixed(Instant.parse("2026-05-26T10:15:30Z"), ZoneOffset.UTC)
        );

        Logger logger = (Logger) LoggerFactory.getLogger(AuthRateLimitService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        try {
            MockHttpServletRequest loginHttpRequest = new MockHttpServletRequest();
            loginHttpRequest.setRemoteAddr("203.0.113.40");

            LoginRequest loginRequest = new LoginRequest();
            loginRequest.setUsername("audit-user");
            loginRequest.setPassword("Sup3rSecret!");

            rateLimitService.checkLogin(loginHttpRequest, loginRequest);
            assertThrows(
                    AuthRateLimitExceededException.class,
                    () -> rateLimitService.checkLogin(loginHttpRequest, loginRequest)
            );

            MockHttpServletRequest refreshHttpRequest = new MockHttpServletRequest();
            refreshHttpRequest.setRemoteAddr("203.0.113.41");

            RefreshRequest refreshRequest = new RefreshRequest();
            refreshRequest.setRefreshToken("very-sensitive-refresh-token");

            rateLimitService.checkRefresh(refreshHttpRequest, refreshRequest);
            assertThrows(
                    AuthRateLimitExceededException.class,
                    () -> rateLimitService.checkRefresh(refreshHttpRequest, refreshRequest)
            );
        } finally {
            logger.detachAppender(appender);
        }

        String combinedLogs = appender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .reduce("", (left, right) -> left + "\n" + right);

        assertTrue(combinedLogs.contains("eventType=AUTH_RATE_LIMIT_EXCEEDED"));
        assertFalse(combinedLogs.contains("Sup3rSecret!"));
        assertFalse(combinedLogs.contains("very-sensitive-refresh-token"));
        assertFalse(combinedLogs.contains("password="));
        assertFalse(combinedLogs.contains("refreshToken="));
    }
}
