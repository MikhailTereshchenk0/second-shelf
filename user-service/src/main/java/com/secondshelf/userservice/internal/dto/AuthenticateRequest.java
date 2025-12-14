package com.secondshelf.userservice.internal.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AuthenticateRequest {
    @NotBlank
    private String username;

    @NotBlank
    private String password;
}
