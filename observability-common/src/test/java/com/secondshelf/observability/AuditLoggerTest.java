package com.secondshelf.observability;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuditLoggerTest {

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void shouldMaskSensitiveValues() {
        Logger logger = (Logger) LoggerFactory.getLogger(AuditLoggerTest.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        MDC.put("correlationId", "corr-audit-mask-123");

        AuditLogger auditLogger = AuditLogger.forClass(AuditLoggerTest.class);
        auditLogger.log(AuditEvent.builder("TEST_AUDIT", AuditOutcome.FAILURE)
                .reason("login failed")
                .attribute("password", "SecretPassword123")
                .attribute("refreshToken", "raw-refresh-token")
                .attribute("Authorization", "Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhbGljZSJ9.signature")
                .attribute("dbPassword", "postgres-password")
                .attribute("rabbitmqPassword", "rabbit-password")
                .build());

        String message = appender.list.get(0).getFormattedMessage();

        assertTrue(message.contains("correlationId=corr-audit-mask-123"));
        assertTrue(message.contains("password=[REDACTED]"));
        assertTrue(message.contains("refreshToken=[REDACTED]"));
        assertTrue(message.contains("Authorization=[REDACTED]"));
        assertTrue(message.contains("dbPassword=[REDACTED]"));
        assertTrue(message.contains("rabbitmqPassword=[REDACTED]"));
        assertFalse(message.contains("SecretPassword123"));
        assertFalse(message.contains("raw-refresh-token"));
        assertFalse(message.contains("eyJhbGciOiJIUzI1NiJ9"));
        assertFalse(message.contains("postgres-password"));
        assertFalse(message.contains("rabbit-password"));

        logger.detachAppender(appender);
    }
}
