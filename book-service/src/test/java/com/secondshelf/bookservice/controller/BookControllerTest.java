package com.secondshelf.bookservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.secondshelf.bookservice.config.InternalTokenFilter;
import com.secondshelf.bookservice.config.SecurityConfig;
import com.secondshelf.bookservice.dto.BookResponse;
import com.secondshelf.bookservice.dto.CreateBookRequest;
import com.secondshelf.bookservice.dto.UpdateBookRequest;
import com.secondshelf.bookservice.entity.BookStatus;
import com.secondshelf.bookservice.entity.BookVisibility;
import com.secondshelf.bookservice.exception.BookAccessDeniedException;
import com.secondshelf.bookservice.exception.BookNotFoundException;
import com.secondshelf.bookservice.exception.handler.GlobalExceptionHandler;
import com.secondshelf.bookservice.observability.CorrelationId;
import com.secondshelf.bookservice.security.JwtAuthenticationFilter;
import com.secondshelf.bookservice.security.UserPrincipal;
import com.secondshelf.bookservice.service.BookService;
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
import static org.hamcrest.Matchers.matchesPattern;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(BookController.class)
@Import({SecurityConfig.class, InternalTokenFilter.class, JwtAuthenticationFilter.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = {
        "internal.token=test-internal-token",
        "jwt.secret=test-secret-test-secret-test-secret-12345678"
})
class BookControllerTest {

    private static final String JWT_SECRET = "test-secret-test-secret-test-secret-12345678";
    private static final String UUID_PATTERN =
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private BookService bookService;

    @Test
    void publicCatalogShouldBeAccessibleWithoutAuthentication() throws Exception {
        // arrange
        BookResponse book = BookResponse.builder()
                .id(1L)
                .ownerId(42L)
                .title("Public Book")
                .author("Author")
                .visibility(BookVisibility.PUBLIC)
                .status(BookStatus.AVAILABLE)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(bookService.getPublicCatalog(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(book)));

        // act + assert
        mockMvc.perform(get("/api/v1/books/public"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Public Book"))
                .andExpect(jsonPath("$.content[0].visibility").value("PUBLIC"));

        verify(bookService).getPublicCatalog(any(Pageable.class));
    }

    @Test
    void publicCatalogShouldClampPageSizeAndAllowWhitelistedSort() throws Exception {
        when(bookService.getPublicCatalog(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/v1/books/public")
                        .param("size", "250")
                        .param("sort", "title,asc"))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(bookService).getPublicCatalog(pageableCaptor.capture());

        Pageable pageable = pageableCaptor.getValue();
        assertThat(pageable.getPageSize()).isEqualTo(100);
        assertThat(pageable.getSort().getOrderFor("title")).isNotNull();
    }

    @Test
    void publicCatalogShouldRejectUnsupportedSort() throws Exception {
        mockMvc.perform(get("/api/v1/books/public")
                        .param("sort", "ownerId,asc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.path").value("/api/v1/books/public"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Unsupported sort field: ownerId"));
    }

    @Test
    void publicCatalogShouldGenerateCorrelationIdWhenHeaderIsMissing() throws Exception {
        when(bookService.getPublicCatalog(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/v1/books/public"))
                .andExpect(status().isOk())
                .andExpect(header().string(CorrelationId.HEADER_NAME, matchesPattern(UUID_PATTERN)));
    }

    @Test
    void createShouldPassAuthenticatedPrincipalFromJwt() throws Exception {
        // arrange
        CreateBookRequest request = new CreateBookRequest();
        request.setTitle("Clean Architecture");
        request.setAuthor("Robert C. Martin");
        request.setDescription("Book description");
        request.setVisibility(BookVisibility.PUBLIC);

        BookResponse response = BookResponse.builder()
                .id(10L)
                .ownerId(42L)
                .title("Clean Architecture")
                .author("Robert C. Martin")
                .description("Book description")
                .visibility(BookVisibility.PUBLIC)
                .status(BookStatus.AVAILABLE)
                .build();

        when(bookService.create(any(CreateBookRequest.class), any(UserPrincipal.class))).thenReturn(response);

        // act + assert
        mockMvc.perform(post("/api/v1/books")
                        .header(HttpHeaders.AUTHORIZATION, bearer(jwtFor(42L, "alice", List.of("ROLE_USER"))))
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.ownerId").value(42))
                .andExpect(jsonPath("$.title").value("Clean Architecture"));

        verify(bookService).create(any(CreateBookRequest.class), eq(new UserPrincipal(42L, "alice")));
    }

    @Test
    void createShouldReturnUnauthorizedWithoutJwt() throws Exception {
        // arrange
        CreateBookRequest request = new CreateBookRequest();
        request.setTitle("Clean Architecture");
        request.setAuthor("Robert C. Martin");

        // act + assert
        mockMvc.perform(post("/api/v1/books")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getByIdShouldReturnNotFoundWhenPrivateBookIsHiddenByService() throws Exception {
        // arrange
        when(bookService.getById(eq(99L), any(UserPrincipal.class)))
                .thenThrow(new BookNotFoundException(99L));

        // act + assert
        mockMvc.perform(get("/api/v1/books/99")
                        .header(HttpHeaders.AUTHORIZATION, bearer(jwtFor(7L, "bob", List.of("ROLE_USER")))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("BOOK_NOT_FOUND"))
                .andExpect(jsonPath("$.path").value("/api/v1/books/99"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Book with id = 99 not found."));
    }

    @Test
    void updateShouldReturnForbiddenWhenBookBelongsToAnotherUser() throws Exception {
        // arrange
        UpdateBookRequest request = new UpdateBookRequest();
        request.setTitle("Updated title");

        when(bookService.update(eq(15L), any(UpdateBookRequest.class), any(UserPrincipal.class)))
                .thenThrow(new BookAccessDeniedException(15L));

        // act + assert
        mockMvc.perform(patch("/api/v1/books/15")
                        .header(HttpHeaders.AUTHORIZATION, bearer(jwtFor(7L, "bob", List.of("ROLE_USER"))))
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"))
                .andExpect(jsonPath("$.message").value("Access denied for book id = 15"));
    }

    @Test
    void myBooksShouldPassAuthenticatedPrincipalFromJwt() throws Exception {
        // arrange
        BookResponse book = BookResponse.builder()
                .id(20L)
                .ownerId(42L)
                .title("My Book")
                .author("Author")
                .visibility(BookVisibility.PRIVATE)
                .status(BookStatus.AVAILABLE)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(bookService.getMyBooks(any(UserPrincipal.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(book)));

        // act + assert
        mockMvc.perform(get("/api/v1/books/my")
                        .header(HttpHeaders.AUTHORIZATION, bearer(jwtFor(42L, "alice", List.of("ROLE_USER")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(20))
                .andExpect(jsonPath("$.content[0].ownerId").value(42))
                .andExpect(jsonPath("$.content[0].visibility").value("PRIVATE"));

        verify(bookService).getMyBooks(eq(new UserPrincipal(42L, "alice")), any(Pageable.class));
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
