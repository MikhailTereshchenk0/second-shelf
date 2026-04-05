package com.secondshelf.userservice.internal.controller;

import com.secondshelf.userservice.config.InternalTokenFilter;
import com.secondshelf.userservice.config.SecurityConfig;
import com.secondshelf.userservice.entity.Role;
import com.secondshelf.userservice.entity.User;
import com.secondshelf.userservice.exception.handler.GlobalExceptionHandler;
import com.secondshelf.userservice.repository.UserRepository;
import com.secondshelf.userservice.security.JwtAuthenticationFilter;
import com.secondshelf.userservice.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.EnumSet;
import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(InternalUserController.class)
@Import({SecurityConfig.class, InternalTokenFilter.class, JwtAuthenticationFilter.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = {
        "internal.token=test-internal-token",
        "jwt.secret=test-secret-test-secret-test-secret-12345678"
})
class InternalUserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private UserRepository userRepository;

    @Test
    void claimsShouldReturnDisabledFlagForBlockedUserWhenInternalTokenIsValid() throws Exception {
        // arrange
        User blockedUser = User.builder()
                .id(5L)
                .username("blocked-user")
                .enabled(false)
                .roles(EnumSet.of(Role.ROLE_USER))
                .build();

        when(userRepository.findById(5L)).thenReturn(Optional.of(blockedUser));

        // act + assert
        mockMvc.perform(get("/internal/users/5/claims")
                        .header(InternalTokenFilter.HEADER, "test-internal-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(5))
                .andExpect(jsonPath("$.username").value("blocked-user"))
                .andExpect(jsonPath("$.roles[0]").value("ROLE_USER"))
                .andExpect(jsonPath("$.enabled").value(false));
    }

    @Test
    void claimsShouldReturnUnauthorizedWhenInternalTokenIsMissing() throws Exception {
        // act + assert
        mockMvc.perform(get("/internal/users/5/claims"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().json("""
                        {
                          "code": "UNAUTHORIZED",
                          "message": "Invalid internal token"
                        }
                        """));
    }
}