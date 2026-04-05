package com.secondshelf.userservice.service;

import com.secondshelf.userservice.dto.UserProfileResponse;
import com.secondshelf.userservice.entity.Role;
import com.secondshelf.userservice.entity.User;
import com.secondshelf.userservice.exception.UserNotFoundException;
import com.secondshelf.userservice.mapper.UserMapper;
import com.secondshelf.userservice.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.EnumSet;
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
        when(userMapper.toUserProfileResponse(any(User.class)))
                .thenReturn(UserProfileResponse.builder()
                        .id(1L)
                        .username("alice")
                        .build());

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);

        // act
        UserProfileResponse response = adminUserService.updateRoles(1L, newRoles);

        // assert
        assertNotNull(response);
        assertEquals(1L, response.getId());
        assertEquals("alice", response.getUsername());

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

        when(userRepository.findById(2L)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userMapper.toUserProfileResponse(any(User.class)))
                .thenReturn(UserProfileResponse.builder()
                        .id(2L)
                        .username("bob")
                        .build());

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);

        // act
        UserProfileResponse response = adminUserService.block(2L);

        // assert
        assertNotNull(response);
        assertEquals(2L, response.getId());
        assertEquals("bob", response.getUsername());

        verify(userRepository).save(userCaptor.capture());
        assertFalse(userCaptor.getValue().isEnabled());
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
        when(userMapper.toUserProfileResponse(any(User.class)))
                .thenReturn(UserProfileResponse.builder()
                        .id(3L)
                        .username("charlie")
                        .build());

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);

        // act
        UserProfileResponse response = adminUserService.unblock(3L);

        // assert
        assertNotNull(response);
        assertEquals(3L, response.getId());
        assertEquals("charlie", response.getUsername());

        verify(userRepository).save(userCaptor.capture());
        assertTrue(userCaptor.getValue().isEnabled());
    }

    @Test
    void blockShouldThrowWhenUserDoesNotExist() {
        // arrange
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        // act
        UserNotFoundException exception = assertThrows(
                UserNotFoundException.class,
                () -> adminUserService.block(999L)
        );

        // assert
        assertEquals("User with id = 999 not found.", exception.getMessage());
        verify(userRepository, never()).save(any(User.class));
        verifyNoInteractions(userMapper);
    }
}