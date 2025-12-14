package com.secondshelf.authservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class CurrentUserResponse {
    private String username;
    private List<String> roles;
}
