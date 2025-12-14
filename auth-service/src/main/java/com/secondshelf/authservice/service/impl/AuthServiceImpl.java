package com.secondshelf.authservice.service.impl;

import com.secondshelf.authservice.client.UserServiceClient;
import com.secondshelf.authservice.client.dto.UserAuthResponse;
import com.secondshelf.authservice.dto.LoginRequest;
import com.secondshelf.authservice.dto.LoginResponse;
import com.secondshelf.authservice.dto.RegisterRequest;
import com.secondshelf.authservice.security.JwtTokenProvider;
import com.secondshelf.authservice.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {
    private final JwtTokenProvider jwtTokenProvider;
    private final UserServiceClient userServiceClient;

    @Override
    public LoginResponse register(RegisterRequest request) {
        userServiceClient.createUser(request);

        var user = userServiceClient.authenticate(request.getUsername(), request.getPassword());

        String token = jwtTokenProvider.generateToken(user.getUserId(), user.getUsername(), user.getRoles());
        return new LoginResponse(token, "Bearer");
    }

    @Override
    public LoginResponse login(LoginRequest loginRequest) {
        UserAuthResponse user = userServiceClient.authenticate(
                loginRequest.getUsername(),
                loginRequest.getPassword()
        );

        if(user == null)
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password.");

        String token = jwtTokenProvider.generateToken(user.getUserId(), user.getUsername(), user.getRoles());
        return new LoginResponse(token, "Bearer");
    }
}
