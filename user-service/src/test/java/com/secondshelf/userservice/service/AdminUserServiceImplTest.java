package com.secondshelf.userservice.service;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.secondshelf.userservice.dto.PrivateUserProfileResponse;
import com.secondshelf.userservice.entity.Role;
import com.secondshelf.userservice.entity.User;
import com.secondshelf.userservice.exception.UserNotFoundException;
import com.secondshelf.userservice.mapper.UserMapper;
import com.secondshelf.userservice.repository.UserRepository;
import com.secondshelf.userservice.security.AuthenticatedUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private AdminUserServiceImpl adminUserService;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void updateRolesShouldReplaceUserRolesAndReturnMappedResponse() {
        // arrange
        User user = User.builder()
                .id(1L)
                .username("alice")
                .enabled(true)
                .roles(EnumSet.of(Role.ROLE_USER))
                .build();

        Set<Role> newRoles = EnumSet.of(Role.ROLE_ADMIN);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userMapper.toPrivateUserProfileResponse(any(User.class)))
                .thenReturn(PrivateUserProfileResponse.builder()
                        .id(1L)
                        .username("alice")
                        .roles(Set.of(Role.ROLE_ADMIN))
                        .build());

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);

        PrivateUserProfileResponse response = adminUserService.updateRoles(1L, newRoles);

        assertNotNull(response);
        assertEquals(1L, response.getId());
        assertEquals("alice", response.getUsername());
        assertEquals(Set.of(Role.ROLE_ADMIN), response.getRoles());

        verify(userRepository).save(userCaptor.capture());
        assertEquals(EnumSet.of(Role.ROLE_ADMIN), userCaptor.getValue().getRoles());
    }

    @Test
    void blockShouldDisableUserAndPersistChange() {
        // arrange
        User user = User.builder()
                .id(2L)
                .username("bob")
                .enabled(true)
                .build();
        Logger logger = (Logger) LoggerFactory.getLogger(AdminUserServiceImpl.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(new AuthenticatedUser(99L, "admin"), null, List.of())
        );

        when(userRepository.findById(2L)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userMapper.toPrivateUserProfileResponse(any(User.class)))
                .thenReturn(PrivateUserProfileResponse.builder()
                        .id(2L)
                        .username("bob")
                        .enabled(false)
                        .build());

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);

        PrivateUserProfileResponse response;
        try {
            response = adminUserService.block(2L);
        } finally {
            logger.detachAppender(appender);
        }

        assertNotNull(response);
        assertEquals(2L, response.getId());
        assertEquals("bob", response.getUsername());
        assertFalse(response.isEnabled());

        verify(userRepository).save(userCaptor.capture());
        assertFalse(userCaptor.getValue().isEnabled());
        assertFalse(appender.list.isEmpty());

        String auditMessage = appender.list.get(0).getFormattedMessage();
        assertTrue(auditMessage.contains("eventType=USER_ADMIN_BLOCK"));
        assertTrue(auditMessage.contains("outcome=SUCCESS"));
        assertTrue(auditMessage.contains("actorUserId=99"));
        assertTrue(auditMessage.contains("targetUserId=2"));
    }

    @Test
    void unblockShouldEnableUserAndPersistChange() {
        // arrange
        User user = User.builder()
                .id(3L)
                .username("charlie")
                .enabled(false)
                .build();

        when(userRepository.findById(3L)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userMapper.toPrivateUserProfileResponse(any(User.class)))
                .thenReturn(PrivateUserProfileResponse.builder()
                        .id(3L)
                        .username("charlie")
                        .enabled(true)
                        .build());

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);

        PrivateUserProfileResponse response = adminUserService.unblock(3L);

        assertNotNull(response);
        assertEquals(3L, response.getId());
        assertEquals("charlie", response.getUsername());
        assertTrue(response.isEnabled());

        verify(userRepository).save(userCaptor.capture());
        assertTrue(userCaptor.getValue().isEnabled());
    }

    @Test
    void blockShouldThrowWhenUserDoesNotExist() {
        // arrange
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        UserNotFoundException exception = assertThrows(
                UserNotFoundException.class,
                () -> adminUserService.block(999L)
        );

        assertEquals("User with id = 999 not found.", exception.getMessage());
        verify(userRepository, never()).save(any(User.class));
        verifyNoInteractions(userMapper);
    }
}
