package com.secondshelf.userservice.service;

import com.secondshelf.userservice.dto.PrivateUserProfileResponse;
import com.secondshelf.userservice.entity.Role;
import com.secondshelf.userservice.entity.User;
import com.secondshelf.userservice.exception.UserNotFoundException;
import com.secondshelf.userservice.mapper.UserMapper;
import com.secondshelf.userservice.repository.UserRepository;
import com.secondshelf.userservice.security.SecurityUtils;
import com.secondshelf.observability.AuditEvent;
import com.secondshelf.observability.AuditLogger;
import com.secondshelf.observability.AuditOutcome;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional
public class AdminUserServiceImpl implements AdminUserService {

    private static final AuditLogger AUDIT_LOGGER = AuditLogger.forClass(AdminUserServiceImpl.class);

    private final UserRepository userRepository;
    private final UserMapper userMapper;

    @Override
    public PrivateUserProfileResponse updateRoles(Long userId, Set<Role> roles) {
        Long actorUserId = SecurityUtils.currentUserId();

        try {
            User user = userRepository.findById(userId).orElseThrow(() -> new UserNotFoundException(userId));

            user.setRoles(new HashSet<>(roles));

            PrivateUserProfileResponse response = userMapper.toPrivateUserProfileResponse(userRepository.save(user));
            AUDIT_LOGGER.log(AuditEvent.builder("USER_ADMIN_ROLE_UPDATE", AuditOutcome.SUCCESS)
                    .actorUserId(actorUserId)
                    .targetUserId(userId)
                    .attribute("roles", roles)
                    .build());
            return response;
        } catch (RuntimeException ex) {
            AUDIT_LOGGER.log(AuditEvent.builder("USER_ADMIN_ROLE_UPDATE", AuditOutcome.FAILURE)
                    .actorUserId(actorUserId)
                    .targetUserId(userId)
                    .reason(ex.getMessage())
                    .build());
            throw ex;
        }
    }

    @Override
    public PrivateUserProfileResponse block(Long userId) {
        Long actorUserId = SecurityUtils.currentUserId();

        try {
            User user = userRepository.findById(userId).orElseThrow(() -> new UserNotFoundException(userId));
            user.setEnabled(false);

            PrivateUserProfileResponse response = userMapper.toPrivateUserProfileResponse(userRepository.save(user));
            AUDIT_LOGGER.log(AuditEvent.builder("USER_ADMIN_BLOCK", AuditOutcome.SUCCESS)
                    .actorUserId(actorUserId)
                    .targetUserId(userId)
                    .build());
            return response;
        } catch (RuntimeException ex) {
            AUDIT_LOGGER.log(AuditEvent.builder("USER_ADMIN_BLOCK", AuditOutcome.FAILURE)
                    .actorUserId(actorUserId)
                    .targetUserId(userId)
                    .reason(ex.getMessage())
                    .build());
            throw ex;
        }
    }

    @Override
    public PrivateUserProfileResponse unblock(Long userId) {
        Long actorUserId = SecurityUtils.currentUserId();

        try {
            User user = userRepository.findById(userId).orElseThrow(() -> new UserNotFoundException(userId));
            user.setEnabled(true);

            PrivateUserProfileResponse response = userMapper.toPrivateUserProfileResponse(userRepository.save(user));
            AUDIT_LOGGER.log(AuditEvent.builder("USER_ADMIN_UNBLOCK", AuditOutcome.SUCCESS)
                    .actorUserId(actorUserId)
                    .targetUserId(userId)
                    .build());
            return response;
        } catch (RuntimeException ex) {
            AUDIT_LOGGER.log(AuditEvent.builder("USER_ADMIN_UNBLOCK", AuditOutcome.FAILURE)
                    .actorUserId(actorUserId)
                    .targetUserId(userId)
                    .reason(ex.getMessage())
                    .build());
            throw ex;
        }
    }

}
