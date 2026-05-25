package com.secondshelf.userservice.controller;

import com.secondshelf.userservice.config.InternalTokenFilter;
import com.secondshelf.userservice.config.SecurityConfig;
import com.secondshelf.userservice.dto.PrivateUserProfileResponse;
import com.secondshelf.userservice.dto.PublicUserProfileResponse;
import com.secondshelf.userservice.dto.UpdateUserProfileRequest;
import com.secondshelf.userservice.entity.Role;
import com.secondshelf.userservice.exception.handler.GlobalExceptionHandler;
import com.secondshelf.userservice.security.AuthenticatedUser;
import com.secondshelf.userservice.security.JwtAuthenticationFilter;
import com.secondshelf.userservice.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@Import({SecurityConfig.class, InternalTokenFilter.class, JwtAuthenticationFilter.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = {
        "internal.token=test-internal-token",
        "jwt.secret=test-secret-test-secret-test-secret-12345678"
})
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @Test
    void getByIdShouldReturnPublicProfileWithoutSensitiveFields() throws Exception {
        PublicUserProfileResponse response = PublicUserProfileResponse.builder()
                .id(7L)
                .username("alice")
                .city("Minsk")
                .about("Book lover")
                .build();

        when(userService.getById(7L)).thenReturn(response);

        mockMvc.perform(get("/api/v1/users/7")
                        .with(SecurityMockMvcRequestPostProcessors.user("reader").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.username").value("alice"))
                .andExpect(jsonPath("$.city").value("Minsk"))
                .andExpect(jsonPath("$.about").value("Book lover"))
                .andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(jsonPath("$.firstName").doesNotExist())
                .andExpect(jsonPath("$.lastName").doesNotExist());

        verify(userService).getById(7L);
    }

    @Test
    void getByUsernameShouldReturnPublicProfileWithoutSensitiveFields() throws Exception {
        PublicUserProfileResponse response = PublicUserProfileResponse.builder()
                .id(8L)
                .username("bob")
                .city("Grodno")
                .about("Collector")
                .build();

        when(userService.getByUsername("bob")).thenReturn(response);

        mockMvc.perform(get("/api/v1/users/by-username")
                        .param("username", "bob")
                        .with(SecurityMockMvcRequestPostProcessors.user("reader").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(8))
                .andExpect(jsonPath("$.username").value("bob"))
                .andExpect(jsonPath("$.city").value("Grodno"))
                .andExpect(jsonPath("$.about").value("Collector"))
                .andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(jsonPath("$.firstName").doesNotExist())
                .andExpect(jsonPath("$.lastName").doesNotExist());

        verify(userService).getByUsername("bob");
    }

    @Test
    void meShouldReturnPrivateProfileForCurrentUser() throws Exception {
        PrivateUserProfileResponse response = PrivateUserProfileResponse.builder()
                .id(9L)
                .username("charlie")
                .email("charlie@example.com")
                .firstName("Charlie")
                .lastName("Reader")
                .city("Brest")
                .about("Private profile")
                .roles(Set.of(Role.ROLE_USER))
                .enabled(true)
                .createdAt(LocalDateTime.of(2025, 1, 10, 12, 0))
                .build();

        when(userService.getCurrentUser(9L)).thenReturn(response);

        mockMvc.perform(get("/api/v1/users/me")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(authenticatedUser(9L, "charlie", "ROLE_USER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(9))
                .andExpect(jsonPath("$.username").value("charlie"))
                .andExpect(jsonPath("$.email").value("charlie@example.com"))
                .andExpect(jsonPath("$.firstName").value("Charlie"))
                .andExpect(jsonPath("$.lastName").value("Reader"))
                .andExpect(jsonPath("$.roles[0]").value("ROLE_USER"))
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.createdAt").value("2025-01-10T12:00:00"));

        verify(userService).getCurrentUser(9L);
    }

    @Test
    void updateProfileShouldReturnPrivateProfileForOwner() throws Exception {
        PrivateUserProfileResponse response = PrivateUserProfileResponse.builder()
                .id(11L)
                .username("diana")
                .email("diana@example.com")
                .firstName("Diana")
                .lastName("Updated")
                .city("Vitebsk")
                .about("Updated bio")
                .roles(Set.of(Role.ROLE_USER))
                .enabled(true)
                .createdAt(LocalDateTime.of(2024, 6, 5, 9, 30))
                .build();

        when(userService.updateProfile(eq(11L), org.mockito.ArgumentMatchers.any(UpdateUserProfileRequest.class)))
                .thenReturn(response);

        mockMvc.perform(put("/api/v1/users/11")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(authenticatedUser(11L, "diana", "ROLE_USER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstName": "Diana",
                                  "lastName": "Updated",
                                  "city": "Vitebsk",
                                  "about": "Updated bio"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(11))
                .andExpect(jsonPath("$.username").value("diana"))
                .andExpect(jsonPath("$.email").value("diana@example.com"))
                .andExpect(jsonPath("$.firstName").value("Diana"))
                .andExpect(jsonPath("$.lastName").value("Updated"))
                .andExpect(jsonPath("$.roles[0]").value("ROLE_USER"))
                .andExpect(jsonPath("$.enabled").value(true));

        verify(userService).updateProfile(eq(11L), org.mockito.ArgumentMatchers.any(UpdateUserProfileRequest.class));
    }

    private UsernamePasswordAuthenticationToken authenticatedUser(Long userId, String username, String... roles) {
        List<SimpleGrantedAuthority> authorities = java.util.Arrays.stream(roles)
                .map(SimpleGrantedAuthority::new)
                .toList();
        return new UsernamePasswordAuthenticationToken(
                new AuthenticatedUser(userId, username),
                null,
                authorities
        );
    }
}
