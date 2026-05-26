package com.secondshelf.notificationservice.controller;

import com.secondshelf.notificationservice.config.SecurityConfig;
import com.secondshelf.notificationservice.dto.NotificationResponse;
import com.secondshelf.notificationservice.entity.NotificationStatus;
import com.secondshelf.notificationservice.entity.NotificationType;
import com.secondshelf.notificationservice.exception.NotificationNotFoundException;
import com.secondshelf.notificationservice.exception.handler.GlobalExceptionHandler;
import com.secondshelf.notificationservice.observability.CorrelationId;
import com.secondshelf.notificationservice.observability.CorrelationIdFilter;
import com.secondshelf.notificationservice.security.JwtAuthenticationFilter;
import com.secondshelf.notificationservice.security.UserPrincipal;
import com.secondshelf.notificationservice.service.NotificationService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NotificationController.class)
@Import({SecurityConfig.class, CorrelationIdFilter.class, JwtAuthenticationFilter.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = {
        "jwt.secret=test-secret-test-secret-test-secret-12345678"
})
class NotificationControllerTest {

    private static final String JWT_SECRET = "test-secret-test-secret-test-secret-12345678";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NotificationService notificationService;

    @Test
    void getMyNotificationsShouldPassAuthenticatedPrincipalFromJwt() throws Exception {
        // arrange
        NotificationResponse response = NotificationResponse.builder()
                .id(1L)
                .userId(42L)
                .type(NotificationType.EXCHANGE_REQUEST_CREATED)
                .title("New exchange request")
                .message("Someone wants to exchange a book with you.")
                .status(NotificationStatus.UNREAD)
                .relatedEntityType("EXCHANGE_REQUEST")
                .relatedEntityId("100")
                .createdAt(LocalDateTime.now())
                .build();

        when(notificationService.getMyNotifications(any(UserPrincipal.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(response)));

        // act + assert
        mockMvc.perform(get("/api/v1/notifications")
                        .header(HttpHeaders.AUTHORIZATION, bearer(jwtFor(42L, "alice", List.of("ROLE_USER"))))
                        .header(CorrelationId.HEADER_NAME, "corr-http-notification-123"))
                .andExpect(status().isOk())
                .andExpect(header().string(CorrelationId.HEADER_NAME, "corr-http-notification-123"))
                .andExpect(jsonPath("$.content[0].id").value(1))
                .andExpect(jsonPath("$.content[0].userId").value(42))
                .andExpect(jsonPath("$.content[0].type").value("EXCHANGE_REQUEST_CREATED"))
                .andExpect(jsonPath("$.content[0].status").value("UNREAD"));

        verify(notificationService).getMyNotifications(eq(new UserPrincipal(42L, "alice")), any(Pageable.class));
    }

    @Test
    void getMyNotificationsShouldClampPageSizeAndAllowWhitelistedSort() throws Exception {
        when(notificationService.getMyNotifications(any(UserPrincipal.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/v1/notifications")
                        .header(HttpHeaders.AUTHORIZATION, bearer(jwtFor(42L, "alice", List.of("ROLE_USER"))))
                        .param("size", "250")
                        .param("sort", "status,desc"))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(notificationService).getMyNotifications(eq(new UserPrincipal(42L, "alice")), pageableCaptor.capture());

        Pageable pageable = pageableCaptor.getValue();
        assertThat(pageable.getPageSize()).isEqualTo(100);
        assertThat(pageable.getSort().getOrderFor("status")).isNotNull();
    }

    @Test
    void getUnreadCountShouldReturnCountForAuthenticatedUser() throws Exception {
        // arrange
        when(notificationService.getUnreadCount(new UserPrincipal(42L, "alice"))).thenReturn(5L);

        // act + assert
        mockMvc.perform(get("/api/v1/notifications/unread-count")
                        .header(HttpHeaders.AUTHORIZATION, bearer(jwtFor(42L, "alice", List.of("ROLE_USER")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(5));

        verify(notificationService).getUnreadCount(new UserPrincipal(42L, "alice"));
    }

    @Test
    void markAsReadShouldReturnNotificationForAuthenticatedUser() throws Exception {
        // arrange
        NotificationResponse response = NotificationResponse.builder()
                .id(10L)
                .userId(42L)
                .type(NotificationType.EXCHANGE_REQUEST_ACCEPTED)
                .title("Request accepted")
                .message("Your request was accepted.")
                .status(NotificationStatus.READ)
                .createdAt(LocalDateTime.now().minusHours(2))
                .readAt(LocalDateTime.now())
                .build();

        when(notificationService.markAsRead(10L, new UserPrincipal(42L, "alice"))).thenReturn(response);

        // act + assert
        mockMvc.perform(post("/api/v1/notifications/10/read")
                        .header(HttpHeaders.AUTHORIZATION, bearer(jwtFor(42L, "alice", List.of("ROLE_USER")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.status").value("READ"));

        verify(notificationService).markAsRead(10L, new UserPrincipal(42L, "alice"));
    }

    @Test
    void markAsReadShouldReturnNotFoundContract() throws Exception {
        // arrange
        when(notificationService.markAsRead(10L, new UserPrincipal(42L, "alice")))
                .thenThrow(new NotificationNotFoundException("NOTIFICATION_NOT_FOUND", "Notification not found."));

        // act + assert
        mockMvc.perform(post("/api/v1/notifications/10/read")
                        .header(HttpHeaders.AUTHORIZATION, bearer(jwtFor(42L, "alice", List.of("ROLE_USER")))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("NOTIFICATION_NOT_FOUND"))
                .andExpect(jsonPath("$.path").value("/api/v1/notifications/10/read"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Notification not found."))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void markAsReadShouldReturnBadRequestForInvalidNotificationId() throws Exception {
        // act + assert
        mockMvc.perform(post("/api/v1/notifications/not-a-number/read")
                        .header(HttpHeaders.AUTHORIZATION, bearer(jwtFor(42L, "alice", List.of("ROLE_USER")))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST_PARAMETER"))
                .andExpect(jsonPath("$.message").value("Invalid request parameter: id"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void markAllAsReadShouldReturnNoContent() throws Exception {
        // act + assert
        mockMvc.perform(post("/api/v1/notifications/read-all")
                        .header(HttpHeaders.AUTHORIZATION, bearer(jwtFor(42L, "alice", List.of("ROLE_USER")))))
                .andExpect(status().isNoContent());

        verify(notificationService).markAllAsRead(new UserPrincipal(42L, "alice"));
    }

    @Test
    void protectedEndpointShouldReturnUnauthorizedWithoutJwt() throws Exception {
        // act + assert
        mockMvc.perform(get("/api/v1/notifications"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getUnreadCountShouldGenerateCorrelationIdWhenHeaderIsMissing() throws Exception {
        // arrange
        when(notificationService.getUnreadCount(new UserPrincipal(42L, "alice"))).thenReturn(5L);

        // act + assert
        mockMvc.perform(get("/api/v1/notifications/unread-count")
                        .header(HttpHeaders.AUTHORIZATION, bearer(jwtFor(42L, "alice", List.of("ROLE_USER")))))
                .andExpect(status().isOk())
                .andExpect(header().exists(CorrelationId.HEADER_NAME));
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
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(key)
                .compact();
    }
}
