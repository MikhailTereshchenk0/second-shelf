package com.secondshelf.exchangeservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.secondshelf.exchangeservice.config.SecurityConfig;
import com.secondshelf.exchangeservice.dto.CreateExchangeRequest;
import com.secondshelf.exchangeservice.dto.ExchangeResponse;
import com.secondshelf.exchangeservice.entity.ExchangeStatus;
import com.secondshelf.exchangeservice.security.JwtAuthenticationFilter;
import com.secondshelf.exchangeservice.security.UserPrincipal;
import com.secondshelf.exchangeservice.service.ExchangeService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ExchangeController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
@TestPropertySource(properties = {
        "jwt.secret=test-secret-test-secret-test-secret-12345678"
})
class ExchangeControllerTest {

    private static final String JWT_SECRET = "test-secret-test-secret-test-secret-12345678";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ExchangeService exchangeService;

    @Test
    void createShouldPassAuthenticatedPrincipalFromJwt() throws Exception {
        // arrange
        CreateExchangeRequest request = new CreateExchangeRequest();
        request.setRequestedBookId(100L);
        request.setMessage("Let's exchange");

        ExchangeResponse response = ExchangeResponse.builder()
                .id(1L)
                .requestedBookId(100L)
                .ownerId(55L)
                .requesterId(42L)
                .status(ExchangeStatus.PENDING)
                .message("Let's exchange")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(exchangeService.create(any(CreateExchangeRequest.class), any(UserPrincipal.class))).thenReturn(response);

        // act + assert
        mockMvc.perform(post("/api/v1/exchanges")
                        .header(HttpHeaders.AUTHORIZATION, bearer(jwtFor(42L, "alice", List.of("ROLE_USER"))))
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.requestedBookId").value(100))
                .andExpect(jsonPath("$.requesterId").value(42))
                .andExpect(jsonPath("$.status").value("PENDING"));

        verify(exchangeService).create(any(CreateExchangeRequest.class), eq(new UserPrincipal(42L, "alice")));
    }

    @Test
    void createShouldReturnBadRequestWhenRequestedBookIdIsMissing() throws Exception {
        // arrange
        String requestBody = """
                {
                  "message": "Let's exchange"
                }
                """;

        // act + assert
        mockMvc.perform(post("/api/v1/exchanges")
                        .header(HttpHeaders.AUTHORIZATION, bearer(jwtFor(42L, "alice", List.of("ROLE_USER"))))
                        .contentType(APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    void outgoingShouldReturnRequestsOfCurrentAuthenticatedUser() throws Exception {
        // arrange
        ExchangeResponse response = ExchangeResponse.builder()
                .id(2L)
                .requestedBookId(101L)
                .ownerId(55L)
                .requesterId(42L)
                .status(ExchangeStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(exchangeService.myOutgoing(any(UserPrincipal.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(response)));

        // act + assert
        mockMvc.perform(get("/api/v1/exchanges/my/outgoing")
                        .header(HttpHeaders.AUTHORIZATION, bearer(jwtFor(42L, "alice", List.of("ROLE_USER")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(2))
                .andExpect(jsonPath("$.content[0].requesterId").value(42))
                .andExpect(jsonPath("$.content[0].status").value("PENDING"));

        verify(exchangeService).myOutgoing(eq(new UserPrincipal(42L, "alice")), any(Pageable.class));
    }

    @Test
    void incomingShouldReturnRequestsOfCurrentAuthenticatedOwner() throws Exception {
        // arrange
        ExchangeResponse response = ExchangeResponse.builder()
                .id(3L)
                .requestedBookId(102L)
                .ownerId(55L)
                .requesterId(42L)
                .status(ExchangeStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(exchangeService.myIncoming(any(UserPrincipal.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(response)));

        // act + assert
        mockMvc.perform(get("/api/v1/exchanges/my/incoming")
                        .header(HttpHeaders.AUTHORIZATION, bearer(jwtFor(55L, "owner", List.of("ROLE_USER")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(3))
                .andExpect(jsonPath("$.content[0].ownerId").value(55))
                .andExpect(jsonPath("$.content[0].status").value("PENDING"));

        verify(exchangeService).myIncoming(eq(new UserPrincipal(55L, "owner")), any(Pageable.class));
    }

    @Test
    void acceptShouldPassAuthenticatedPrincipalFromJwt() throws Exception {
        // arrange
        ExchangeResponse response = ExchangeResponse.builder()
                .id(10L)
                .requestedBookId(100L)
                .ownerId(55L)
                .requesterId(42L)
                .status(ExchangeStatus.ACCEPTED)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(exchangeService.accept(10L, new UserPrincipal(55L, "owner"))).thenReturn(response);

        // act + assert
        mockMvc.perform(post("/api/v1/exchanges/10/accept")
                        .header(HttpHeaders.AUTHORIZATION, bearer(jwtFor(55L, "owner", List.of("ROLE_USER")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.status").value("ACCEPTED"));

        verify(exchangeService).accept(10L, new UserPrincipal(55L, "owner"));
    }

    @Test
    void cancelShouldPassAuthenticatedPrincipalFromJwt() throws Exception {
        // arrange
        ExchangeResponse response = ExchangeResponse.builder()
                .id(11L)
                .requestedBookId(101L)
                .ownerId(55L)
                .requesterId(42L)
                .status(ExchangeStatus.CANCELLED)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(exchangeService.cancel(11L, new UserPrincipal(42L, "alice"))).thenReturn(response);

        // act + assert
        mockMvc.perform(post("/api/v1/exchanges/11/cancel")
                        .header(HttpHeaders.AUTHORIZATION, bearer(jwtFor(42L, "alice", List.of("ROLE_USER")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(11))
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        verify(exchangeService).cancel(11L, new UserPrincipal(42L, "alice"));
    }

    @Test
    void completeShouldPassAuthenticatedPrincipalFromJwt() throws Exception {
        // arrange
        ExchangeResponse response = ExchangeResponse.builder()
                .id(12L)
                .requestedBookId(102L)
                .ownerId(55L)
                .requesterId(42L)
                .status(ExchangeStatus.COMPLETED)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(exchangeService.complete(12L, new UserPrincipal(55L, "owner"))).thenReturn(response);

        // act + assert
        mockMvc.perform(post("/api/v1/exchanges/12/complete")
                        .header(HttpHeaders.AUTHORIZATION, bearer(jwtFor(55L, "owner", List.of("ROLE_USER")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(12))
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        verify(exchangeService).complete(12L, new UserPrincipal(55L, "owner"));
    }

    @Test
    void protectedEndpointShouldReturnUnauthorizedWithoutJwt() throws Exception {
        // act + assert
        mockMvc.perform(get("/api/v1/exchanges/my/outgoing"))
                .andExpect(status().isUnauthorized());
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