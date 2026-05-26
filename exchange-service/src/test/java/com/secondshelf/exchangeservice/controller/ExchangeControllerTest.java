package com.secondshelf.exchangeservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.secondshelf.exchangeservice.config.SecurityConfig;
import com.secondshelf.exchangeservice.dto.CreateExchangeRequest;
import com.secondshelf.exchangeservice.dto.ExchangeResponse;
import com.secondshelf.exchangeservice.entity.ExchangeStatus;
import com.secondshelf.exchangeservice.exception.ExchangeBadRequestException;
import com.secondshelf.exchangeservice.exception.ExchangeConflictException;
import com.secondshelf.exchangeservice.exception.ExchangeForbiddenException;
import com.secondshelf.exchangeservice.exception.ExchangeNotFoundException;
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
    void createShouldPassAuthenticatedPrincipalFromJwt() throws Exception {
        // arrange
        CreateExchangeRequest request = new CreateExchangeRequest();
        request.setRequestedBookId(100L);
        request.setOfferedBookId(200L);
        request.setMessage("Let's exchange");

        ExchangeResponse response = ExchangeResponse.builder()
                .id(1L)
                .requestedBookId(100L)
                .requestedBookTitle("The Left Hand of Darkness")
                .requestedBookAuthor("Ursula K. Le Guin")
                .offeredBookId(200L)
                .offeredBookTitle("Dune")
                .offeredBookAuthor("Frank Herbert")
                .ownerId(55L)
                .requesterId(42L)
                .requesterUsernameSnapshot("alice")
                .status(ExchangeStatus.PENDING)
                .message("Let's exchange")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(exchangeService.create(any(CreateExchangeRequest.class), any(UserPrincipal.class))).thenReturn(response);

        // act + assert
        mockMvc.perform(post("/api/v1/exchanges")
                        .header(HttpHeaders.AUTHORIZATION, bearer(jwtFor(42L, "alice", List.of("ROLE_USER"))))
                        .header(CorrelationId.HEADER_NAME, "corr-http-exchange-123")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(header().string(CorrelationId.HEADER_NAME, "corr-http-exchange-123"))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.requestedBookId").value(100))
                .andExpect(jsonPath("$.requestedBookTitle").value("The Left Hand of Darkness"))
                .andExpect(jsonPath("$.requestedBookAuthor").value("Ursula K. Le Guin"))
                .andExpect(jsonPath("$.offeredBookId").value(200))
                .andExpect(jsonPath("$.offeredBookTitle").value("Dune"))
                .andExpect(jsonPath("$.offeredBookAuthor").value("Frank Herbert"))
                .andExpect(jsonPath("$.requesterId").value(42))
                .andExpect(jsonPath("$.requesterUsernameSnapshot").value("alice"))
                .andExpect(jsonPath("$.ownerUsernameSnapshot").isEmpty())
                .andExpect(jsonPath("$.status").value("PENDING"));

        verify(exchangeService).create(any(CreateExchangeRequest.class), eq(new UserPrincipal(42L, "alice")));
    }

    @Test
    void createShouldReturnBadRequestWhenRequiredBookIdsAreMissing() throws Exception {
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
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("Request validation failed"))
                .andExpect(jsonPath("$.details.requestedBookId").value("must not be null"))
                .andExpect(jsonPath("$.details.offeredBookId").value("must not be null"));
    }

    @Test
    void createShouldReturnDomainBadRequestContract() throws Exception {
        // arrange
        CreateExchangeRequest request = new CreateExchangeRequest();
        request.setRequestedBookId(100L);
        request.setOfferedBookId(100L);

        when(exchangeService.create(any(CreateExchangeRequest.class), any(UserPrincipal.class)))
                .thenThrow(new ExchangeBadRequestException(
                        "INVALID_EXCHANGE_BOOK_SELECTION",
                        "Requested book and offered book must be different."
                ));

        // act + assert
        mockMvc.perform(post("/api/v1/exchanges")
                        .header(HttpHeaders.AUTHORIZATION, bearer(jwtFor(42L, "alice", List.of("ROLE_USER"))))
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_EXCHANGE_BOOK_SELECTION"))
                .andExpect(jsonPath("$.message").value("Requested book and offered book must be different."))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void createShouldReturnNotFoundContractWhenRequestedBookDoesNotExist() throws Exception {
        // arrange
        CreateExchangeRequest request = new CreateExchangeRequest();
        request.setRequestedBookId(100L);
        request.setOfferedBookId(200L);

        when(exchangeService.create(any(CreateExchangeRequest.class), any(UserPrincipal.class)))
                .thenThrow(new ExchangeNotFoundException(
                        "REQUESTED_BOOK_NOT_FOUND",
                        "Requested book not found."
                ));

        // act + assert
        mockMvc.perform(post("/api/v1/exchanges")
                        .header(HttpHeaders.AUTHORIZATION, bearer(jwtFor(42L, "alice", List.of("ROLE_USER"))))
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("REQUESTED_BOOK_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Requested book not found."))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void outgoingShouldReturnRequestsOfCurrentAuthenticatedUser() throws Exception {
        // arrange
        ExchangeResponse response = ExchangeResponse.builder()
                .id(2L)
                .requestedBookId(101L)
                .requestedBookTitle("The Dispossessed")
                .requestedBookAuthor("Ursula K. Le Guin")
                .offeredBookTitle("Neuromancer")
                .offeredBookAuthor("William Gibson")
                .ownerId(55L)
                .requesterId(42L)
                .ownerUsernameSnapshot("owner")
                .requesterUsernameSnapshot("alice")
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
                .andExpect(jsonPath("$.content[0].ownerUsernameSnapshot").value("owner"))
                .andExpect(jsonPath("$.content[0].requesterUsernameSnapshot").value("alice"))
                .andExpect(jsonPath("$.content[0].requestedBookTitle").value("The Dispossessed"))
                .andExpect(jsonPath("$.content[0].offeredBookAuthor").value("William Gibson"))
                .andExpect(jsonPath("$.content[0].status").value("PENDING"));

        verify(exchangeService).myOutgoing(eq(new UserPrincipal(42L, "alice")), any(Pageable.class));
    }

    @Test
    void incomingShouldReturnRequestsOfCurrentAuthenticatedOwner() throws Exception {
        // arrange
        ExchangeResponse response = ExchangeResponse.builder()
                .id(3L)
                .requestedBookId(102L)
                .requestedBookTitle("Hyperion")
                .requestedBookAuthor("Dan Simmons")
                .offeredBookTitle("Snow Crash")
                .offeredBookAuthor("Neal Stephenson")
                .ownerId(55L)
                .requesterId(42L)
                .ownerUsernameSnapshot("owner")
                .requesterUsernameSnapshot("alice")
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
                .andExpect(jsonPath("$.content[0].ownerUsernameSnapshot").value("owner"))
                .andExpect(jsonPath("$.content[0].requesterUsernameSnapshot").value("alice"))
                .andExpect(jsonPath("$.content[0].requestedBookAuthor").value("Dan Simmons"))
                .andExpect(jsonPath("$.content[0].offeredBookTitle").value("Snow Crash"))
                .andExpect(jsonPath("$.content[0].status").value("PENDING"));

        verify(exchangeService).myIncoming(eq(new UserPrincipal(55L, "owner")), any(Pageable.class));
    }

    @Test
    void acceptShouldPassAuthenticatedPrincipalFromJwt() throws Exception {
        // arrange
        ExchangeResponse response = ExchangeResponse.builder()
                .id(10L)
                .requestedBookId(100L)
                .requestedBookTitle("The Left Hand of Darkness")
                .requestedBookAuthor("Ursula K. Le Guin")
                .offeredBookTitle("Dune")
                .offeredBookAuthor("Frank Herbert")
                .ownerId(55L)
                .requesterId(42L)
                .ownerUsernameSnapshot("owner")
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
                .andExpect(jsonPath("$.requestedBookTitle").value("The Left Hand of Darkness"))
                .andExpect(jsonPath("$.offeredBookAuthor").value("Frank Herbert"))
                .andExpect(jsonPath("$.ownerUsernameSnapshot").value("owner"))
                .andExpect(jsonPath("$.status").value("ACCEPTED"));

        verify(exchangeService).accept(10L, new UserPrincipal(55L, "owner"));
    }

    @Test
    void acceptShouldReturnForbiddenContractWhenActionIsPerformedByAnotherUser() throws Exception {
        // arrange
        when(exchangeService.accept(10L, new UserPrincipal(55L, "owner")))
                .thenThrow(new ExchangeForbiddenException("ONLY_OWNER_CAN_ACCEPT", "Only owner can accept."));

        // act + assert
        mockMvc.perform(post("/api/v1/exchanges/10/accept")
                        .header(HttpHeaders.AUTHORIZATION, bearer(jwtFor(55L, "owner", List.of("ROLE_USER")))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ONLY_OWNER_CAN_ACCEPT"))
                .andExpect(jsonPath("$.message").value("Only owner can accept."))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void cancelShouldPassAuthenticatedPrincipalFromJwt() throws Exception {
        // arrange
        ExchangeResponse response = ExchangeResponse.builder()
                .id(11L)
                .requestedBookId(101L)
                .requestedBookTitle("Solaris")
                .requestedBookAuthor("Stanislaw Lem")
                .offeredBookTitle("Foundation")
                .offeredBookAuthor("Isaac Asimov")
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
                .andExpect(jsonPath("$.requestedBookAuthor").value("Stanislaw Lem"))
                .andExpect(jsonPath("$.offeredBookTitle").value("Foundation"))
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        verify(exchangeService).cancel(11L, new UserPrincipal(42L, "alice"));
    }

    @Test
    void completeShouldPassAuthenticatedPrincipalFromJwt() throws Exception {
        // arrange
        ExchangeResponse response = ExchangeResponse.builder()
                .id(12L)
                .requestedBookId(102L)
                .requestedBookTitle("Hyperion")
                .requestedBookAuthor("Dan Simmons")
                .offeredBookTitle("Snow Crash")
                .offeredBookAuthor("Neal Stephenson")
                .ownerId(55L)
                .requesterId(42L)
                .status(ExchangeStatus.COMPLETION_PENDING)
                .ownerCompletionConfirmedAt(LocalDateTime.of(2026, 5, 25, 14, 5))
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(exchangeService.complete(12L, new UserPrincipal(55L, "owner"))).thenReturn(response);

        // act + assert
        mockMvc.perform(post("/api/v1/exchanges/12/complete")
                        .header(HttpHeaders.AUTHORIZATION, bearer(jwtFor(55L, "owner", List.of("ROLE_USER")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(12))
                .andExpect(jsonPath("$.requestedBookTitle").value("Hyperion"))
                .andExpect(jsonPath("$.offeredBookAuthor").value("Neal Stephenson"))
                .andExpect(jsonPath("$.status").value("COMPLETION_PENDING"))
                .andExpect(jsonPath("$.ownerCompletionConfirmedAt").value("2026-05-25T14:05:00"))
                .andExpect(jsonPath("$.requesterCompletionConfirmedAt").isEmpty());

        verify(exchangeService).complete(12L, new UserPrincipal(55L, "owner"));
    }

    @Test
    void completeShouldSerializeCompletedResponseWithBothConfirmationTimestamps() throws Exception {
        // arrange
        ExchangeResponse response = ExchangeResponse.builder()
                .id(12L)
                .requestedBookId(102L)
                .requestedBookTitle("Hyperion")
                .requestedBookAuthor("Dan Simmons")
                .offeredBookTitle("Snow Crash")
                .offeredBookAuthor("Neal Stephenson")
                .ownerId(55L)
                .requesterId(42L)
                .status(ExchangeStatus.COMPLETED)
                .ownerCompletionConfirmedAt(LocalDateTime.of(2026, 5, 25, 14, 5))
                .requesterCompletionConfirmedAt(LocalDateTime.of(2026, 5, 25, 14, 11))
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(exchangeService.complete(12L, new UserPrincipal(42L, "alice"))).thenReturn(response);

        // act + assert
        mockMvc.perform(post("/api/v1/exchanges/12/complete")
                        .header(HttpHeaders.AUTHORIZATION, bearer(jwtFor(42L, "alice", List.of("ROLE_USER")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(12))
                .andExpect(jsonPath("$.requestedBookAuthor").value("Dan Simmons"))
                .andExpect(jsonPath("$.offeredBookTitle").value("Snow Crash"))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.ownerCompletionConfirmedAt").value("2026-05-25T14:05:00"))
                .andExpect(jsonPath("$.requesterCompletionConfirmedAt").value("2026-05-25T14:11:00"));

        verify(exchangeService).complete(12L, new UserPrincipal(42L, "alice"));
    }

    @Test
    void completeShouldReturnConflictContractForInvalidStatusTransition() throws Exception {
        // arrange
        when(exchangeService.complete(12L, new UserPrincipal(55L, "owner")))
                .thenThrow(new ExchangeConflictException(
                        "INVALID_EXCHANGE_STATUS_TRANSITION",
                        "Only ACCEPTED or COMPLETION_PENDING request can be completed."
                ));

        // act + assert
        mockMvc.perform(post("/api/v1/exchanges/12/complete")
                        .header(HttpHeaders.AUTHORIZATION, bearer(jwtFor(55L, "owner", List.of("ROLE_USER")))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_EXCHANGE_STATUS_TRANSITION"))
                .andExpect(jsonPath("$.message").value("Only ACCEPTED or COMPLETION_PENDING request can be completed."))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void protectedEndpointShouldReturnUnauthorizedWithoutJwt() throws Exception {
        // act + assert
        mockMvc.perform(get("/api/v1/exchanges/my/outgoing"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createShouldGenerateCorrelationIdWhenHeaderIsMissing() throws Exception {
        // arrange
        CreateExchangeRequest request = new CreateExchangeRequest();
        request.setRequestedBookId(100L);
        request.setOfferedBookId(200L);

        when(exchangeService.create(any(CreateExchangeRequest.class), any(UserPrincipal.class)))
                .thenReturn(ExchangeResponse.builder()
                        .id(1L)
                        .requestedBookId(100L)
                        .requestedBookTitle("The Left Hand of Darkness")
                        .requestedBookAuthor("Ursula K. Le Guin")
                        .offeredBookId(200L)
                        .offeredBookTitle("Dune")
                        .offeredBookAuthor("Frank Herbert")
                        .ownerId(55L)
                        .requesterId(42L)
                        .status(ExchangeStatus.PENDING)
                        .createdAt(LocalDateTime.now())
                        .updatedAt(LocalDateTime.now())
                        .build());

        // act + assert
        mockMvc.perform(post("/api/v1/exchanges")
                        .header(HttpHeaders.AUTHORIZATION, bearer(jwtFor(42L, "alice", List.of("ROLE_USER"))))
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
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
                .expiration(new Date(System.currentTimeMillis() + 60 * 60 * 1000))
                .signWith(key)
                .compact();
    }
}
