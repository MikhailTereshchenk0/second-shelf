package com.secondshelf.userservice.service;

import com.secondshelf.userservice.entity.Role;
import com.secondshelf.userservice.exception.*;
import com.secondshelf.userservice.dto.CreateUserProfileRequest;
import com.secondshelf.userservice.dto.PrivateUserProfileResponse;
import com.secondshelf.userservice.dto.PublicUserProfileResponse;
import com.secondshelf.userservice.dto.UpdateUserProfileRequest;
import com.secondshelf.userservice.entity.User;
import com.secondshelf.userservice.mapper.UserMapper;
import com.secondshelf.userservice.repository.UserRepository;
import com.secondshelf.userservice.security.SecurityUtils;
import com.secondshelf.observability.AuditEvent;
import com.secondshelf.observability.AuditLogger;
import com.secondshelf.observability.AuditOutcome;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class UserServiceImpl implements UserService {

    private static final AuditLogger AUDIT_LOGGER = AuditLogger.forClass(UserServiceImpl.class);

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    public PrivateUserProfileResponse createProfile(CreateUserProfileRequest request) {

        userRepository.findByUsername(request.getUsername())
                .ifPresent(u -> {
                    throw new UsernameAlreadyExistsException(request.getUsername());
                });

        userRepository.findByEmail(request.getEmail())
                .ifPresent(u -> {
                    throw new EmailAlreadyExistsException(request.getEmail());
                });

        User user = userMapper.toUser(request);
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.getRoles().add(Role.ROLE_USER);

        User saved = userRepository.save(user);

        return userMapper.toPrivateUserProfileResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public PublicUserProfileResponse getById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(id));
        return userMapper.toPublicUserProfileResponse(user);
    }

    @Override
    @Transactional(readOnly = true)
    public PublicUserProfileResponse getByUsername(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException(username));
        return userMapper.toPublicUserProfileResponse(user);
    }

    @Override
    @Transactional(readOnly = true)
    public PrivateUserProfileResponse getCurrentUser(Long currentUserId) {
        User user = userRepository.findById(currentUserId)
                .orElseThrow(() -> new UserNotFoundException(currentUserId));
        return userMapper.toPrivateUserProfileResponse(user);
    }

    @Override
    public PrivateUserProfileResponse updateProfile(Long id, UpdateUserProfileRequest request) {
        Long actorUserId = SecurityUtils.currentUserId();

        try {
            User user = userRepository.findById(id)
                    .orElseThrow(() -> new UserNotFoundException(id));

            userMapper.updateUserFromRequest(request, user);

            User updated = userRepository.save(user);
            PrivateUserProfileResponse response = userMapper.toPrivateUserProfileResponse(updated);
            AUDIT_LOGGER.log(AuditEvent.builder("USER_PROFILE_UPDATE", AuditOutcome.SUCCESS)
                    .actorUserId(actorUserId)
                    .targetUserId(id)
                    .build());
            return response;
        } catch (RuntimeException ex) {
            AUDIT_LOGGER.log(AuditEvent.builder("USER_PROFILE_UPDATE", AuditOutcome.FAILURE)
                    .actorUserId(actorUserId)
                    .targetUserId(id)
                    .reason(ex.getMessage())
                    .build());
            throw ex;
        }
    }
}
