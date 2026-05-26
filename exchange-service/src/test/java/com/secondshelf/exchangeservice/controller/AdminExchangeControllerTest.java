package com.secondshelf.exchangeservice.controller;

import com.secondshelf.exchangeservice.config.SecurityConfig;
import com.secondshelf.exchangeservice.dto.ExchangeResponse;
import com.secondshelf.exchangeservice.entity.ExchangeStatus;
import com.secondshelf.exchangeservice.exception.handler.GlobalExceptionHandler;
import com.secondshelf.exchangeservice.observability.CorrelationIdFilter;
import com.secondshelf.exchangeservice.security.JwtAuthenticationFilter;
import com.secondshelf.exchangeservice.security.UserPrincipal;
import com.secondshelf.exchangeservice.service.ExchangeService;
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

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminExchangeController.class)
@Import({SecurityConfig.class, CorrelationIdFilter.class, JwtAuthenticationFilter.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = {
        "jwt.secret=test-secret-test-secret-test-secret-12345678"
})
class AdminExchangeControllerTest {

    private static final String JWT_SECRET = "test-secret-test-secret-test-secret-12345678";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ExchangeService exchangeService;

    @Test
    void repairShouldRejectNonAdmin() throws Exception {
        mockMvc.perform(post("/api/v1/admin/exchanges/10/repair")
                        .header(HttpHeaders.AUTHORIZATION, bearer(jwtFor(42L, "alice", List.of("ROLE_USER")))))
                .andExpect(status().isForbidden());

        verifyNoInteractions(exchangeService);
    }

    @Test
    void repairShouldAllowAdmin() throws Exception {
        ExchangeResponse response = ExchangeResponse.builder()
                .id(10L)
                .status(ExchangeStatus.COMPLETED)
                .repairAttempts(1)
                .lastRepairAttemptAt(LocalDateTime.of(2026, 5, 26, 13, 45))
                .build();

        when(exchangeService.repair(10L, new UserPrincipal(99L, "admin"))).thenReturn(response);

        mockMvc.perform(post("/api/v1/admin/exchanges/10/repair")
                        .header(HttpHeaders.AUTHORIZATION, bearer(jwtFor(99L, "admin", List.of("ROLE_ADMIN")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.repairAttempts").value(1));

        verify(exchangeService).repair(10L, new UserPrincipal(99L, "admin"));
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
