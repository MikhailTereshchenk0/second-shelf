package com.secondshelf.userservice.internal.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class AuthenticateResponse {
    private Long userId;
    private String username;
    private List<String> roles;
}
