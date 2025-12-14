package com.secondshelf.authservice.service;

import com.secondshelf.authservice.dto.LoginRequest;
import com.secondshelf.authservice.dto.LoginResponse;
import org.springframework.stereotype.Service;

public interface AuthService {
    LoginResponse login(LoginRequest loginRequest);
}
