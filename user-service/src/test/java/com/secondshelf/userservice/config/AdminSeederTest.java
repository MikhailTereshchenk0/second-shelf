package com.secondshelf.userservice.config;

import com.secondshelf.userservice.entity.User;
import com.secondshelf.userservice.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminSeederTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private Environment environment;

    @InjectMocks
    private AdminSeeder adminSeeder;

    @Test
    void weakSeedPasswordShouldBeAllowedForLocalProfile() throws Exception {
        ReflectionTestUtils.setField(adminSeeder, "adminUsername", "admin");
        ReflectionTestUtils.setField(adminSeeder, "adminPassword", "admin12345");
        ReflectionTestUtils.setField(adminSeeder, "adminEmail", "admin@secondshelf.local");

        when(environment.getActiveProfiles()).thenReturn(new String[]{"local"});
        when(userRepository.findByUsername("admin")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("admin12345")).thenReturn("encoded-password");

        adminSeeder.run();

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertEquals("admin", userCaptor.getValue().getUsername());
        assertEquals("encoded-password", userCaptor.getValue().getPassword());
    }

    @Test
    void weakSeedPasswordShouldFailForNonLocalProfile() {
        ReflectionTestUtils.setField(adminSeeder, "adminUsername", "admin");
        ReflectionTestUtils.setField(adminSeeder, "adminPassword", "admin12345");
        ReflectionTestUtils.setField(adminSeeder, "adminEmail", "admin@secondshelf.local");

        when(environment.getActiveProfiles()).thenReturn(new String[]{"dev"});
        when(userRepository.findByUsername("admin")).thenReturn(Optional.empty());

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> adminSeeder.run());

        assertEquals(
                "Seed admin password does not satisfy the password policy for non-local profiles: Password must contain at least one uppercase letter.",
                exception.getMessage()
        );
        verify(passwordEncoder, never()).encode(anyString());
        verify(userRepository, never()).save(any());
    }

    @Test
    void strongSeedPasswordShouldPassForNonLocalProfile() throws Exception {
        ReflectionTestUtils.setField(adminSeeder, "adminUsername", "admin");
        ReflectionTestUtils.setField(adminSeeder, "adminPassword", "Str0ng!RootPwd");
        ReflectionTestUtils.setField(adminSeeder, "adminEmail", "ops@example.com");

        when(environment.getActiveProfiles()).thenReturn(new String[]{"prod"});
        when(userRepository.findByUsername("admin")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("Str0ng!RootPwd")).thenReturn("encoded-password");

        adminSeeder.run();

        verify(passwordEncoder).encode("Str0ng!RootPwd");
        verify(userRepository).save(any(User.class));
    }
}
