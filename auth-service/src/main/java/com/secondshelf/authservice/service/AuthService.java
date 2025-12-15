package com.secondshelf.authservice.service;

import com.secondshelf.authservice.dto.LoginRequest;
import com.secondshelf.authservice.dto.RefreshRequest;
import com.secondshelf.authservice.dto.RegisterRequest;
import com.secondshelf.authservice.dto.TokenPairResponse;

public interface AuthService {
    TokenPairResponse login(LoginRequest loginRequest);
    TokenPairResponse register(RegisterRequest request);
    TokenPairResponse refresh(RefreshRequest request);
    void logout(RefreshRequest request);
    void logoutAll(RefreshRequest request);
}
