package com.secondshelf.userservice.service;

import com.secondshelf.userservice.exception.*;
import com.secondshelf.userservice.dto.CreateUserProfileRequest;
import com.secondshelf.userservice.dto.UpdateUserProfileRequest;
import com.secondshelf.userservice.dto.UserProfileResponse;
import com.secondshelf.userservice.entity.User;
import com.secondshelf.userservice.mapper.UserMapper;
import com.secondshelf.userservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    public UserProfileResponse createProfile(CreateUserProfileRequest request) {

        if (userRepository.existsById(request.getId())) {
            throw new UserProfileAlreadyExistsException(request.getId());
        }

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
        user.getRoles().add("ROLE_USER");

        User saved = userRepository.save(user);

        return userMapper.toUserProfileResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public UserProfileResponse getById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(id));
        return userMapper.toUserProfileResponse(user);
    }

    @Override
    @Transactional(readOnly = true)
    public UserProfileResponse getByUsername(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException(username));
        return userMapper.toUserProfileResponse(user);
    }

    @Override
    public UserProfileResponse updateProfile(Long id, UpdateUserProfileRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(id));

        // обновляем только не-null поля
        userMapper.updateUserFromRequest(request, user);

        User updated = userRepository.save(user);
        return userMapper.toUserProfileResponse(updated);
    }
}
