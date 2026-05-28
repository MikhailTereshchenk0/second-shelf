package com.secondshelf.exchangeservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.secondshelf.exchangeservice.config.SecurityConfig;
import com.secondshelf.exchangeservice.dto.ExchangeResponse;
import com.secondshelf.exchangeservice.dto.OwnerOfferRequest;
import com.secondshelf.exchangeservice.entity.ExchangeStatus;
import com.secondshelf.exchangeservice.exception.ExchangeConflictException;
import com.secondshelf.exchangeservice.exception.ExchangeForbiddenException;
import com.secondshelf.exchangeservice.exception.handler.GlobalExceptionHandler;
import com.secondshelf.exchangeservice.observability.CorrelationId;
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
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ExchangeController.class)
@Import({SecurityConfig.class, CorrelationIdFilter.class, JwtAuthenticationFilter.class, GlobalExceptionHandler.class})
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
    void createShouldRequireOnlyRequestedBookIdAndPassPrincipal() throws Exception {
        String requestBody = """
                {
                  "requestedBookId": 100,
                  "message": "Let's exchange"
                }
                """;
        ExchangeResponse response = baseResponse()
                .status(ExchangeStatus.PENDING)
                .message("Let's exchange")
                .build();

        when(exchangeService.create(any(), any(UserPrincipal.class), isNull())).thenReturn(response);

        mockMvc.perform(post("/api/v1/exchanges")
                        .header(HttpHeaders.AUTHORIZATION, bearer(jwtFor(42L, "alice", List.of("ROLE_USER"))))
                        .header(CorrelationId.HEADER_NAME, "corr-http-exchange-123")
                        .contentType(APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(header().string(CorrelationId.HEADER_NAME, "corr-http-exchange-123"))
                .andExpect(jsonPath("$.requestedBookId").value(100))
                .andExpect(jsonPath("$.offeredBookId").isEmpty())
                .andExpect(jsonPath("$.requesterId").value(42))
                .andExpect(jsonPath("$.status").value("PENDING"));

        verify(exchangeService).create(any(), eq(new UserPrincipal(42L, "alice")), isNull());
    }

    @Test
    void createShouldReturnBadRequestWhenRequestedBookIdIsMissing() throws Exception {
        mockMvc.perform(post("/api/v1/exchanges")
                        .header(HttpHeaders.AUTHORIZATION, bearer(jwtFor(42L, "alice", List.of("ROLE_USER"))))
                        .contentType(APPLICATION_JSON)
                        .content("{\"message\":\"Let's exchange\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details.requestedBookId").value("must not be null"))
                .andExpect(jsonPath("$.details.offeredBookId").doesNotExist());
    }

    @Test
    void offerShouldPassOwnerOfferRequestAndPrincipal() throws Exception {
        OwnerOfferRequest request = new OwnerOfferRequest();
        request.setOfferedBookId(200L);
        ExchangeResponse response = baseResponse()
                .offeredBookId(200L)
                .offeredBookTitle("Dune")
                .offeredBookAuthor("Frank Herbert")
                .ownerUsernameSnapshot("owner")
                .requesterPhoneNumber("+375291112233")
                .status(ExchangeStatus.OWNER_OFFERED)
                .build();

        when(exchangeService.offer(eq(10L), any(OwnerOfferRequest.class), eq(new UserPrincipal(55L, "owner"))))
                .thenReturn(response);

        mockMvc.perform(post("/api/v1/exchanges/10/offer")
                        .header(HttpHeaders.AUTHORIZATION, bearer(jwtFor(55L, "owner", List.of("ROLE_USER"))))
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.offeredBookId").value(200))
                .andExpect(jsonPath("$.requesterPhoneNumber").value("+375291112233"))
                .andExpect(jsonPath("$.status").value("OWNER_OFFERED"));

        verify(exchangeService).offer(eq(10L), any(OwnerOfferRequest.class), eq(new UserPrincipal(55L, "owner")));
    }

    @Test
    void acceptShouldFinalizeOwnerOfferAsRequester() throws Exception {
        ExchangeResponse response = baseResponse()
                .offeredBookId(200L)
                .offeredBookTitle("Dune")
                .offeredBookAuthor("Frank Herbert")
                .ownerPhoneNumber("+375292223344")
                .status(ExchangeStatus.ACCEPTED)
                .build();

        when(exchangeService.accept(10L, new UserPrincipal(42L, "alice"))).thenReturn(response);

        mockMvc.perform(post("/api/v1/exchanges/10/accept")
                        .header(HttpHeaders.AUTHORIZATION, bearer(jwtFor(42L, "alice", List.of("ROLE_USER")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.ownerPhoneNumber").value("+375292223344"))
                .andExpect(jsonPath("$.status").value("ACCEPTED"));

        verify(exchangeService).accept(10L, new UserPrincipal(42L, "alice"));
    }

    @Test
    void acceptShouldReturnForbiddenWhenOwnerTriesToFinalizeOffer() throws Exception {
        when(exchangeService.accept(10L, new UserPrincipal(55L, "owner")))
                .thenThrow(new ExchangeForbiddenException("ONLY_REQUESTER_CAN_ACCEPT", "Only requester can accept owner offer."));

        mockMvc.perform(post("/api/v1/exchanges/10/accept")
                        .header(HttpHeaders.AUTHORIZATION, bearer(jwtFor(55L, "owner", List.of("ROLE_USER")))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ONLY_REQUESTER_CAN_ACCEPT"))
                .andExpect(jsonPath("$.message").value("Only requester can accept owner offer."));
    }

    @Test
    void declineOfferShouldPassRequesterPrincipal() throws Exception {
        ExchangeResponse response = baseResponse()
                .offeredBookId(200L)
                .status(ExchangeStatus.CANCELLED)
                .build();

        when(exchangeService.declineOffer(10L, new UserPrincipal(42L, "alice"))).thenReturn(response);

        mockMvc.perform(post("/api/v1/exchanges/10/decline-offer")
                        .header(HttpHeaders.AUTHORIZATION, bearer(jwtFor(42L, "alice", List.of("ROLE_USER")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.ownerPhoneNumber").isEmpty());

        verify(exchangeService).declineOffer(10L, new UserPrincipal(42L, "alice"));
    }

    @Test
    void incomingShouldSerializeRequesterContactFieldsForOwnerView() throws Exception {
        ExchangeResponse response = baseResponse()
                .requesterPhoneNumber("+375291112233")
                .status(ExchangeStatus.PENDING)
                .build();

        when(exchangeService.myIncoming(any(UserPrincipal.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(response)));

        mockMvc.perform(get("/api/v1/exchanges/my/incoming")
                        .header(HttpHeaders.AUTHORIZATION, bearer(jwtFor(55L, "owner", List.of("ROLE_USER")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].requesterPhoneNumber").value("+375291112233"))
                .andExpect(jsonPath("$.content[0].ownerPhoneNumber").isEmpty());

        verify(exchangeService).myIncoming(eq(new UserPrincipal(55L, "owner")), any(Pageable.class));
    }

    @Test
    void completeShouldReturnConflictContractForInvalidStatusTransition() throws Exception {
        when(exchangeService.complete(12L, new UserPrincipal(55L, "owner")))
                .thenThrow(new ExchangeConflictException(
                        "INVALID_EXCHANGE_STATUS_TRANSITION",
                        "Only ACCEPTED or COMPLETION_PENDING request can be completed."
                ));

        mockMvc.perform(post("/api/v1/exchanges/12/complete")
                        .header(HttpHeaders.AUTHORIZATION, bearer(jwtFor(55L, "owner", List.of("ROLE_USER")))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("INVALID_EXCHANGE_STATUS_TRANSITION"))
                .andExpect(jsonPath("$.message").value("Only ACCEPTED or COMPLETION_PENDING request can be completed."));
    }

    @Test
    void protectedEndpointShouldReturnUnauthorizedWithoutJwt() throws Exception {
        mockMvc.perform(get("/api/v1/exchanges/my/outgoing"))
                .andExpect(status().isUnauthorized());
    }

    private ExchangeResponse.ExchangeResponseBuilder baseResponse() {
        return ExchangeResponse.builder()
                .id(10L)
                .requestedBookId(100L)
                .requestedBookTitle("The Left Hand of Darkness")
                .requestedBookAuthor("Ursula K. Le Guin")
                .ownerId(55L)
                .requesterId(42L)
                .requesterUsernameSnapshot("alice")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now());
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
