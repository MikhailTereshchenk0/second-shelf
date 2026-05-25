package com.secondshelf.userservice.service;

import com.secondshelf.userservice.dto.PrivateUserProfileResponse;
import com.secondshelf.userservice.entity.Role;

import java.util.Set;

public interface AdminUserService {
    PrivateUserProfileResponse updateRoles(Long userId, Set<Role> roles);
    PrivateUserProfileResponse block(Long userId);
    PrivateUserProfileResponse unblock(Long userId);
}
