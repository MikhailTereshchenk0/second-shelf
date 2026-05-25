package com.secondshelf.userservice.service;

import com.secondshelf.userservice.dto.CreateUserProfileRequest;
import com.secondshelf.userservice.dto.PrivateUserProfileResponse;
import com.secondshelf.userservice.dto.PublicUserProfileResponse;
import com.secondshelf.userservice.dto.UpdateUserProfileRequest;
import org.springframework.stereotype.Service;

public interface UserService {
    PrivateUserProfileResponse createProfile(CreateUserProfileRequest request);

    PublicUserProfileResponse getById(Long id);

    PublicUserProfileResponse getByUsername(String username);

    PrivateUserProfileResponse getCurrentUser(Long currentUserId);

    PrivateUserProfileResponse updateProfile(Long id, UpdateUserProfileRequest request);
}
