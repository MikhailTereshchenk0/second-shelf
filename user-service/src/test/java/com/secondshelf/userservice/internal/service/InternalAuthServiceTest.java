package com.secondshelf.userservice.internal.service;

import com.secondshelf.userservice.entity.Role;
import com.secondshelf.userservice.entity.User;
import com.secondshelf.userservice.internal.dto.AuthenticateResponse;
import com.secondshelf.userservice.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.EnumSet;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InternalAuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private InternalAuthService internalAuthService;

    @Test
    void authenticateShouldReturnResponseWhenUserIsEnabledAndPasswordMatches() {
        // arrange
        User user = User.builder()
                .id(1L)
                .username("alice")
                .password("encoded-password")
                .enabled(true)
                .roles(EnumSet.of(Role.ROLE_USER))
                .build();

        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("raw-password", "encoded-password")).thenReturn(true);

        // act
        AuthenticateResponse response = internalAuthService.authenticate("alice", "raw-password");

        // assert
        assertNotNull(response);
        assertEquals(1L, response.getUserId());
        assertEquals("alice", response.getUsername());
        assertEquals(1, response.getRoles().size());
        assertEquals(Role.ROLE_USER, response.getRoles().get(0));
    }

    @Test
    void authenticateShouldReturnNullWhenUserIsBlocked() {
        // arrange
        User user = User.builder()
                .id(2L)
                .username("blocked-user")
                .password("encoded-password")
                .enabled(false)
                .roles(EnumSet.of(Role.ROLE_USER))
                .build();

        when(userRepository.findByUsername("blocked-user")).thenReturn(Optional.of(user));

        // act
        AuthenticateResponse response = internalAuthService.authenticate("blocked-user", "raw-password");

        // assert
        assertNull(response);
        verify(passwordEncoder, never()).matches(anyString(), anyString());
    }

    @Test
    void authenticateShouldReturnNullWhenPasswordDoesNotMatch() {
        // arrange
        User user = User.builder()
                .id(3L)
                .username("alice")
                .password("encoded-password")
                .enabled(true)
                .roles(EnumSet.of(Role.ROLE_USER))
                .build();

        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong-password", "encoded-password")).thenReturn(false);

        // act
        AuthenticateResponse response = internalAuthService.authenticate("alice", "wrong-password");

        // assert
        assertNull(response);
    }
}