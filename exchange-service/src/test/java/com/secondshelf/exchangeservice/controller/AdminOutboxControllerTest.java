package com.secondshelf.exchangeservice.controller;

import com.secondshelf.exchangeservice.config.SecurityConfig;
import com.secondshelf.exchangeservice.dto.OutboxEventSummaryResponse;
import com.secondshelf.exchangeservice.dto.OutboxRetryResponse;
import com.secondshelf.exchangeservice.entity.OutboxEventStatus;
import com.secondshelf.exchangeservice.exception.ExchangeConflictException;
import com.secondshelf.exchangeservice.exception.handler.GlobalExceptionHandler;
import com.secondshelf.exchangeservice.observability.CorrelationIdFilter;
import com.secondshelf.exchangeservice.security.JwtAuthenticationFilter;
import com.secondshelf.exchangeservice.service.ExchangeOutboxAdminService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminOutboxController.class)
@Import({SecurityConfig.class, CorrelationIdFilter.class, JwtAuthenticationFilter.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = {
        "jwt.secret=test-secret-test-secret-test-secret-12345678"
})
class AdminOutboxControllerTest {

    private static final String JWT_SECRET = "test-secret-test-secret-test-secret-12345678";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ExchangeOutboxAdminService exchangeOutboxAdminService;

    @Test
    void terminalFailedEventsShouldAllowAdmin() throws Exception {
        UUID eventId = UUID.randomUUID();
        OutboxEventSummaryResponse response = OutboxEventSummaryResponse.builder()
                .id(10L)
                .eventId(eventId)
                .aggregateType("EXCHANGE_REQUEST")
                .aggregateId("42")
                .eventType("exchange.request.completed")
                .status(OutboxEventStatus.TERMINAL_FAILED)
                .attemptsCount(5)
                .manualRetryCount(1)
                .failedAt(LocalDateTime.of(2026, 5, 26, 14, 15))
                .errorCode("AmqpException")
                .lastError("AmqpException: RabbitMQ is unavailable")
                .build();

        when(exchangeOutboxAdminService.findTerminalFailedEvents()).thenReturn(List.of(response));

        mockMvc.perform(get("/api/v1/admin/outbox/terminal-failed")
                        .header(HttpHeaders.AUTHORIZATION, bearer(jwtFor(99L, "admin", List.of("ROLE_ADMIN")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].eventId").value(eventId.toString()))
                .andExpect(jsonPath("$[0].status").value("TERMINAL_FAILED"))
                .andExpect(jsonPath("$[0].lastError").value("AmqpException: RabbitMQ is unavailable"))
                .andExpect(jsonPath("$[0].payload").doesNotExist());

        verify(exchangeOutboxAdminService).findTerminalFailedEvents();
    }

    @Test
    void terminalFailedEventsShouldRejectNonAdmin() throws Exception {
        mockMvc.perform(get("/api/v1/admin/outbox/terminal-failed")
                        .header(HttpHeaders.AUTHORIZATION, bearer(jwtFor(42L, "alice", List.of("ROLE_USER")))))
                .andExpect(status().isForbidden());

        verifyNoInteractions(exchangeOutboxAdminService);
    }

    @Test
    void retryShouldReturnRequeuedEventForAdmin() throws Exception {
        UUID eventId = UUID.randomUUID();
        OutboxEventSummaryResponse event = OutboxEventSummaryResponse.builder()
                .eventId(eventId)
                .status(OutboxEventStatus.PENDING)
                .nextAttemptAt(LocalDateTime.of(2026, 5, 26, 14, 20))
                .manualRetryCount(2)
                .build();

        when(exchangeOutboxAdminService.retryTerminalFailedEvent(eventId))
                .thenReturn(OutboxRetryResponse.builder()
                        .event(event)
                        .message("Outbox event was re-queued for the scheduled publisher.")
                        .build());

        mockMvc.perform(post("/api/v1/admin/outbox/{eventId}/retry", eventId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(jwtFor(99L, "admin", List.of("ROLE_ADMIN")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.event.eventId").value(eventId.toString()))
                .andExpect(jsonPath("$.event.status").value("PENDING"))
                .andExpect(jsonPath("$.event.manualRetryCount").value(2))
                .andExpect(jsonPath("$.message").value("Outbox event was re-queued for the scheduled publisher."));

        verify(exchangeOutboxAdminService).retryTerminalFailedEvent(eventId);
    }

    @Test
    void retryShouldReturnConflictForNonTerminalEvent() throws Exception {
        UUID eventId = UUID.randomUUID();
        when(exchangeOutboxAdminService.retryTerminalFailedEvent(eventId))
                .thenThrow(new ExchangeConflictException(
                        "OUTBOX_EVENT_NOT_TERMINAL_FAILED",
                        "Only terminally failed outbox events can be retried manually."
                ));

        mockMvc.perform(post("/api/v1/admin/outbox/{eventId}/retry", eventId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(jwtFor(99L, "admin", List.of("ROLE_ADMIN")))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("OUTBOX_EVENT_NOT_TERMINAL_FAILED"));
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private String jwtFor(Long userId, String username, List<String> roles) {
        SecretKey key = Keys.hmacShaKeyFor(JWT_SECRET.getBytes(StandardCharsets.UTF_8));

        return Jwts.builder()
                .subject(username)
                .claim("userId", userId)
                .claim("roles", roles)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60 * 60 * 1000))
                .signWith(key)
                .compact();
    }
}
