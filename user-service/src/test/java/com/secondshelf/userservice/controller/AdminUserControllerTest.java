package com.secondshelf.userservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.secondshelf.userservice.config.InternalTokenFilter;
import com.secondshelf.userservice.config.SecurityConfig;
import com.secondshelf.userservice.dto.PrivateUserProfileResponse;
import com.secondshelf.userservice.entity.Role;
import com.secondshelf.userservice.exception.UserNotFoundException;
import com.secondshelf.userservice.exception.handler.GlobalExceptionHandler;
import com.secondshelf.userservice.security.JwtAuthenticationFilter;
import com.secondshelf.userservice.service.AdminUserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Set;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdminUserController.class)
@Import({SecurityConfig.class, InternalTokenFilter.class, JwtAuthenticationFilter.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = {
        "internal.token=test-internal-token",
        "jwt.secret=test-secret-test-secret-test-secret-12345678"
})
class AdminUserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AdminUserService adminUserService;

    @Test
    void blockShouldReturnUserProfileForAdmin() throws Exception {
        PrivateUserProfileResponse response = PrivateUserProfileResponse.builder()
                .id(10L)
                .username("blocked-user")
                .email("blocked@example.com")
                .firstName("Blocked")
                .lastName("User")
                .roles(Set.of(Role.ROLE_USER))
                .enabled(false)
                .createdAt(LocalDateTime.of(2024, 1, 2, 3, 4))
                .build();

        when(adminUserService.block(10L)).thenReturn(response);

        mockMvc.perform(put("/api/v1/admin/users/10/block")
                        .with(SecurityMockMvcRequestPostProcessors.user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.username").value("blocked-user"))
                .andExpect(jsonPath("$.email").value("blocked@example.com"))
                .andExpect(jsonPath("$.roles[0]").value("ROLE_USER"))
                .andExpect(jsonPath("$.enabled").value(false));

        verify(adminUserService).block(10L);
    }

    @Test
    void blockShouldReturnForbiddenForNonAdmin() throws Exception {
        // act + assert
        mockMvc.perform(put("/api/v1/admin/users/10/block")
                        .with(SecurityMockMvcRequestPostProcessors.user("user").roles("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void blockShouldReturnUnauthorizedWithoutAuthentication() throws Exception {
        mockMvc.perform(put("/api/v1/admin/users/10/block"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(adminUserService);
    }

    @Test
    void unblockShouldReturnPrivateProfileForAdmin() throws Exception {
        PrivateUserProfileResponse response = PrivateUserProfileResponse.builder()
                .id(77L)
                .username("restored-user")
                .email("restored@example.com")
                .firstName("Restored")
                .lastName("User")
                .roles(Set.of(Role.ROLE_USER))
                .enabled(true)
                .createdAt(LocalDateTime.of(2023, 5, 6, 7, 8))
                .build();

        when(adminUserService.unblock(77L)).thenReturn(response);

        mockMvc.perform(put("/api/v1/admin/users/77/unblock")
                        .with(SecurityMockMvcRequestPostProcessors.user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(77))
                .andExpect(jsonPath("$.email").value("restored@example.com"))
                .andExpect(jsonPath("$.firstName").value("Restored"))
                .andExpect(jsonPath("$.enabled").value(true));

        verify(adminUserService).unblock(77L);
    }

    @Test
    void unblockShouldReturnNotFoundWhenUserDoesNotExist() throws Exception {
        when(adminUserService.unblock(77L)).thenThrow(new UserNotFoundException(77L));

        mockMvc.perform(put("/api/v1/admin/users/77/unblock")
                        .with(SecurityMockMvcRequestPostProcessors.user("admin").roles("ADMIN")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("USER_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("User with id = 77 not found."));
    }

    @Test
    void updateRolesShouldValidateRequestBody() throws Exception {
        String requestBody = """
                {
                  "roles": []
                }
                """;

        mockMvc.perform(put("/api/v1/admin/users/15/roles")
                        .with(SecurityMockMvcRequestPostProcessors.user("admin").roles("ADMIN"))
                        .contentType(APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.path").value("/api/v1/admin/users/15/roles"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Request validation failed"))
                .andExpect(jsonPath("$.details.roles").value("must not be empty"))
                .andExpect(jsonPath("$.details.fieldErrors[0].field").value("roles"))
                .andExpect(jsonPath("$.details.fieldErrors[0].reason").value("must not be empty"))
                .andExpect(jsonPath("$.details.fieldErrors[0].code").exists());
    }

    @Test
    void updateRolesShouldReturnForbiddenForNonAdminBeforeCallingService() throws Exception {
        String requestBody = objectMapper.writeValueAsString(
                new UpdateRolesRequestBody(Set.of(Role.ROLE_ADMIN))
        );

        mockMvc.perform(put("/api/v1/admin/users/15/roles")
                        .with(SecurityMockMvcRequestPostProcessors.user("user").roles("USER"))
                        .contentType(APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isForbidden());

        verifyNoInteractions(adminUserService);
    }

    @Test
    void updateRolesShouldPassRolesToServiceForAdmin() throws Exception {
        PrivateUserProfileResponse response = PrivateUserProfileResponse.builder()
                .id(15L)
                .username("alice")
                .email("alice@example.com")
                .firstName("Alice")
                .lastName("Admin")
                .roles(Set.of(Role.ROLE_ADMIN))
                .enabled(true)
                .build();

        String requestBody = objectMapper.writeValueAsString(
                new UpdateRolesRequestBody(Set.of(Role.ROLE_ADMIN))
        );

        when(adminUserService.updateRoles(eq(15L), eq(Set.of(Role.ROLE_ADMIN)))).thenReturn(response);

        mockMvc.perform(put("/api/v1/admin/users/15/roles")
                        .with(SecurityMockMvcRequestPostProcessors.user("admin").roles("ADMIN"))
                        .contentType(APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(15))
                .andExpect(jsonPath("$.username").value("alice"))
                .andExpect(jsonPath("$.email").value("alice@example.com"))
                .andExpect(jsonPath("$.firstName").value("Alice"))
                .andExpect(jsonPath("$.roles[0]").value("ROLE_ADMIN"))
                .andExpect(jsonPath("$.enabled").value(true));

        verify(adminUserService).updateRoles(15L, Set.of(Role.ROLE_ADMIN));
    }

    private record UpdateRolesRequestBody(Set<Role> roles) {
    }
}
