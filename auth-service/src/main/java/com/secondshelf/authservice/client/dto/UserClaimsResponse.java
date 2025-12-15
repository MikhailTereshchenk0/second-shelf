package com.secondshelf.authservice.client.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserClaimsResponse {
    private Long userId;
    private String username;
    private List<String> roles;
    private boolean enabled;
}

