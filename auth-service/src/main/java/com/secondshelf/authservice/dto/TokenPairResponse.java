package com.secondshelf.authservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class TokenPairResponse {
    private String accessToken;
    private String refreshToken;
    private String tokenType; // "Bearer"
}
