package com.secondshelf.authservice.service;

import com.secondshelf.authservice.dto.LoginRequest;
import com.secondshelf.authservice.dto.LoginResponse;
import com.secondshelf.authservice.dto.RegisterRequest;

public interface AuthService {
    LoginResponse login(LoginRequest loginRequest);
    LoginResponse register(RegisterRequest request);
}
