package com.secondshelf.userservice.service;

import com.secondshelf.userservice.dto.UserProfileResponse;
import com.secondshelf.userservice.entity.Role;

import java.util.Set;

public interface AdminUserService {
    UserProfileResponse updateRoles(Long userId, Set<Role> roles);
    UserProfileResponse block(Long userId);
    UserProfileResponse unblock(Long userId);
}
