package com.secondshelf.userservice.dto;

import com.secondshelf.userservice.entity.Role;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Set;

@Data
@Builder
public class PrivateUserProfileResponse {
    private Long id;
    private String username;
    private String email;
    private String firstName;
    private String lastName;
    private String city;
    private String about;
    private Set<Role> roles;
    private boolean enabled;
    private LocalDateTime createdAt;
}
