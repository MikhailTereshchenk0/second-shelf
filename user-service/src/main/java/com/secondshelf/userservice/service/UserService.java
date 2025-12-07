package com.secondshelf.userservice.service;

import com.secondshelf.userservice.dto.CreateUserProfileRequest;
import com.secondshelf.userservice.dto.UpdateUserProfileRequest;
import com.secondshelf.userservice.dto.UserProfileResponse;
import org.springframework.stereotype.Service;

@Service
public interface UserService {
    UserProfileResponse createProfile(CreateUserProfileRequest request);

    UserProfileResponse getById(Long id);

    UserProfileResponse getByUsername(String username);

    UserProfileResponse updateProfile(Long id, UpdateUserProfileRequest request);
}
