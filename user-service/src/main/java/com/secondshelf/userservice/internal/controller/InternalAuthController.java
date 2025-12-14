package com.secondshelf.userservice.internal.controller;

import com.secondshelf.userservice.internal.dto.AuthenticateRequest;
import com.secondshelf.userservice.internal.dto.AuthenticateResponse;
import com.secondshelf.userservice.internal.service.InternalAuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/internal/auth")
@RequiredArgsConstructor
public class InternalAuthController {

    private final InternalAuthService internalAuthService;

    @PostMapping("/authenticate")
    public AuthenticateResponse authenticate(@Valid @RequestBody AuthenticateRequest request) {
        var resp = internalAuthService.authenticate(request.getUsername(), request.getPassword());
        if (resp == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password");
        }
        return resp;
    }
}
