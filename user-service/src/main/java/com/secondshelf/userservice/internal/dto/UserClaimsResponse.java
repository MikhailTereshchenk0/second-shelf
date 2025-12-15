package com.secondshelf.userservice.internal.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class UserClaimsResponse {
    private Long userId;
    private String username;
    private List<String> roles;
    private boolean enabled;
}
