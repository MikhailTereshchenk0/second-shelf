package com.secondshelf.notificationservice.controller;

import com.secondshelf.notificationservice.config.SecurityConfig;
import com.secondshelf.notificationservice.dto.DlqRedriveResponse;
import com.secondshelf.notificationservice.exception.handler.GlobalExceptionHandler;
import com.secondshelf.notificationservice.observability.CorrelationIdFilter;
import com.secondshelf.notificationservice.security.JwtAuthenticationFilter;
import com.secondshelf.notificationservice.service.NotificationDlqRedriveService;
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
import java.util.Date;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminDlqController.class)
@Import({SecurityConfig.class, CorrelationIdFilter.class, JwtAuthenticationFilter.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = {
        "jwt.secret=test-secret-test-secret-test-secret-12345678"
})
class AdminDlqControllerTest {

    private static final String JWT_SECRET = "test-secret-test-secret-test-secret-12345678";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NotificationDlqRedriveService notificationDlqRedriveService;

    @Test
    void redriveShouldRejectNonAdmin() throws Exception {
        mockMvc.perform(post("/api/v1/admin/notifications/dlq/redrive?limit=5")
                        .header(HttpHeaders.AUTHORIZATION, bearer(jwtFor(42L, "alice", List.of("ROLE_USER")))))
                .andExpect(status().isForbidden());

        verifyNoInteractions(notificationDlqRedriveService);
    }

    @Test
    void redriveShouldAllowAdmin() throws Exception {
        DlqRedriveResponse response = DlqRedriveResponse.builder()
                .requestedLimit(5)
                .redrivenCount(2)
                .skippedCount(0)
                .failedCount(0)
                .errors(List.of())
                .build();

        when(notificationDlqRedriveService.redrive(5)).thenReturn(response);

        mockMvc.perform(post("/api/v1/admin/notifications/dlq/redrive?limit=5")
                        .header(HttpHeaders.AUTHORIZATION, bearer(jwtFor(99L, "admin", List.of("ROLE_ADMIN")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestedLimit").value(5))
                .andExpect(jsonPath("$.redrivenCount").value(2))
                .andExpect(jsonPath("$.skippedCount").value(0))
                .andExpect(jsonPath("$.failedCount").value(0));

        verify(notificationDlqRedriveService).redrive(5);
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
