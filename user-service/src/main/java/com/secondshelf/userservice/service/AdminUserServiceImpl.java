package com.secondshelf.userservice.service;

import com.secondshelf.userservice.dto.UserProfileResponse;
import com.secondshelf.userservice.entity.Role;
import com.secondshelf.userservice.entity.User;
import com.secondshelf.userservice.exception.UserNotFoundException;
import com.secondshelf.userservice.mapper.UserMapper;
import com.secondshelf.userservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional
public class AdminUserServiceImpl implements AdminUserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;

    @Override
    public UserProfileResponse updateRoles(Long userId, Set<Role> roles) {
        User user = userRepository.findById(userId).orElseThrow(() -> new UserNotFoundException(userId));

        user.setRoles(new HashSet<>(roles));

        return userMapper.toUserProfileResponse(userRepository.save(user));
    }

    @Override
    public UserProfileResponse block(Long userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new UserNotFoundException(userId));
        user.setEnabled(false);
        return userMapper.toUserProfileResponse(userRepository.save(user));
    }

    @Override
    public UserProfileResponse unblock(Long userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new UserNotFoundException(userId));
        user.setEnabled(true);
        return userMapper.toUserProfileResponse(userRepository.save(user));
    }

}
