package com.secondshelf.userservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.secondshelf.userservice.config.InternalTokenFilter;
import com.secondshelf.userservice.config.SecurityConfig;
import com.secondshelf.userservice.dto.UserProfileResponse;
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

import java.util.Set;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
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
        // arrange
        UserProfileResponse response = UserProfileResponse.builder()
                .id(10L)
                .username("blocked-user")
                .firstName("Blocked")
                .lastName("User")
                .email("blocked@example.com")
                .build();

        when(adminUserService.block(10L)).thenReturn(response);

        // act + assert
        mockMvc.perform(put("/api/v1/admin/users/10/block")
                        .with(SecurityMockMvcRequestPostProcessors.user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.username").value("blocked-user"))
                .andExpect(jsonPath("$.email").value("blocked@example.com"));

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
    void unblockShouldReturnNotFoundWhenUserDoesNotExist() throws Exception {
        // arrange
        when(adminUserService.unblock(77L)).thenThrow(new UserNotFoundException(77L));

        // act + assert
        mockMvc.perform(put("/api/v1/admin/users/77/unblock")
                        .with(SecurityMockMvcRequestPostProcessors.user("admin").roles("ADMIN")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("User with id = 77 not found."));
    }

    @Test
    void updateRolesShouldValidateRequestBody() throws Exception {
        // arrange
        String requestBody = """
                {
                  "roles": []
                }
                """;

        // act + assert
        mockMvc.perform(put("/api/v1/admin/users/15/roles")
                        .with(SecurityMockMvcRequestPostProcessors.user("admin").roles("ADMIN"))
                        .contentType(APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("Request validation failed"))
                .andExpect(jsonPath("$.details.roles").value("must not be empty"));
    }

    @Test
    void updateRolesShouldPassRolesToServiceForAdmin() throws Exception {
        // arrange
        UserProfileResponse response = UserProfileResponse.builder()
                .id(15L)
                .username("alice")
                .build();

        String requestBody = objectMapper.writeValueAsString(
                new UpdateRolesRequestBody(Set.of(Role.ROLE_ADMIN))
        );

        when(adminUserService.updateRoles(eq(15L), eq(Set.of(Role.ROLE_ADMIN)))).thenReturn(response);

        // act + assert
        mockMvc.perform(put("/api/v1/admin/users/15/roles")
                        .with(SecurityMockMvcRequestPostProcessors.user("admin").roles("ADMIN"))
                        .contentType(APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(15))
                .andExpect(jsonPath("$.username").value("alice"));

        verify(adminUserService).updateRoles(15L, Set.of(Role.ROLE_ADMIN));
    }

    private record UpdateRolesRequestBody(Set<Role> roles) {
    }
}